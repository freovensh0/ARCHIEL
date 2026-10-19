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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
)

@Serializable
data class ModelRef(val id: String, val providerID: String, val variant: String? = null)

fun ModelRef.label(): String = "$providerID/$id" + (variant?.let { "#$it" } ?: "")

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
data class AgentInfo(val id: String, val name: String, val mode: String? = null, val description: String? = null)

// `cost` and `variants` arrive as arrays and `limit` as an object; nothing reads them yet, so they
// stay out of the model rather than making the whole list undecodable.
@Serializable
data class ModelInfo(
    val providerID: String,
    val modelID: String,
    val name: String? = null,
)

@Serializable
data class CommandInfo(val name: String, val description: String? = null)

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

@Serializable
private class Data<T>(val data: List<T> = emptyList())

@Serializable
private class One<T>(val data: T)

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

    // The server checks Basic auth; the user name is fixed, the password is printed at startup.
    private val auth: String = "Basic " + Base64.encodeToString(
        "opencode:$password".toByteArray(Charsets.UTF_8),
        Base64.NO_WRAP,
    )

    private fun request(path: String) = Request.Builder()
        .url(base + path)
        .header("Authorization", auth)

    private suspend fun call(method: String, path: String, body: String? = null): String =
        withContext(Dispatchers.IO) {
            val payload = (body ?: "{}").toRequestBody(JSON_TYPE)
            val req = request(path).method(method, if (method == "GET" || method == "DELETE") null else payload).build()
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

    suspend fun setModel(sessionID: String, providerID: String, modelID: String) {
        val body = buildJsonObject {
            put("model", buildJsonObject {
                put("providerID", providerID)
                put("id", modelID)
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

    suspend fun createSession(): Session =
        json.decodeFromString(One.serializer(Session.serializer()), call("POST", "/api/session")).data

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

    suspend fun send(sessionID: String, text: String) {
        call("POST", "/api/session/$sessionID/prompt", buildJsonObject { put("text", text) }.toString())
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
