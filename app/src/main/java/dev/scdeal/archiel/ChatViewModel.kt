package dev.scdeal.archiel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import kotlin.math.min

data class ChatState(    val sessions: List<Session> = emptyList(),
    val commands: List<CommandInfo> = emptyList(),
    val agents: List<AgentInfo> = emptyList(),
    val models: List<ModelInfo> = emptyList(),
    val permissions: List<PermissionRequest> = emptyList(),
    val model: String? = null,
    val agent: String? = null,
    val previews: Map<String, String> = emptyMap(),
    val sessionId: String? = null,
    val title: String? = null,
    val location: String? = null,
    val messages: List<Message> = emptyList(),
    val stream: String = "",
    val thinking: String = "",
    val tools: List<Part> = emptyList(),
    val busy: Boolean = false,
    val cutShort: Boolean = false,
    val loading: Boolean = false,
    val online: Boolean? = null,
    val latencyMs: Long? = null,
    val serverVersion: String? = null,
    val error: String? = null,
)

/** The session commands the TUI keeps out of /api/command and runs itself; we do the same here. */
val builtins = listOf(
    CommandInfo("compact", "Compress the transcript into a summary"),
    CommandInfo("fork", "Fork this session into a new one"),
    CommandInfo("undo", "Rewind to before the last prompt you sent"),
    CommandInfo("redo", "Walk the undo back forward"),
    CommandInfo("rename", "Retitle this session; anything after /rename is the title"),
    CommandInfo("reload", "Reload the server configuration after you edit it"),
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val cfg = Cfg.load(app)
    private val client = OpenCodeClient(cfg.baseUrl, cfg.password)
    val address = cfg.baseUrl

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    /** What Retry re-runs; null means the current sentence has nothing worth replaying. */
    private var retryAction: (() -> Unit)? = null
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
                .onSuccess { (agents, models) -> _state.update { it.copy(agents = agents, models = models) } }
                .onFailure { e -> fail("Could not load agents and models: ${e.message}") }
        }
    }

    private fun handleEvent(e: JsonObject) {
        val type = e.str("type") ?: return
        val data = e["data"] as? JsonObject ?: return
        if (data.str("sessionID") != _state.value.sessionId) return
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
                upsertTool(data.str("id")) { it.copy(state = (it.state ?: PartState()).copy(status = "completed", content = toolOutput(data))) }
            type == "session.tool.failed" ->
                upsertTool(data.str("id")) { it.copy(state = (it.state ?: PartState()).copy(status = "error", content = toolOutput(data))) }
            type == "session.execution.started" -> {
                _state.update { it.copy(busy = true, cutShort = false, tools = emptyList()) }
                pollPermissions()
            }
            type == "session.execution.succeeded" -> {
                _state.update { it.copy(busy = false, stream = "", thinking = "", tools = emptyList()) }
                loadMessages()
            }
            "error" in type -> {
                _state.update { it.copy(busy = false, cutShort = true, thinking = "", tools = emptyList()) }
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
    private fun pollPermissions() {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { client.permissions(id) }
                .onSuccess { list -> _state.update { it.copy(permissions = list) } }
        }
    }

    fun replyPermission(requestID: String, decision: String) {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { client.replyPermission(id, requestID, decision) }
                .onSuccess { _state.update { it.copy(permissions = it.permissions.filterNot { p -> p.id == requestID }) } }
                .onFailure { e -> fail("Could not answer the permission request: ${e.message}") }
            pollPermissions()
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
                .onSuccess { pollPermissions() }
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
            val list = client.sessions()
            info to list
        }.onSuccess { (info, list) ->
            _state.update {
                it.copy(
                    sessions = list,
                    loading = false,
                    online = true,
                    latencyMs = System.currentTimeMillis() - started,
                    serverVersion = info.version,
                    error = null,
                )
            }
        }.onFailure { e ->
            _state.update { it.copy(loading = false, online = false) }
            fail("The server is not answering: ${e.message}")
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
                title = it.sessions.firstOrNull { s -> s.id == id }?.title,
                messages = emptyList(),
                stream = "",
                thinking = "",
                tools = emptyList(),
                cutShort = false,
                error = null,
            )
        }
        loadMessages()
    }

    fun close() {
        _state.update {
            it.copy(sessionId = null, messages = emptyList(), stream = "", thinking = "", tools = emptyList(), busy = false, cutShort = false, permissions = emptyList())
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

    fun send(text: String) {
        val body = text.trim()
        val id = _state.value.sessionId ?: return
        if (body.isEmpty()) return
        _state.update {
            it.copy(
                messages = it.messages + Message(id = "local-${System.nanoTime()}", type = "user", text = body),
                cutShort = false,
                thinking = "",
                tools = emptyList(),
            )
        }
        dispatch(id, body)
    }

    /** The network half of [send], apart so Retry resends without echoing the user turn twice. */
    private fun dispatch(id: String, body: String) = viewModelScope.launch {
        _state.update { it.copy(busy = true, error = null) }
        runCatching { client.send(id, body) }
            .onFailure { e ->
                _state.update { it.copy(busy = false) }
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
                    _state.update { it.copy(messages = msgs, online = true) }
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
}
