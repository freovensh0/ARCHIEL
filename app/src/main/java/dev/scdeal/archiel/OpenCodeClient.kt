package dev.scdeal.archiel

import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private val JSON_TYPE = "application/json".toMediaType()

@Serializable
data class Session(
    val id: String,
    val title: String? = null,
    val time: SessionTime? = null,
    val agent: String? = null,
    val model: ModelRef? = null,
    val location: SessionLocation? = null,
    val parentID: String? = null,
    val cost: Double? = null,
    val tokens: TokenUsage? = null,
)

@Serializable
data class ModelRef(val id: String, val providerID: String, val variant: String? = null)

@Serializable
data class TokenUsage(
    val input: Long = 0,
    val output: Long = 0,
    val reasoning: Long = 0,
    val cache: TokenCache = TokenCache(),
)

@Serializable
data class TokenCache(val read: Long = 0, val write: Long = 0)

/** What one turn cost the context window, as the TUI counts it. */
fun TokenUsage.total(): Long = input + output + reasoning + cache.read + cache.write

fun ModelRef.label(): String = "$providerID/$id" + (variant?.let { "#$it" } ?: "")

/** The inverse of [label]; null when the text does not name a provider and an id. */
fun modelRefOf(label: String): ModelRef? {
    val slash = label.indexOf('/')
    if (slash <= 0) return null
    val rest = label.substring(slash + 1)
    val hash = rest.indexOf('#')
    val id = if (hash >= 0) rest.substring(0, hash) else rest
    if (id.isEmpty()) return null
    val variant = if (hash >= 0) rest.substring(hash + 1).ifEmpty { null } else null
    return ModelRef(id = id, providerID = label.substring(0, slash), variant = variant)
}

@Serializable
data class SessionTime(val updated: Long = 0)

@Serializable
data class SessionLocation(val directory: String? = null)

@Serializable
data class Message(
    val id: String,
    val type: String,
    val time: MessageTime? = null,
    val text: String? = null,
    val content: List<Part>? = null,
    val outcome: String? = null,
    val cost: Double? = null,
    val tokens: TokenUsage? = null,
    val model: ModelRef? = null,
    /** The files a user message carried; `data` is base64 and the server may have resized an image. */
    val files: List<MessageFile>? = null,
)

@Serializable
data class MessageFile(
    val data: String? = null,
    val mime: String? = null,
    val name: String? = null,
)

@Serializable
data class MessageTime(val created: Long = 0)

@Serializable
data class Part(
    val id: String? = null,
    val type: String? = null,
    val text: String? = null,
    val name: String? = null,
    val state: PartState? = null,
    val time: PartTime? = null,
)

@Serializable
data class PartTime(val created: Long? = null, val completed: Long? = null)

@Serializable
data class PartState(
    val status: String? = null,
    val input: JsonObject? = null,
    val content: List<PartText>? = null,
    // Read only for `patch`: files[] with each unified diff and its counts.
    val metadata: JsonObject? = null,
)

@Serializable
data class PartText(val type: String? = null, val text: String? = null)

@Serializable
data class AgentInfo(val id: String, val name: String, val mode: String? = null, val description: String? = null, val hidden: Boolean = false)

// `cost` arrives as an array and nothing reads it, so it stays out of the model rather than making
// the whole list undecodable.
@Serializable
data class ModelInfo(
    val providerID: String,
    val modelID: String,
    val name: String? = null,
    val id: String? = null,
    val limit: ModelLimit? = null,
    val variants: List<ModelVariant>? = null,
)

@Serializable
data class ModelVariant(val id: String)

@Serializable
data class ModelLimit(val context: Long = 0, val output: Long = 0)

@Serializable
data class CommandInfo(val name: String, val description: String? = null)

/** One file on a prompt: `uri` may be a data URL, which is the only path that works for a remote server. */
@Serializable
data class PromptFile(val uri: String, val name: String? = null)

/** A subagent named in a prompt; the server stores the name with the message and the primary agent delegates to it. */
@Serializable
data class PromptAgent(val name: String)

@Serializable
data class PermissionRequest(
    val id: String,
    val sessionID: String? = null,
    val action: String,
    val resources: List<String> = emptyList(),
    val save: List<String> = emptyList(),
    val message: String? = null,
)

/**
 * A pending server form — the `question` tool asks through one. Only the fields the app renders are
 * modelled; the server's other field types arrive as a `type` we do not special-case.
 */
@Serializable
data class FormInfo(
    val id: String,
    val sessionID: String? = null,
    val title: String? = null,
    val fields: List<FormField> = emptyList(),
)

@Serializable
data class FormField(
    val key: String,
    val title: String? = null,
    val description: String? = null,
    val type: String? = null,
    val required: Boolean = false,
    val custom: Boolean = false,
    val options: List<FormOption> = emptyList(),
)

