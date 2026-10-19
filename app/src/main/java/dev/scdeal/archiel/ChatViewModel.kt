package dev.scdeal.archiel

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.io.IOException
import kotlin.math.min

/** The session totals: live from `session.usage.updated`, seeded from the session list on open. */
data class Usage(val tokens: TokenUsage, val cost: Double)

/** The open session's context fill; the limit is null until the model that ran the last turn is known. */
data class ContextUsage(val used: Long, val limit: Long?)

data class ChatState(    val sessions: List<Session> = emptyList(),
    val commands: List<CommandInfo> = emptyList(),
    val agents: List<AgentInfo> = emptyList(),
    val models: List<ModelInfo> = emptyList(),
    val permissions: List<PermissionRequest> = emptyList(),
    val forms: List<FormInfo> = emptyList(),
    val model: String? = null,
    val agent: String? = null,
    val previews: Map<String, String> = emptyMap(),
    val sessionId: String? = null,
    val title: String? = null,
    val location: String? = null,
    val messages: List<Message> = emptyList(),
    /** The local echo no run has picked up yet; its bubble shows "queued" while this is set. */
    val queuedId: String? = null,
    val stream: String = "",
    val thinking: String = "",
    val tools: List<Part> = emptyList(),
    val busy: Boolean = false,
    val cutShort: Boolean = false,
    val loading: Boolean = false,
    val online: Boolean? = null,
    val latencyMs: Long? = null,
    val serverVersion: String? = null,
    /** The sessions-screen title filter; it is not part of opening a session, so `open()` leaves it alone. */
    val search: String = "",
    /** The unsent input of the open session; persisted to disk so it survives process death. */
    val draft: String = "",
    /** The exported session file to hand to the share sheet; cleared once the chooser is shown. */
    val shareUri: Uri? = null,
    val usage: Usage? = null,
    val context: ContextUsage? = null,
    val error: String? = null,
)

private const val SEARCH_DEBOUNCE_MS = 250L

/** The session commands the TUI keeps out of /api/command and runs itself; we do the same here. */
val builtins = listOf(
    CommandInfo("compact", "Compress the transcript into a summary"),
    CommandInfo("fork", "Fork this session into a new one"),
    CommandInfo("undo", "Rewind to before the last prompt you sent"),
    CommandInfo("redo", "Walk the undo back forward"),
    CommandInfo("rename", "Retitle this session; anything after /rename is the title"),
    CommandInfo("reload", "Reload the server configuration after you edit it"),
)

