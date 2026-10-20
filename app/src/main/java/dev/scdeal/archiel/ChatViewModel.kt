package dev.scdeal.archiel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
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
import okhttp3.WebSocket
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import kotlin.math.min

/** The session totals: live from `session.usage.updated`, seeded from the session list on open. */
data class Usage(val tokens: TokenUsage, val cost: Double)

/** The open session's context fill; the limit is null until the model that ran the last turn is known. */
data class ContextUsage(val used: Long, val limit: Long?)

/** The last command run in the shell screen: its paged output and, once it exits, its code. */
data class ShellRun(val id: String, val command: String, val output: String = "", val status: String? = null, val exit: Int? = null)

/** The terminal run: live output from a PTY socket, and the exit code once the socket closed. */
data class PtyRun(val id: String, val title: String, val command: String, val output: String = "", val status: String? = null, val exitCode: Int? = null)

/** A pending attachment: a file picked for the next prompt. `data` is the canonical base64 payload, kept so
 *  Retry can resend it; a file the server already has carries its `file://` uri instead. */
data class Attachment(val name: String, val mime: String, val bytes: Int, val data: String, val uri: String? = null)

data class ChatState(
    /** Starred and hidden model keys ("provider/model"), kept on disk so they outlive the process. */
    val favorites: Set<String> = emptySet(),
    val hidden: Set<String> = emptySet(),
    val sessions: List<Session> = emptyList(),
    val commands: List<CommandInfo> = emptyList(),
    val agents: List<AgentInfo> = emptyList(),
    val models: List<ModelInfo> = emptyList(),
    val permissions: List<PermissionRequest> = emptyList(),
    val forms: List<FormInfo> = emptyList(),
    val model: String? = null,
    val agent: String? = null,
    /** Preselected for a new session and kept on disk; null means the server picks. */
    val defaultModel: String? = null,
    val defaultAgent: String? = null,
    val previews: Map<String, String> = emptyMap(),
    val sessionId: String? = null,
    /** A subagent's session: the user may read it, but it belongs to the agent that spawned it. */
    val readOnly: Boolean = false,
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
    /** Files waiting to go out with the next prompt. */
    val attachments: List<Attachment> = emptyList(),
    /** The server's own files matching the dock's live "@word"; empty while there is no mention. */
    val fileMatches: List<FileEntry> = emptyList(),
    /** The subagents this prompt delegates to; the body carries them by name. */
    val mentions: List<String> = emptyList(),
    /** The exported session file to hand to the share sheet; cleared once the chooser is shown. */
    val shareUri: Uri? = null,
    val usage: Usage? = null,
    val context: ContextUsage? = null,
    /** The shell screen's run; cleared when the chat closes, which is also what stops its polling. */
    val shell: ShellRun? = null,
    val shellBusy: Boolean = false,
    /** The terminal screen's run; cleared when the chat closes, which also closes its socket. */
    val pty: PtyRun? = null,
    val ptyBusy: Boolean = false,
    val error: String? = null,
)

private const val SEARCH_DEBOUNCE_MS = 250L

/** The shell is polled for output and status; a quiet command is treated as done after this many idle polls. */
private const val SHELL_POLL_MS = 600L
private const val SHELL_STALE_POLLS = 5
/** A command that runs longer than this stops being polled; the server keeps it alive. */
private const val SHELL_MAX_MS = 10 * 60 * 1000L

/** The server refuses a data URL larger than 20 MB, so the app stops it before the encode. */
private const val MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024

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

/** The pending attachment as the wire sends it: a server file by its `file://` uri, a local pick as a data URL. */
private fun Attachment.toPromptFile(): PromptFile = PromptFile(uri = uri ?: "data:$mime;base64,$data", name = name)

/** A trailing "@word"; the group before "@" is the space (or nothing) the rewrite keeps. A path is one word too. */
private val MENTION = Regex("(^|\\s)@([A-Za-z0-9_./-]*)$")

/** The word the dock is offering agents for, or null when the text is not sitting on a mention. */
internal fun mentionQuery(text: String): String? = MENTION.find(text)?.groupValues?.get(2)

/** The absolute `file://` uri a server file is attached by; each segment encodes on its own, so its
 *  separators stay separators (encoding the whole path would turn them into %2F). */
internal fun fileUri(directory: String, path: String): String =
    "file://${directory.trimEnd('/')}/" + path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

