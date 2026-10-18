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
import kotlinx.serialization.json.JsonObject
import kotlin.math.min

data class ChatState(
    val sessions: List<Session> = emptyList(),
    val commands: List<CommandInfo> = emptyList(),
    val agents: List<AgentInfo> = emptyList(),
    val models: List<ModelInfo> = emptyList(),
    val permissions: List<PermissionRequest> = emptyList(),
    val model: String? = null,
    val agent: String? = null,
    val previews: Map<String, String> = emptyMap(),
    val sessionId: String? = null,
    val title: String? = null,
    val messages: List<Message> = emptyList(),
    val stream: String = "",
    val busy: Boolean = false,
    val cutShort: Boolean = false,
    val loading: Boolean = false,
    val online: Boolean? = null,
    val latencyMs: Long? = null,
    val serverVersion: String? = null,
    val error: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val cfg = Cfg.load(app)
    private val client = OpenCodeClient(cfg.baseUrl, cfg.password)
    val address = cfg.baseUrl

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            client.events()
                // The phone sleeps and sockets die; back off instead of hammering the server.
                .retryWhen { cause, attempt ->
                    val wait = min(15_000L, 1_000L shl min(attempt.toInt(), 4))
                    _state.update {
                        it.copy(
                            online = false,
                            error = "The event stream dropped (${cause.message}). Retrying in ${wait / 1000}s.",
                        )
                    }
                    delay(wait)
                    true
                }
                .collect { handleEvent(it) }
        }
        refresh()
        viewModelScope.launch {
            runCatching { client.commands() }
                .onSuccess { list -> _state.update { it.copy(commands = list) } }
        }
    }

    /** Both pickers are fetched the first time they are opened, not on every start. */
    fun loadPickers() {
        if (_state.value.agents.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { client.agents() to client.models() }
                .onSuccess { (agents, models) -> _state.update { it.copy(agents = agents, models = models) } }
                .onFailure { e -> _state.update { it.copy(error = "Could not load agents and models: ${e.message}") } }
        }
    }

    private fun handleEvent(e: JsonObject) {
        val type = e.str("type") ?: return
        val data = e["data"] as? JsonObject ?: return
        if (data.str("sessionID") != _state.value.sessionId) return
        when {
            type == "session.text.delta" ->
                _state.update { it.copy(busy = true, stream = it.stream + data.str("delta").orEmpty()) }
            type == "session.execution.started" -> {
                _state.update { it.copy(busy = true, cutShort = false) }
                pollPermissions()
            }
            type == "session.execution.succeeded" -> {
                _state.update { it.copy(busy = false, stream = "") }
                loadMessages()
            }
            "error" in type ->
                _state.update {
                    it.copy(
                        busy = false,
                        cutShort = true,
                        error = "The server reported an error: ${data.toString().take(160)}",
                    )
                }
        }
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
                .onFailure { e -> _state.update { it.copy(error = "Could not answer the permission request: ${e.message}") } }
            pollPermissions()
        }
    }

    fun setModel(providerID: String, modelID: String) {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { client.setModel(id, providerID, modelID) }
                .onSuccess { _state.update { it.copy(model = "$providerID/$modelID") } }
                .onFailure { e -> _state.update { it.copy(error = "Could not switch the model: ${e.message}") } }
        }
    }

    fun setAgent(agent: String) {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { client.setAgent(id, agent) }
                .onSuccess { _state.update { it.copy(agent = agent) } }
                .onFailure { e -> _state.update { it.copy(error = "Could not switch the agent: ${e.message}") } }
        }
    }

    fun runCommand(name: String, arguments: String) {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, cutShort = false, error = null) }
            runCatching { client.runCommand(id, name, arguments) }
                .onSuccess { pollPermissions() }
                .onFailure { e -> _state.update { it.copy(busy = false, error = "Could not run /$name: ${e.message}") } }
        }
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
            _state.update {
                it.copy(loading = false, online = false, error = "The server is not answering: ${e.message}")
            }
        }
    }

    fun open(id: String) {
        val existing = _state.value.sessions.firstOrNull { s -> s.id == id }
        _state.update {
            it.copy(
                sessionId = id,
                model = existing?.model?.label(),
                agent = existing?.agent,
                permissions = emptyList(),
                title = it.sessions.firstOrNull { s -> s.id == id }?.title,
                messages = emptyList(),
                stream = "",
                cutShort = false,
                error = null,
            )
        }
        loadMessages()
    }

    fun close() {
        _state.update {
            it.copy(sessionId = null, messages = emptyList(), stream = "", busy = false, cutShort = false, permissions = emptyList())
        }
    }

    fun newSession(onCreated: (String) -> Unit) = viewModelScope.launch {
        runCatching { client.createSession() }
            .onSuccess { s ->
                _state.update { it.copy(sessions = listOf(s) + it.sessions.filterNot { x -> x.id == s.id }) }
                open(s.id)
                onCreated(s.id)
            }
            .onFailure { e -> _state.update { it.copy(error = "Could not open a session: ${e.message}") } }
    }

    fun send(text: String) {
        val body = text.trim()
        val id = _state.value.sessionId ?: return
        if (body.isEmpty()) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    messages = it.messages + Message(id = "local-${System.nanoTime()}", type = "user", text = body),
                    busy = true,
                    cutShort = false,
                    error = null,
                )
            }
            runCatching { client.send(id, body) }
                .onFailure { e -> _state.update { it.copy(busy = false, error = "Could not send the message: ${e.message}") } }
        }
    }

    fun stop() {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { client.interrupt(id) }
                .onFailure { e -> _state.update { it.copy(error = "Could not stop it: ${e.message}") } }
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
                .onFailure { e -> _state.update { it.copy(online = false, error = "Could not load the messages: ${e.message}") } }
        }
    }
}
