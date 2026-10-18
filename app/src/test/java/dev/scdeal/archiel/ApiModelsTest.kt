package dev.scdeal.archiel

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Rows copied from a live v2.0.32 server; `cost` is an array and sessions carry their directory. */
class ApiModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a model row decodes even though cost is an array`() {
        val row = """
            {"id":"nemotron-3.5-super-vl-free","modelID":"nemotron-3.5-super-vl-free",
             "providerID":"opencode","name":"Nemotron 3.5 Super VL Free","variants":[{"id":"high"}],
             "cost":[{"input":0,"output":0,"cache":{"read":0,"write":0}}],
             "limit":{"context":1048576,"output":131072},"status":"active","enabled":true}
        """.trimIndent()
        val model = json.decodeFromString(ModelInfo.serializer(), row)
        assertEquals("opencode", model.providerID)
        assertEquals("nemotron-3.5-super-vl-free", model.modelID)
        assertEquals("Nemotron 3.5 Super VL Free", model.name)
    }

    @Test
    fun `a session carries the directory its commands come from`() {
        val row = """
            {"id":"ses_1","projectID":"p1","cost":0,
             "tokens":{"input":0,"output":0,"reasoning":0,"cache":{"read":0,"write":0}},
             "time":{"created":1,"updated":2},"location":{"directory":"/home/x/project"}}
        """.trimIndent()
        val session = json.decodeFromString(Session.serializer(), row)
        assertEquals("/home/x/project", session.location?.directory)
    }

    @Test
    fun `a reasoning part keeps its text and the time it took`() {
        val row = """
            {"id":"prt_1","type":"reasoning",
             "text":"**Inspecting the ARK layout**\n\nNeed to surface space for the session list.",
             "time":{"created":1000,"completed":43000}}
        """.trimIndent()
        val part = json.decodeFromString(Part.serializer(), row)
        assertEquals("reasoning", part.type)
        assertEquals(true, part.text?.startsWith("**Inspecting"))
        assertEquals(43000, part.time?.completed)
    }
}