/** CSI colour/cursor codes and OSC titles: a command that colours its output would otherwise dump them on screen. */
private val ANSI = Regex("\u001B(?:\\[[0-9;?]*[ -/]*[@-~]|\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)|[ -/]*[@-~])")

internal fun stripAnsi(text: String): String = text.replace(ANSI, "")

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val cfg = Cfg.load(app)
    private val client = OpenCodeClient(cfg.baseUrl, cfg.password)
    val address = cfg.baseUrl

    /** Drafts sit on disk, keyed by session id, so a killed process does not lose what you typed. */
    private val drafts = app.getSharedPreferences("drafts", android.content.Context.MODE_PRIVATE)

    /** Favourites and hidden models are device preferences, keyed by "provider/model". */
    private val modelPrefs = app.getSharedPreferences("models", android.content.Context.MODE_PRIVATE)

    /** The model and agent a new session is created with, kept across restarts. */
    private val defaults = app.getSharedPreferences("defaults", android.content.Context.MODE_PRIVATE)

    // Event payloads carry the same shapes as the API responses; decode them with the same tolerance.
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(
        ChatState(
            favorites = modelPrefs.getStringSet("favorites", emptySet()).orEmpty(),
            hidden = modelPrefs.getStringSet("hidden", emptySet()).orEmpty(),
            defaultModel = defaults.getString("model", null),
            defaultAgent = defaults.getString("agent", null),
        ),
    )
    val state: StateFlow<ChatState> = _state.asStateFlow()

    /** What Retry re-runs; null means the current sentence has nothing worth replaying. */
    private var retryAction: (() -> Unit)? = null

    /** The in-flight debounce for the sessions search, so typing cancels the request before it fires. */
    private var searchJob: Job? = null

    /** The in-flight debounce for the dock's file search, and the word it was asked for. */
    private var filesJob: Job? = null
    private var fileQuery = ""

    /** The shell polling loop; one at a time, and `close()` cancels it. */
    private var shellJob: Job? = null

    /** The open terminal socket and the run that owns it: a replaced run's close must not clear the new one. */
    private var ptySocket: WebSocket? = null
    private var ptyId: String? = null

    /** The server's absolute output cursor from the socket's one control frame; the text frames carry the output. */
    private var ptyCursor = 0L

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
        if (_state.value.readOnly) return
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { client.replyPermission(id, requestID, decision) }
                .onSuccess { _state.update { it.copy(permissions = it.permissions.filterNot { p -> p.id == requestID }) } }
                .onFailure { e -> fail("Could not answer the permission request: ${e.message}") }
            pollPrompts()
        }
    }

    fun replyForm(formID: String, answer: JsonObject) {
        if (_state.value.readOnly) return
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
        if (_state.value.readOnly) return
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { client.cancelForm(id, formID) }
                .onSuccess { _state.update { it.copy(forms = it.forms.filterNot { f -> f.id == formID }) } }
                .onFailure { e -> fail("Could not dismiss the form: ${e.message}") }
        }
    }

    fun setModel(providerID: String, modelID: String, variant: String? = null) {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { client.setModel(id, providerID, modelID, variant) }
                .onSuccess {
                    val ref = ModelRef(id = modelID, providerID = providerID, variant = variant)
                    _state.update {
                        it.copy(
                            model = ref.label(),
                            // Keep the row in step so the pick survives leaving the chat and coming back.
                            sessions = it.sessions.map { s -> if (s.id == id) s.copy(model = ref) else s },
                        )
                    }
                }
                .onFailure { e -> fail("Could not switch the model: ${e.message}") }
        }
    }

    fun toggleFavorite(key: String) {
        val next = flip(_state.value.favorites, key)
        modelPrefs.edit().putStringSet("favorites", next).apply()
        _state.update { it.copy(favorites = next) }
    }

    fun toggleHidden(key: String) {
        val next = flip(_state.value.hidden, key)
        modelPrefs.edit().putStringSet("hidden", next).apply()
        _state.update { it.copy(hidden = next) }
    }

    private fun flip(set: Set<String>, key: String): Set<String> = if (key in set) set - key else set + key

    fun setDefaultModel(label: String?) {
        putDefault("model", label)
        _state.update { it.copy(defaultModel = label) }
    }

    fun setDefaultAgent(id: String?) {
        putDefault("agent", id)
        _state.update { it.copy(defaultAgent = id) }
    }

    /** A null value clears the key rather than storing the text "null". */
    private fun putDefault(key: String, value: String?) {
        val edit = defaults.edit()
        if (value == null) edit.remove(key) else edit.putString(key, value)
        edit.apply()
    }

    fun setAgent(agent: String) {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { client.setAgent(id, agent) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            agent = agent,
                            // Keep the row in step so the pick survives leaving the chat and coming back.
                            sessions = it.sessions.map { s -> if (s.id == id) s.copy(agent = agent) else s },
                        )
                    }
                }
                .onFailure { e -> fail("Could not switch the agent: ${e.message}") }
        }
    }

    fun runCommand(name: String, arguments: String) {
        if (_state.value.readOnly) return
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

    fun open(id: String, readOnly: Boolean = false) {
        val existing = _state.value.sessions.firstOrNull { s -> s.id == id }
        _state.update {
            it.copy(
                sessionId = id,
                readOnly = readOnly,
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
                attachments = emptyList(),
                mentions = emptyList(),
                // The matches belong to the directory that was open; a new one searches its own.
                fileMatches = emptyList(),
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
        // The assistant announces the run of the session the phone last talked to.
        if (!readOnly) Assistant.watch(getApplication<Application>(), id, existing?.title)
    }

    fun close() {
        // Leaving the chat is what stops the shell polling; the run is dropped with it.
        shellJob?.cancel()
        // The terminal is the same: the socket closes and the program is killed on the server.
        val pty = ptyId
        ptySocket?.close(1000, null)
        ptySocket = null
        ptyId = null
        if (pty != null) viewModelScope.launch { runCatching { client.deletePty(pty) } }
        _state.update {
            it.copy(sessionId = null, readOnly = false, messages = emptyList(), queuedId = null, stream = "", thinking = "", tools = emptyList(), busy = false, cutShort = false, permissions = emptyList(), forms = emptyList(), draft = "", attachments = emptyList(), mentions = emptyList(), shareUri = null, usage = null, context = null, shell = null, shellBusy = false, pty = null, ptyBusy = false)
        }
    }

    fun newSession(onCreated: (String) -> Unit) = viewModelScope.launch {
        val current = _state.value
        runCatching { client.createSession(agent = current.defaultAgent, model = current.defaultModel?.let { modelRefOf(it) }) }
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
        if (_state.value.readOnly) return
        val id = _state.value.sessionId ?: return
        _state.update { it.copy(draft = text) }
        drafts.edit().putString(id, text).apply()
    }

    /** The text became a message, so a later reload must not bring it back. */
    private fun clearDraft(id: String) {
        drafts.edit().remove(id).apply()
        _state.update { it.copy(draft = "") }
    }

    /** A suggestion tap: the agent joins the prompt and the typed "@word" becomes its name plus a space. */
    fun mention(name: String) {
        if (name !in _state.value.mentions) _state.update { it.copy(mentions = it.mentions + name) }
        rewriteMention(name)
    }

    fun unmention(name: String) = _state.update { it.copy(mentions = it.mentions.filterNot { it == name }) }

    /** The typed "@word" becomes the picked name plus a space; the agent list and the file list both land here. */
    private fun rewriteMention(name: String) {
        val text = _state.value.draft
        val match = MENTION.find(text) ?: return
        draft(text.replaceRange(match.range, match.groupValues[1] + "@$name "))
    }

    /**
     * A file the server already has, attached by its absolute `file://` uri: the server reads it from
     * its own disk, which is the only way a picked path means anything for a remote session.
     */
    fun attachServerFile(path: String) {
        val directory = _state.value.location
        if (directory == null) {
            fail("This server session has no working directory, so its files cannot be attached.")
            return
        }
        _state.update { it.copy(attachments = it.attachments + Attachment(name = path, mime = "", bytes = 0, data = "", uri = fileUri(directory, path))) }
        rewriteMention(path)
    }

    /**
     * The dock's "@word" search against the server's own files: one request per pause, and a slow answer
     * for a word the user has typed past is dropped.
     */
    fun findFiles(query: String) {
        val word = query.trim()
        fileQuery = word
        filesJob?.cancel()
        if (word.isEmpty()) {
            _state.update { it.copy(fileMatches = emptyList()) }
            return
        }
        filesJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            // A failed lookup is not worth a card: the agent list and a path typed by hand still work.
            val matches = try {
                client.findFiles(word, _state.value.location)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            if (fileQuery == word) _state.update { it.copy(fileMatches = matches) }
        }
    }

    /**
     * Reads a picked file off the main thread and parks it as a pending attachment. The content resolver
     * is the only source that works for a remote server, since the server never sees the device's disk.
     */
    fun attach(uri: Uri) = viewModelScope.launch {
        val resolver = getApplication<Application>().contentResolver
        val attachment = runCatching {
            withContext(Dispatchers.IO) {
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    ?.takeIf { it.isNotBlank() }
                    ?: uri.lastPathSegment
                    ?: "file"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IOException("The file could not be read.")
                if (bytes.size > MAX_ATTACHMENT_BYTES) throw IOException("The file is larger than 20 MB, which the server refuses.")
                Attachment(name, mime, bytes.size, Base64.encodeToString(bytes, Base64.NO_WRAP))
            }
        }
        attachment
            .onSuccess { a -> _state.update { it.copy(attachments = it.attachments + a) } }
            .onFailure { e -> fail(e.message ?: "The file could not be read.") }
    }

    /**
     * A file or text handed over by another app: it waits in the dock of the open session, or of a new
     * one when there is none, so the next prompt carries it.
     */
    fun share(uris: List<Uri>, text: String, onReady: () -> Unit) {
        val body = text.trim()
        if (uris.isEmpty() && body.isEmpty()) return
        val current = _state.value
        // A subagent's session cannot take a prompt, so a share there opens a session of its own.
        if (current.sessionId == null || current.readOnly) {
            newSession {
                attachShared(uris, body)
                onReady()
            }
        } else {
            attachShared(uris, body)
            onReady()
        }
    }

    /** `open()` clears the dock, so a new session takes the share after it opens, not before. */
    private fun attachShared(uris: List<Uri>, text: String) {
        uris.forEach { attach(it) }
        if (text.isEmpty()) return
        // Keep what the user already typed; the shared text lands under it rather than replacing it.
        val typed = _state.value.draft
        draft(if (typed.isBlank()) text else "$typed\n\n$text")
    }

    fun detach(name: String) = _state.update { it.copy(attachments = it.attachments.filterNot { a -> a.name == name }) }

    fun clearAttachments() = _state.update { it.copy(attachments = emptyList()) }

    fun send(text: String) {
        if (_state.value.readOnly) return
        val body = text.trim()
        val id = _state.value.sessionId ?: return
        if (body.isEmpty()) return
        clearDraft(id)
        val files = _state.value.attachments.map { it.toPromptFile() }
        val agents = _state.value.mentions.map { PromptAgent(it) }
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
        dispatch(id, body, files, agents)
    }

    /** The network half of [send], apart so Retry resends the same body, files and mentions without echoing the user turn twice. */
    private fun dispatch(id: String, body: String, files: List<PromptFile>, agents: List<PromptAgent>): Job = viewModelScope.launch {
        _state.update { it.copy(busy = true, error = null) }
        runCatching { client.send(id, body, files, agents) }
            // The prompt is on the server, so the attachments and mentions are no longer pending; Retry replays them itself.
            .onSuccess { _state.update { it.copy(attachments = emptyList(), mentions = emptyList()) } }
            .onFailure { e ->
                // The prompt never reached the server, so the queued label would be a lie; the error card explains why.
                _state.update { it.copy(busy = false, queuedId = null) }
                fail("Could not send the message: ${e.message}") { dispatch(id, body, files, agents) }
            }
    }

    fun stop() {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { client.interrupt(id) }
                .onFailure { e -> fail("Could not stop it: ${e.message}") }
        }
    }

    /**
     * Runs one command on the server in the open session's directory. The runner has no stdin, so the
     * loop only reads: the output page grows the text, the shell row carries the status and exit code.
     */
    fun runShell(command: String) {
        val body = command.trim()
        if (body.isEmpty()) return
        val directory = _state.value.location
        // A second Run replaces the first; the old loop is cancelled before a new one starts.
        shellJob?.cancel()
        shellJob = viewModelScope.launch {
            _state.update { it.copy(shellBusy = true, shell = ShellRun(id = "", command = body), error = null) }
            val created = try {
                client.runShell(body, directory)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(shellBusy = false, shell = null) }
                fail("Could not run the command: ${e.message}")
                return@launch
            }
            var status = created.status
            var exit = created.exit
            var cursor = 0L
            var idle = 0
            val started = System.currentTimeMillis()
            _state.update { it.copy(shell = ShellRun(id = created.id, command = body, status = status, exit = exit)) }
            while (true) {
                val chunk = try {
                    client.shellOutput(created.id, cursor)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(shellBusy = false) }
                    fail("Could not run the command: ${e.message}")
                    return@launch
                }
                val text = stripAnsi(chunk.output)
                val moved = text.isNotEmpty() || chunk.cursor != cursor
                cursor = chunk.cursor
                if (text.isNotEmpty()) {
                    _state.update { s -> s.copy(shell = s.shell?.let { it.copy(output = it.output + text) }) }
                }
                try {
                    val info = client.shell(created.id)
                    status = info.status
                    exit = info.exit
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The output that did arrive still stands; the next poll retries the status read.
                }
                idle = if (moved) 0 else idle + 1
                // The server's status is the truth; the idle counter only ends a run whose status could not be read.
                val done = if (status != null) status != "running" else idle >= SHELL_STALE_POLLS
                if (done || System.currentTimeMillis() - started > SHELL_MAX_MS) break
                _state.update { s -> s.copy(shell = s.shell?.copy(status = status, exit = exit)) }
                delay(SHELL_POLL_MS)
            }
            _state.update { it.copy(shellBusy = false, shell = it.shell?.copy(status = status, exit = exit)) }
        }
    }

    /**
     * Runs a program on the server over the PTY socket, which gives it a real stdin. It is not a
     * shell: the first word is the command and the rest are its arguments, exec'd directly, so
     * quoting is the program's business. The output arrives live on the socket.
     */
    fun startPty(command: String) {
        val line = command.trim()
        if (line.isEmpty()) return
        val words = line.split(Regex("\\s+"))
        val directory = _state.value.location
        // A second Start replaces the first; the old socket and its program go before a new one opens.
        stopPty()
        viewModelScope.launch {
            _state.update { it.copy(ptyBusy = true, error = null) }
            val created = try {
                client.createPty(words.first(), words.drop(1), cwd = directory, title = line)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(ptyBusy = false) }
                fail("Could not start the terminal: ${e.message}")
                return@launch
            }
            val ticket = try {
                client.ptyTicket(created.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(ptyBusy = false) }
                fail("Could not start the terminal: ${e.message}")
                return@launch
            }
            ptyId = created.id
            ptyCursor = 0L
            _state.update { it.copy(ptyBusy = true, pty = PtyRun(id = created.id, title = created.title ?: line, command = line, status = "running")) }
            ptySocket = client.ptySocket(
                id = created.id,
                ticket = ticket,
                directory = directory,
                // The frames arrive on OkHttp's own thread; the state belongs to the main one.
                onText = { chunk ->
                    viewModelScope.launch {
                        _state.update { s -> s.copy(pty = s.pty?.let { it.copy(output = it.output + stripAnsi(chunk)) }) }
                    }
                },
                onControl = { cursor -> ptyCursor = cursor },
                onClosed = { finishPty(created.id) },
            )
        }
    }

    /** The socket closed, so the run is over; read the row once for the server's exit code. */
    private fun finishPty(id: String) {
        if (ptyId != id) return
        ptySocket = null
        ptyId = null
        viewModelScope.launch {
            val info = runCatching { client.pty(id) }.getOrNull()
            _state.update { s ->
                s.copy(ptyBusy = false, pty = s.pty?.copy(status = info?.status ?: "exited", exitCode = info?.exitCode))
            }
        }
    }

    /** A line of keystrokes; a terminal reads the newline as Enter. */
    fun ptyInput(line: String) {
        ptySocket?.send(line + "\n")
    }

    /** Closes the socket and kills the program on the server. */
    fun stopPty() {
        val id = ptyId
        ptySocket?.close(1000, null)
        ptySocket = null
        ptyId = null
        _state.update { it.copy(ptyBusy = false, pty = it.pty?.copy(status = "exited")) }
        if (id != null) viewModelScope.launch { runCatching { client.deletePty(id) } }
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
