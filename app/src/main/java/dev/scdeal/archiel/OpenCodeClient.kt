package dev.scdeal.archiel

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
import okhttp3.Credentials.Companion.basic
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

private val JSON_TYPE = "application/json".toMediaType()

@Serializable
data class Session(val id: String, val title: String? = null, val time: SessionTime? = null)

@Serializable
data class SessionTime(val updated: Long = 0)

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
    val type: String? = null,
    val text: String? = null,
    val name: String? = null,
    val state: PartState? = null,
)

@Serializable
data class PartState(
    val status: String? = null,
    val input: JsonObject? = null,
    val content: List<PartText>? = null,
)

@Serializable
data class PartText(val type: String? = null, val text: String? = null)

@Serializable
private class Data<T>(val data: List<T> = emptyList())

@Serializable
private class One<T>(val data: T)

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** SSE kare biriktirici: "data:" satırlarını biriktirir, boş satırda tamamlanmış olayı döndürür. */
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
 * opencode HTTP API'si (v2): Basic auth + /api/* uçları, akış için /api/event (SSE).
 * Doğrulandı: opencode v2.0.32, openapi.json.
 */
class OpenCodeClient(baseUrl: String, password: String) {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }

    // readTimeout 0: SSE bağlantısı uzun süre sessiz kalabilir.
    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private fun request(path: String) = Request.Builder()
        .url(base + path)
        .header("Authorization", basic("opencode", password))

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

    suspend fun sessions(): List<Session> =
        json.decodeFromString(Data.serializer(Session.serializer()), call("GET", "/api/session?limit=50")).data

    suspend fun createSession(): Session =
        json.decodeFromString(One.serializer(Session.serializer()), call("POST", "/api/session")).data

    suspend fun messages(sessionID: String): List<Message> =
        json.decodeFromString(Data.serializer(Message.serializer()), call("GET", "/api/session/$sessionID/message"))
            .data
            .sortedBy { it.time?.created ?: 0L }
            .filter { it.type == "user" || it.type == "assistant" }

    suspend fun send(sessionID: String, text: String) {
        call("POST", "/api/session/$sessionID/prompt", buildJsonObject { put("text", text) }.toString())
    }

    suspend fun interrupt(sessionID: String) {
        call("POST", "/api/session/$sessionID/interrupt", "{}")
    }

    /** Sunucudaki tüm oturumların olayları; çağıran sessionID'ye göre süzer. Yeniden bağlanma yok. */
    fun events(): Flow<JsonObject> = callbackFlow {
        val httpCall = http.newCall(request("/api/event").build())
        val reader = launch(Dispatchers.IO) {
            try {
                httpCall.execute().use { r ->
                    if (!r.isSuccessful) throw IOException("Olay akışı → ${r.code}")
                    val source = (r.body ?: throw IOException("Olay akışı boş gövde")).source()
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
