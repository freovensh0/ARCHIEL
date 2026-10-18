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

data class ChatState(
    val sessions: List<Session> = emptyList(),
    val sessionId: String? = null,
    val title: String? = null,
    val messages: List<Message> = emptyList(),
    val stream: String = "",
    val busy: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val cfg = Cfg.load(app)
    private val client = OpenCodeClient(cfg.baseUrl, cfg.password)

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            client.events()
                .retryWhen { _, e ->
                    _state.update { it.copy(error = "Olay akışı kesildi, yeniden bağlanılıyor: ${e.message}") }
                    delay(3_000)
                    true
                }
                .collect { handleEvent(it) }
        }
        refresh()
    }

    private fun handleEvent(e: JsonObject) {
        val type = e.str("type") ?: return
        val data = e["data"] as? JsonObject ?: return
        if (data.str("sessionID") != _state.value.sessionId) return
        when {
            type == "session.text.delta" ->
                _state.update { it.copy(busy = true, stream = it.stream + data.str("delta").orEmpty()) }
            type == "session.execution.started" ->
                _state.update { it.copy(busy = true) }
            type == "session.execution.succeeded" -> {
                _state.update { it.copy(busy = false, stream = "") }
                loadMessages()
            }
            "error" in type ->
                _state.update { it.copy(busy = false, error = "Sunucu hata verdi: ${data.toString().take(160)}") }
        }
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        runCatching { client.sessions() }
            .onSuccess { list -> _state.update { it.copy(sessions = list, loading = false, error = null) } }
            .onFailure { e -> _state.update { it.copy(loading = false, error = "Oturumlar alınamadı: ${e.message}") } }
    }

    fun open(id: String) {
        _state.update {
            it.copy(
                sessionId = id,
                title = it.sessions.firstOrNull { s -> s.id == id }?.title,
                messages = emptyList(),
                stream = "",
                error = null,
            )
        }
        loadMessages()
    }

    fun close() {
        _state.update { it.copy(sessionId = null, messages = emptyList(), stream = "", busy = false) }
    }

    fun newSession(onCreated: (String) -> Unit) = viewModelScope.launch {
        runCatching { client.createSession() }
            .onSuccess { s ->
                _state.update { it.copy(sessions = listOf(s) + it.sessions.filterNot { x -> x.id == s.id }) }
                open(s.id)
                onCreated(s.id)
            }
            .onFailure { e -> _state.update { it.copy(error = "Oturum açılamadı: ${e.message}") } }
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
                    error = null,
                )
            }
            runCatching { client.send(id, body) }
                .onFailure { e -> _state.update { it.copy(busy = false, error = "Mesaj gönderilemedi: ${e.message}") } }
        }
    }

    fun stop() {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { client.interrupt(id) }
                .onFailure { e -> _state.update { it.copy(error = "Durdurulamadı: ${e.message}") } }
        }
    }

    private fun loadMessages() {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { client.messages(id) }
                .onSuccess { msgs -> _state.update { it.copy(messages = msgs) } }
                .onFailure { e -> _state.update { it.copy(error = "Mesajlar alınamadı: ${e.message}") } }
        }
    }
}
