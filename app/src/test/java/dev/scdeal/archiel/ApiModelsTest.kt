package dev.scdeal.archiel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
    fun `a subagent session keeps its parent id and a root stays without one`() {
        val child = json.decodeFromString(
            Session.serializer(),
            """{"id":"ses_child","parentID":"ses_parent","title":"Build the parser"}""",
        )
        assertEquals("ses_parent", child.parentID)
        val root = json.decodeFromString(Session.serializer(), """{"id":"ses_parent"}""")
        assertEquals(null, root.parentID)
    }

    @Test
    fun `a reasoning part keeps its text and the time it took`() {
        val row = """
            {"id":"prt_1","type":"reasoning",
             "text":"**Inspecting the ARCHIEL layout**\n\nNeed to surface room for the session list.",
             "time":{"created":1000,"completed":43000}}
        """.trimIndent()
        val part = json.decodeFromString(Part.serializer(), row)
        assertEquals("reasoning", part.type)
        assertEquals(true, part.text?.startsWith("**Inspecting"))
        assertEquals(43000L, part.time?.completed)
    }

    @Test
    fun `a tool part decodes with its call id and full state`() {
        val row = """
            {"type":"tool","id":"call-1","name":"shell","executed":false,
             "state":{"status":"completed","input":{"command":"echo hi"},
                      "content":[{"type":"text","text":"hi\n"}],"metadata":{"exit":0}},
             "time":{"created":1,"ran":2,"completed":3}}
        """.trimIndent()
        val part = json.decodeFromString(Part.serializer(), row)
        assertEquals("call-1", part.id)
        assertEquals("shell", part.name)
        val command = part.state?.input?.get("command")
        assertEquals("echo hi", (command as? JsonPrimitive)?.contentOrNull)
        assertEquals("hi\n", part.state?.content?.firstOrNull()?.text)
    }

    @Test
    fun `a question form decodes its fields, types and options`() {
        val row = """
            {"id":"frm_1","sessionID":"ses_1","title":"Build settings","metadata":{"origin":"question"},
             "fields":[
               {"key":"q0","title":"Where should it live?","description":"Pick one target.",
                "type":"string","options":[{"value":"local","label":"Local"},{"value":"remote","label":"Remote"}],
                "custom":false,"required":true},
               {"key":"q1","title":"Which formats?","type":"multiselect",
                "options":[{"value":"md","label":"Markdown","description":"Plain text"},
                           {"value":"html","label":"HTML"}],"custom":true}
             ]}
        """.trimIndent()
        val form = json.decodeFromString(FormInfo.serializer(), row)
        assertEquals("frm_1", form.id)
        assertEquals("ses_1", form.sessionID)
        assertEquals("Build settings", form.title)
        assertEquals(2, form.fields.size)
        val single = form.fields[0]
        assertEquals("q0", single.key)
        assertEquals("Where should it live?", single.title)
        assertEquals("string", single.type)
        assertEquals(true, single.required)
        assertEquals(listOf("Local", "Remote"), single.options.map { it.label })
        val multi = form.fields[1]
        assertEquals("multiselect", multi.type)
        assertEquals(true, multi.custom)
        assertEquals("HTML", multi.options[1].label)
        assertEquals("Plain text", multi.options[0].description)
    }

    @Test
    fun `a patch tool part keeps its files metadata`() {
        val row = """
            {"type":"tool","id":"call-patch","name":"patch",
             "state":{"status":"completed","input":{},
                      "metadata":{"files":[{"file":"app/src/main/java/dev/scdeal/archiel/Theme.kt",
                      "patch":"--- a/Theme.kt\n+++ b/Theme.kt\n@@ -1,1 +1,2 @@\n val x = 1\n+val y = 2\n",
                      "additions":1,"deletions":0,"status":"modified"}]}}}
        """.trimIndent()
        val part = json.decodeFromString(Part.serializer(), row)
        assertEquals("patch", part.name)
        val files = part.state?.metadata?.get("files")?.jsonArray
        assertEquals(1, files?.size)
        val file = files?.first()?.jsonObject
        assertEquals("app/src/main/java/dev/scdeal/archiel/Theme.kt", (file?.get("file") as? JsonPrimitive)?.contentOrNull)
        assertEquals("modified", (file?.get("status") as? JsonPrimitive)?.contentOrNull)
        assertEquals("1", (file?.get("additions") as? JsonPrimitive)?.contentOrNull)
        assertEquals(true, (file?.get("patch") as? JsonPrimitive)?.contentOrNull?.contains("@@ -1,1 +1,2 @@"))
    }
}