@Serializable
data class FormOption(val value: String, val label: String, val description: String? = null)

@Serializable
data class ServerInfo(val version: String? = null, val pid: Int? = null, val urls: List<String> = emptyList())

/** One server-run command. `status` is "running" until it exits; `exit` is only set once it has. */
@Serializable
data class ShellInfo(val id: String, val status: String? = null, val command: String? = null, val exit: Int? = null)

/** A page of a shell's combined output from an absolute byte cursor. */
@Serializable
data class ShellOutput(val output: String = "", val cursor: Long = 0, val size: Long = 0, val truncated: Boolean = false)

/** One server-run PTY: a program with a real stdin, unlike the one-shot [ShellInfo]. */
@Serializable
data class PtyInfo(
    val id: String,
    val title: String? = null,
    val command: String? = null,
    val status: String? = null,
    val exitCode: Int? = null,
)

@Serializable
private class Data<T>(val data: List<T> = emptyList())

@Serializable
private class One<T>(val data: T)

/** The short-lived ticket that authenticates one PTY socket. */
@Serializable
private class Ticket(val ticket: String)

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** SSE frame buffer: accumulates "data:" lines, returns the event once a blank line closes it. */
internal class SseBuffer {
    private val data = StringBuilder()

    fun push(line: String): String? {
        if (line.isBlank()) return if (data.isEmpty()) null else data.toString().also { data.clear() }
        if (line.startsWith(":")) return null
        if (line.startsWith("data:")) data.append(line.removePrefix("data:").trim())
        return null
    }
}

/**
 * opencode HTTP API (v2): Basic auth and the api endpoints, with an SSE stream for live answers.
 * Verified against opencode v2.0.32 and its openapi.json.
 */