/** A file name from the session title: lowercase letters, digits and dashes only, capped at 40. */
private fun sessionSlug(title: String?, fallback: String): String =
    title.orEmpty().lowercase()
        .map { if (it in 'a'..'z' || it in '0'..'9' || it == '-') it else '-' }
        .joinToString("")
        .replace(Regex("-+"), "-")
        .trim('-')
        .take(40)
        .ifEmpty { fallback }

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val cfg = Cfg.load(app)
    private val client = OpenCodeClient(cfg.baseUrl, cfg.password)
    val address = cfg.baseUrl

    /** Drafts sit on disk, keyed by session id, so a killed process does not lose what you typed. */
    private val drafts = app.getSharedPreferences("drafts", android.content.Context.MODE_PRIVATE)

    // Event payloads carry the same shapes as the API responses; decode them with the same tolerance.
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    /** What Retry re-runs; null means the current sentence has nothing worth replaying. */
    private var retryAction: (() -> Unit)? = null

    /** The in-flight debounce for the sessions search, so typing cancels the request before it fires. */
    private var searchJob: Job? = null

    private val _canRetry = MutableStateFlow(false)
    val canRetry: StateFlow<Boolean> = _canRetry.asStateFlow()

    fun retry() {
        val action = retryAction ?: return
        retryAction = null
        _canRetry.value = false
        _state.update { it.copy(error = null) }
        action()
    }

    private fun fail(message: String, action: (() -> Unit)? = null) {
        retryAction = action
        _canRetry.value = action != null
        _state.update { it.copy(error = message) }
    }

    init {
        viewModelScope.launch {
            client.events()
                // The phone sleeps and sockets die; back off instead of hammering the server.
                .retryWhen { cause, attempt ->
                    val wait = min(15_000L, 1_000L shl min(attempt.toInt(), 4))
                    _state.update { it.copy(online = false) }
                    fail("The event stream dropped (${cause.message}). Retrying in ${wait / 1000}s.")
                    delay(wait)
                    true
                }
                .collect { handleEvent(it) }
        }
        refresh()
    }

    /** The server registers plugins after it starts, so the dock refetches commands instead of caching them. */
    fun loadCommands() = viewModelScope.launch {
        runCatching { client.commands(_state.value.location) }
            .onSuccess { list ->
                _state.update { st ->
                    st.copy(commands = builtins + list.filterNot { s -> builtins.any { it.name == s.name } })
                }
            }
    }

    /** Both pickers are fetched the first time they are opened, not on every start. */
    fun loadPickers() {
        if (_state.value.agents.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { client.agents() to client.models() }
                .onSuccess { (agents, models) ->
                    // The context limit needs the model list; the transcript may have landed first.
                    _state.update { it.copy(agents = agents, models = models, context = contextOf(it.messages, models)) }
                }
                .onFailure { e -> fail("Could not load agents and models: ${e.message}") }
        }
    }

    private fun handleEvent(e: JsonObject) {
        val type = e.str("type") ?: return
        val data = e["data"] as? JsonObject ?: return
        // form.created carries the session on the form, not next to it.
        val sessionID = data.str("sessionID") ?: (data["form"] as? JsonObject)?.str("sessionID")
        if (sessionID != _state.value.sessionId) return
        when {
            type == "session.text.delta" ->
                _state.update { it.copy(busy = true, stream = it.stream + data.str("delta").orEmpty()) }
            // The thinking phase often runs alone for a while; a bounded tail keeps it cheap.
            type == "session.reasoning.delta" ->
                _state.update { it.copy(busy = true, thinking = (it.thinking + data.str("delta").orEmpty()).takeLast(2000)) }
            // Tools stream the whole call: the command shows the moment the agent names it.
            type == "session.tool.input.started" ->
                upsertTool(data.str("id")) { it.copy(name = data.str("name"), state = PartState(status = "running")) }
            type == "session.tool.called" ->
                upsertTool(data.str("id")) { it.copy(state = it.state?.copy(input = data["input"] as? JsonObject) ?: PartState(status = "running", input = data["input"] as? JsonObject)) }
            type == "session.tool.success" ->
                upsertTool(data.str("id")) { it.copy(state = (it.state ?: PartState()).copy(status = "completed", content = toolOutput(data), metadata = data["metadata"] as? JsonObject)) }
            type == "session.tool.failed" ->
                upsertTool(data.str("id")) { it.copy(state = (it.state ?: PartState()).copy(status = "error", content = toolOutput(data), metadata = data["metadata"] as? JsonObject)) }
            type == "form.created" -> {
                val form = (data["form"] as? JsonObject)
                    ?.let { runCatching { json.decodeFromJsonElement(FormInfo.serializer(), it) }.getOrNull() }
                if (form != null) {
                    _state.update { s ->
                        s.copy(
                            forms = if (s.forms.any { it.id == form.id }) s.forms.map { if (it.id == form.id) form else it }
                            else s.forms + form,
                        )
                    }
                }
            }
            type == "form.replied" || type == "form.cancelled" ->
                data.str("id")?.let { id -> _state.update { it.copy(forms = it.forms.filterNot { f -> f.id == id }) } }
            // The feed names these too, but polling keeps the list honest across a dropped stream.
            type == "permission.asked" -> pollPrompts()
            type == "permission.replied" -> {
                val requestID = data.str("requestID")
                _state.update { it.copy(permissions = it.permissions.filterNot { p -> p.id == requestID }) }
                pollPrompts()
            }
            // Ephemeral, but it is the only live source of the session totals.
            type == "session.usage.updated" -> {
                val tokens = (data["tokens"] as? JsonObject)
                    ?.let { runCatching { json.decodeFromJsonElement(TokenUsage.serializer(), it) }.getOrNull() }
                if (tokens != null) {
                    val cost = (data["cost"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0
                    _state.update { it.copy(usage = Usage(tokens, cost)) }
                }
            }
            type == "session.execution.started" -> {
                // A run for the prompt has begun, so the server has it even before the reload.
                _state.update { it.copy(busy = true, cutShort = false, tools = emptyList(), queuedId = null) }
                pollPrompts()
            }
            type == "session.execution.succeeded" -> {
                _state.update { it.copy(busy = false, stream = "", thinking = "", tools = emptyList()) }
                loadMessages()
            }
            "error" in type -> {
                _state.update { it.copy(busy = false, cutShort = true, thinking = "", tools = emptyList(), queuedId = null) }
                // Reload so the transcript shows what the server actually stored before it failed.
                fail("The server reported an error: ${data.toString().take(160)}") { loadMessages() }
            }
        }
    }

    /** Live tool sections are keyed by the call id the server assigns. */
    private fun upsertTool(id: String?, patch: (Part) -> Part) {
        if (id == null) return
        _state.update { s ->
            s.copy(
                tools = if (s.tools.any { it.id == id }) s.tools.map { if (it.id == id) patch(it) else it }
                else s.tools + patch(Part(id = id)),
            )
        }
    }

    private fun toolOutput(data: JsonObject) =
        (data["content"] as? JsonArray)?.mapNotNull { o ->
            ((o as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull?.let { PartText(text = it) }
        }

    /** The event feed does not name permission updates, so a run in flight polls them. */
    private fun pollPrompts() {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { client.permissions(id) }
                .onSuccess { list -> _state.update { it.copy(permissions = list) } }
            runCatching { client.forms(id) }
                .onSuccess { list -> _state.update { it.copy(forms = list) } }
        }
    }

    fun replyPermission(requestID: String, decision: String) {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { client.replyPermission(id, requestID, decision) }
                .onSuccess { _state.update { it.copy(permissions = it.permissions.filterNot { p -> p.id == requestID }) } }
                .onFailure { e -> fail("Could not answer the permission request: ${e.message}") }
            pollPrompts()
        }
    }

    fun replyForm(formID: String, answer: JsonObject) {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { client.replyForm(id, formID, answer) }
                .onSuccess { _state.update { it.copy(forms = it.forms.filterNot { f -> f.id == formID }) } }
                // A 400 means the server rejected the choice; Retry would only resend the same answer.
                .onFailure { e -> fail("The server rejected the answer: ${e.message} Pick an option the form offers and submit again.") }
            pollPrompts()
        }
    }

    fun cancelForm(formID: String) {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { client.cancelForm(id, formID) }
                .onSuccess { _state.update { it.copy(forms = it.forms.filterNot { f -> f.id == formID }) } }
                .onFailure { e -> fail("Could not dismiss the form: ${e.message}") }
        }
    }

    fun setModel(providerID: String, modelID: String) {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { client.setModel(id, providerID, modelID) }
                .onSuccess { _state.update { it.copy(model = "$providerID/$modelID") } }
                .onFailure { e -> fail("Could not switch the model: ${e.message}") }
        }
    }

    fun setAgent(agent: String) {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { client.setAgent(id, agent) }
                .onSuccess { _state.update { it.copy(agent = agent) } }
                .onFailure { e -> fail("Could not switch the agent: ${e.message}") }
        }
    }

    fun runCommand(name: String, arguments: String) {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            if (name in builtins.map { it.name }) {
                builtin(id, name, arguments)
                return@launch
            }
            _state.update { it.copy(busy = true, cutShort = false, thinking = "", tools = emptyList(), error = null) }
            runCatching { client.runCommand(id, name, arguments) }
                .onSuccess { pollPrompts() }
                .onFailure { e ->
                    _state.update { it.copy(busy = false) }
                    fail("Could not run /$name: ${e.message}") { runCommand(name, arguments) }
                }
        }
    }

    /** The app-level slash set: the same calls the official TUI makes when you run these. */
    private suspend fun builtin(id: String, name: String, arguments: String) {
        runCatching {
            when (name) {
                "compact" -> client.compact(id)
                "undo" -> {
                    client.interrupt(id)
                    val last = _state.value.messages.lastOrNull { it.type == "user" && !it.id.startsWith("local-") }
                    client.revertStage(id, last?.id ?: throw IOException("Nothing to undo"))
                }
                "redo" -> client.revertClear(id)
                "reload" -> client.reloadConfig()
                "rename" -> {
                    val title = arguments.trim()
                    if (title.isEmpty()) throw IOException("Type the title after /rename")
                    client.retitle(id, title)
                    _state.update { st ->
                        st.copy(title = title, sessions = st.sessions.map { if (it.id == id) it.copy(title = title) else it })
                    }
                }
                "fork" -> {
                    val forked = client.fork(id)
                    _state.update { it.copy(sessions = listOf(forked) + it.sessions.filterNot { x -> x.id == forked.id }) }
                    open(forked.id)
                    return
                }
            }
        }
            // A builtin can half-apply (undo interrupts before it reverts), so Retry only replays server commands.
            .onFailure { e -> fail("Could not run /$name: ${e.message}") }
        loadMessages()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        val started = System.currentTimeMillis()
        runCatching {
            val info = client.info()
            val list = client.sessions(_state.value.search)
            info to list
        }.onSuccess { (info, list) ->
            _state.update {
                it.copy(
                    sessions = list,
                    loading = false,
                    online = true,
                    latencyMs = System.currentTimeMillis() - started,
                    serverVersion = info.version,
                    // Seed only: a usage event that already arrived is fresher than the list.
                    usage = it.usage ?: usageOf(list.firstOrNull { s -> s.id == it.sessionId }),
                    error = null,
                )
            }
        }.onFailure { e ->
            _state.update { it.copy(loading = false, online = false) }
            fail("The server is not answering: ${e.message}")
        }
    }

    /**
     * The sessions-screen title filter: show the query at once, then fetch it server side after a
     * pause so a fast typist sends one request, not one per keystroke.
     */
    fun search(query: String) {
        _state.update { it.copy(search = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val list = try {
                client.sessions(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A blank query failing is the ordinary offline case refresh() already reports.
                if (query.isNotBlank() && _state.value.search == query) fail("Could not search sessions: ${e.message}")
                return@launch
            }
            // A slow earlier response must not overwrite a newer query's result.
            if (_state.value.search == query) _state.update { it.copy(sessions = list, online = true, error = null) }
        }
    }

    fun open(id: String) {
        val existing = _state.value.sessions.firstOrNull { s -> s.id == id }
        _state.update {
            it.copy(
                sessionId = id,
                model = existing?.model?.label(),
                agent = existing?.agent,
                location = existing?.location?.directory,
                commands = emptyList(),
                permissions = emptyList(),
                forms = emptyList(),
                title = it.sessions.firstOrNull { s -> s.id == id }?.title,
                messages = emptyList(),
                queuedId = null,
                stream = "",
                thinking = "",
                tools = emptyList(),
                cutShort = false,
                draft = drafts.getString(id, "").orEmpty(),
                usage = usageOf(existing),
                context = null,
                error = null,
            )
        }
        // The context bar needs the model windows; loadPickers caches, so this is free afterwards.
        loadPickers()
        loadMessages()
        // A question asked while the screen was closed has no live event left to replay.
        pollPrompts()
    }

    fun close() {
        _state.update {
            it.copy(sessionId = null, messages = emptyList(), queuedId = null, stream = "", thinking = "", tools = emptyList(), busy = false, cutShort = false, permissions = emptyList(), forms = emptyList(), draft = "", shareUri = null, usage = null, context = null)
        }
    }

    fun newSession(onCreated: (String) -> Unit) = viewModelScope.launch {
        runCatching { client.createSession() }
            .onSuccess { s ->
                _state.update { it.copy(sessions = listOf(s) + it.sessions.filterNot { x -> x.id == s.id }) }
                open(s.id)
                onCreated(s.id)
            }
            .onFailure { e -> fail("Could not open a session: ${e.message}") }
    }

    fun deleteSession(onDeleted: () -> Unit) = viewModelScope.launch {
        val id = _state.value.sessionId ?: return@launch
        runCatching { client.deleteSession(id) }
            .onSuccess {
                _state.update { it.copy(sessions = it.sessions.filterNot { s -> s.id == id }) }
                drafts.edit().remove(id).apply()
                onDeleted()
            }
            .onFailure { e -> fail("Could not delete the session: ${e.message}") }
    }

    /**
     * The server writes the portable bundle; the app only parks it in the cache and points the share
     * sheet at it. A later export overwrites the same file, so the cache does not grow per tap.
     */
    fun exportSession() = viewModelScope.launch {
        val id = _state.value.sessionId ?: return@launch
        runCatching {
            val body = client.exportSession(id)
            // The bundle can be megabytes; keep the write off the main thread.
            withContext(Dispatchers.IO) {
                val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
                val file = File(dir, "${sessionSlug(_state.value.title, id)}.json")
                file.writeText(body)
                val app = getApplication<Application>()
                FileProvider.getUriForFile(app, "${app.packageName}.files", file)
            }
        }
            .onSuccess { uri -> _state.update { it.copy(shareUri = uri) } }
            .onFailure { e -> fail("Could not export the session: ${e.message}") }
    }

    fun clearShare() = _state.update { it.copy(shareUri = null) }

    /** Persists every keystroke; the strings are tiny and `apply()` writes off the main thread. */
    fun draft(text: String) {
        val id = _state.value.sessionId ?: return
        _state.update { it.copy(draft = text) }
        drafts.edit().putString(id, text).apply()
    }

    /** The text became a message, so a later reload must not bring it back. */
    private fun clearDraft(id: String) {
        drafts.edit().remove(id).apply()
        _state.update { it.copy(draft = "") }
    }

    fun send(text: String) {
        val body = text.trim()
        val id = _state.value.sessionId ?: return
        if (body.isEmpty()) return
        clearDraft(id)
        val local = Message(id = "local-${System.nanoTime()}", type = "user", text = body)
        _state.update {
            it.copy(
                messages = it.messages + local,
                queuedId = local.id,
                cutShort = false,
                thinking = "",
                tools = emptyList(),
            )
        }
        dispatch(id, body)
    }

    /** The network half of [send], apart so Retry resends without echoing the user turn twice. */
    private fun dispatch(id: String, body: String): Job = viewModelScope.launch {
        _state.update { it.copy(busy = true, error = null) }
        runCatching { client.send(id, body) }
            .onFailure { e ->
                // The prompt never reached the server, so the queued label would be a lie; the error card explains why.
                _state.update { it.copy(busy = false, queuedId = null) }
                fail("Could not send the message: ${e.message}") { dispatch(id, body) }
            }
    }

    fun stop() {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { client.interrupt(id) }
                .onFailure { e -> fail("Could not stop it: ${e.message}") }
        }
    }

    /** Fetched per visible row: one small request, cached for the life of the screen. */
    fun preview(sessionID: String) {
        if (_state.value.previews.containsKey(sessionID)) return
        viewModelScope.launch {
            val text = runCatching { client.lastMessage(sessionID)?.preview().orEmpty() }.getOrDefault("")
            _state.update { it.copy(previews = it.previews + (sessionID to text)) }
        }
    }

    private fun loadMessages() {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { client.messages(id) }
                .onSuccess { msgs ->
                    _state.update { it.copy(messages = msgs, online = true, context = contextOf(msgs, it.models)) }
                    msgs.lastOrNull()?.let { last ->
                        _state.update { it.copy(previews = it.previews + (id to last.preview())) }
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(online = false) }
                    fail("Could not load the messages: ${e.message}") { loadMessages() }
                }
        }
    }

    /** The TUI's rule: the last assistant message with usage sets the fill, matched to its model's window. */
    private fun contextOf(messages: List<Message>, models: List<ModelInfo>): ContextUsage? {
        val message = messages.lastOrNull { it.tokens != null } ?: return null
        val tokens = message.tokens ?: return null
        val ref = message.model
        val limit = ref?.let { r ->
            models.firstOrNull { it.providerID == r.providerID && (it.id == r.id || it.modelID == r.id) }
                ?.limit?.context
        }?.takeIf { it > 0 }
        return ContextUsage(tokens.total(), limit)
    }

    /** The session list row carries the same totals, so an opened session has numbers before any event. */
    private fun usageOf(session: Session?): Usage? {
        val s = session ?: return null
        if (s.tokens == null && s.cost == null) return null
        return Usage(s.tokens ?: TokenUsage(), s.cost ?: 0.0)
    }
}