class OpenCodeClient(baseUrl: String, private val password: String) {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }

    // readTimeout 0: the stream stays silent for long stretches between events.
    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    // A PTY socket sits idle between keystrokes, so it pings to keep the connection from being dropped.
    private val socketClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    // The server checks Basic auth; the user name is fixed, the password is printed at startup.
    private val auth: String = "Basic " + Base64.encodeToString(
        "opencode:$password".toByteArray(Charsets.UTF_8),
        Base64.NO_WRAP,
    )

    private fun request(path: String) = Request.Builder()
        .url(base + path)
        .header("Authorization", auth)

    private suspend fun call(method: String, path: String, body: String? = null, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val payload = (body ?: "{}").toRequestBody(JSON_TYPE)
            val builder = request(path)
            headers.forEach { (name, value) -> builder.header(name, value) }
            val req = builder.method(method, if (method == "GET" || method == "DELETE") null else payload).build()
            http.newCall(req).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw IOException("$method $path → ${r.code}. ${text.take(180)}")
                text
            }
        }

    suspend fun info(): ServerInfo = json.decodeFromString(ServerInfo.serializer(), call("GET", "/api/info"))

    suspend fun agents(): List<AgentInfo> =
        json.decodeFromString(Data.serializer(AgentInfo.serializer()), call("GET", "/api/agent")).data

    suspend fun models(): List<ModelInfo> =
        json.decodeFromString(Data.serializer(ModelInfo.serializer()), call("GET", "/api/model")).data

    /**
     * Commands are scoped to a directory and the server registers plugins after it starts listening,
     * so the caller fetches them with the session's directory every time it needs them.
     */
    suspend fun commands(directory: String? = null): List<CommandInfo> {
        val query = directory?.let { "?location[directory]=" + URLEncoder.encode(it, "UTF-8") }.orEmpty()
        return json.decodeFromString(Data.serializer(CommandInfo.serializer()), call("GET", "/api/command$query")).data
    }

    /** A one-shot runner: the server owns the process and pages its combined output back by cursor. */
    suspend fun runShell(command: String, cwd: String?): ShellInfo {
        val body = buildJsonObject {
            put("command", command)
            if (cwd != null) put("cwd", cwd)
        }
        return json.decodeFromString(One.serializer(ShellInfo.serializer()), call("POST", "/api/shell", body.toString())).data
    }

    /** The shell row is where the status and the exit code live once the command has exited. */
    suspend fun shell(id: String): ShellInfo =
        json.decodeFromString(One.serializer(ShellInfo.serializer()), call("GET", "/api/shell/$id")).data

    suspend fun shellOutput(id: String, cursor: Long): ShellOutput =
        json.decodeFromString(One.serializer(ShellOutput.serializer()), call("GET", "/api/shell/$id/output?cursor=$cursor")).data

    /** A PTY is a program with a stdin, unlike [runShell]: the first word is exec'd with the rest as args. */
    suspend fun createPty(command: String, args: List<String> = emptyList(), cwd: String? = null, title: String? = null): PtyInfo {
        val body = buildJsonObject {
            put("command", command)
            if (args.isNotEmpty()) put("args", json.encodeToJsonElement(args))
            if (cwd != null) put("cwd", cwd)
            if (title != null) put("title", title)
        }
        return json.decodeFromString(One.serializer(PtyInfo.serializer()), call("POST", "/api/pty", body.toString())).data
    }

    suspend fun pty(id: String): PtyInfo =
        json.decodeFromString(One.serializer(PtyInfo.serializer()), call("GET", "/api/pty/$id")).data

    /** The server kills the process with the row. */
    suspend fun deletePty(id: String) {
        call("DELETE", "/api/pty/$id")
    }

    /** The socket is authenticated by a ticket, not by the Basic header alone; the header names the kind. */
    suspend fun ptyTicket(id: String): String =
        json.decodeFromString(One.serializer(Ticket.serializer()), call("POST", "/api/pty/$id/connect-token", headers = mapOf("x-opencode-ticket" to "1"))).data.ticket

    /**
     * Opens the PTY socket and routes its frames to the callbacks. Text frames are raw terminal chunks,
     * the one binary frame is the absolute output cursor, and a close or failure ends the run. The
     * caller owns the returned socket and closes it. Resize (`PUT /api/pty/{id}`) is not wired:
     * the phone only reads and sends lines, so the server's default size is enough.
     */
    fun ptySocket(
        id: String,
        ticket: String,
        directory: String?,
        onText: (String) -> Unit,
        onControl: (Long) -> Unit,
        onClosed: () -> Unit,
    ): WebSocket {
        // http -> ws and https -> wss fall out of the same prefix swap.
        val scheme = base.replaceFirst("http", "ws")
        // A path segment encodes a space as %20, not the + a form body would use.
        val location = directory?.let { "&location[directory]=" + URLEncoder.encode(it, "UTF-8").replace("+", "%20") }.orEmpty()
        val url = "$scheme/api/pty/$id/connect?ticket=${URLEncoder.encode(ticket, "UTF-8")}$location"
        val req = Request.Builder().url(url).header("Authorization", auth).build()
        return socketClient.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) = onText(text)

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // The one binary frame is the cursor: a 0x00 byte, then the JSON. Anything else is output.
                val raw = bytes.toByteArray()
                if (raw.isEmpty() || raw[0] != 0.toByte()) return
                val cursor = runCatching {
                    json.parseToJsonElement(String(raw, 1, raw.size - 1, Charsets.UTF_8)).jsonObject.str("cursor")?.toLongOrNull()
                }.getOrNull()
                if (cursor != null) onControl(cursor)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = onClosed()

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = onClosed()
        })
    }

    suspend fun permissions(sessionID: String): List<PermissionRequest> =
        json.decodeFromString(Data.serializer(PermissionRequest.serializer()), call("GET", "/api/session/$sessionID/permission")).data

    suspend fun replyPermission(sessionID: String, requestID: String, decision: String) {
        call(
            "POST",
            "/api/session/$sessionID/permission/$requestID/reply",
            buildJsonObject { put("decision", decision) }.toString(),
        )
    }

    /** Pending forms only: a form leaves this list once it is answered or dismissed. */
    suspend fun forms(sessionID: String): List<FormInfo> =
        json.decodeFromString(Data.serializer(FormInfo.serializer()), call("GET", "/api/session/$sessionID/form")).data

    /** `answer` keys are field keys; single choice and text send a string, multiselect an array. */
    suspend fun replyForm(sessionID: String, formID: String, answer: JsonObject) {
        call(
            "POST",
            "/api/session/$sessionID/form/$formID/reply",
            buildJsonObject { put("answer", answer) }.toString(),
        )
    }

    suspend fun cancelForm(sessionID: String, formID: String) {
        call("DELETE", "/api/session/$sessionID/form/$formID")
    }

    suspend fun setModel(sessionID: String, providerID: String, modelID: String, variant: String? = null) {
        val body = buildJsonObject {
            put("model", buildJsonObject {
                put("providerID", providerID)
                put("id", modelID)
                // The server rejects a null variant, so it is omitted until the model has one.
                if (variant != null) put("variant", variant)
            })
        }
        call("POST", "/api/session/$sessionID/model", body.toString())
    }

    suspend fun setAgent(sessionID: String, agent: String) {
        call("POST", "/api/session/$sessionID/agent", buildJsonObject { put("agent", agent) }.toString())
    }

    suspend fun runCommand(sessionID: String, name: String, text: String) {
        val body = buildJsonObject {
            put("name", name)
            put("text", text)
        }
        call("POST", "/api/session/$sessionID/command", body.toString())
    }

    suspend fun sessions(search: String? = null): List<Session> {
        val query = search?.takeIf { it.isNotBlank() }?.let { "&search=" + URLEncoder.encode(it, "UTF-8") }.orEmpty()
        return json.decodeFromString(Data.serializer(Session.serializer()), call("GET", "/api/session?limit=50$query")).data
    }

    suspend fun createSession(agent: String? = null, model: ModelRef? = null): Session {
        val body = buildJsonObject {
            if (agent != null) put("agent", agent)
            if (model != null) put("model", buildJsonObject {
                put("providerID", model.providerID)
                put("id", model.id)
                // The server rejects a null variant, so it is omitted until the model has one.
                if (model.variant != null) put("variant", model.variant)
            })
        }
        return json.decodeFromString(One.serializer(Session.serializer()), call("POST", "/api/session", body.toString())).data
    }

    suspend fun messages(sessionID: String): List<Message> =
        json.decodeFromString(Data.serializer(Message.serializer()), call("GET", "/api/session/$sessionID/message"))
            .data
            .sortedBy { it.time?.created ?: 0L }
            .filter { it.type == "user" || it.type == "assistant" }

    /** One message, newest first: the list preview only needs the tail of a session. */
    suspend fun lastMessage(sessionID: String): Message? =
        json.decodeFromString(Data.serializer(Message.serializer()), call("GET", "/api/session/$sessionID/message?limit=1&order=desc"))
            .data
            .firstOrNull()

    suspend fun send(sessionID: String, text: String, files: List<PromptFile> = emptyList(), agents: List<PromptAgent> = emptyList()) {
        val body = buildJsonObject {
            put("text", text)
            // A prompt with no files or mentions must not carry an empty array.
            if (files.isNotEmpty()) put("files", json.encodeToJsonElement(files))
            if (agents.isNotEmpty()) put("agents", json.encodeToJsonElement(agents))
        }
        call("POST", "/api/session/$sessionID/prompt", body.toString())
    }

    suspend fun interrupt(sessionID: String) {
        call("POST", "/api/session/$sessionID/interrupt", "{}")
    }

    /** Turn the transcript into a summary; the server summarises it in the background. */
    suspend fun compact(sessionID: String) {
        call("POST", "/api/session/$sessionID/compact", "{}")
    }

    /** A fork keeps the transcript and returns the fresh session it was copied into. */
    suspend fun fork(sessionID: String): Session =
        json.decodeFromString(One.serializer(Session.serializer()), call("POST", "/api/session/$sessionID/fork", "{}"))
            .data

    /** Rewind to just before a message; the clear call walks an undo forward again. */
    suspend fun revertStage(sessionID: String, messageID: String) {
        call("POST", "/api/session/$sessionID/revert/stage", buildJsonObject { put("messageID", messageID) }.toString())
    }

    suspend fun revertClear(sessionID: String) {
        call("DELETE", "/api/session/$sessionID/revert")
    }

    suspend fun retitle(sessionID: String, title: String) {
        call("PATCH", "/api/session/$sessionID", buildJsonObject { put("title", title) }.toString())
    }

    /** The server answers 204 with no body, so there is nothing to decode. */
    suspend fun deleteSession(sessionID: String) {
        call("DELETE", "/api/session/$sessionID")
    }

    /** The server's portable JSON bundle, handed to the share sheet unread so it can be imported again. */
    suspend fun exportSession(sessionID: String): String =
        call("GET", "/api/experimental/session/$sessionID/export")

    /** The server re-reads its config; used after the user edits one outside the app. */
    suspend fun reloadConfig() {
        call("POST", "/api/location/reload", "{}")
    }

    /** Live events for every session on the server. The caller filters by sessionID. No replay. */
    fun events(): Flow<JsonObject> = callbackFlow {
        val httpCall = http.newCall(request("/api/event").build())
        val reader = launch(Dispatchers.IO) {
            try {
                httpCall.execute().use { r ->
                    if (!r.isSuccessful) throw IOException("The event stream → ${r.code}")
                    val source = (r.body ?: throw IOException("The event stream returned nothing")).source()
                    val frames = SseBuffer()
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        val payload = frames.push(line) ?: continue
                        val obj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
                        if (obj != null) trySend(obj)
                    }
                }
                close()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                close(e)
            }
        }
        awaitClose {
            reader.cancel()
            httpCall.cancel()
        }
    }
}

/** The sentence a row shows under a title, whichever kind of message it came from. */
fun Message.preview(): String {
    val body = text ?: content.orEmpty().firstOrNull { it.type == "text" }?.text
    return body.orEmpty().replace(Regex("\\s+"), " ").trim()
}
