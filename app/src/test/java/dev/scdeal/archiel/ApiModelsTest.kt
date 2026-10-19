package dev.scdeal.archiel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
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
    fun `an assistant message carries its cost, model and token breakdown`() {
        val row = """
            {"id":"msg_1","type":"assistant","cost":0.0423,
             "tokens":{"input":8100,"output":1200,"reasoning":400,"cache":{"read":2500,"write":500}},
             "model":{"id":"claude-sonnet-4","providerID":"anthropic","variant":"high"}}
        """.trimIndent()
        val message = json.decodeFromString(Message.serializer(), row)
        assertEquals(0.0423, message.cost ?: 0.0, 0.0001)
        assertEquals(8100L, message.tokens?.input)
        assertEquals(1200L, message.tokens?.output)
        assertEquals(400L, message.tokens?.reasoning)
        assertEquals(2500L, message.tokens?.cache?.read)
        assertEquals(500L, message.tokens?.cache?.write)
        assertEquals(12700L, message.tokens?.total())
        assertEquals("anthropic", message.model?.providerID)
        assertEquals("claude-sonnet-4", message.model?.id)
    }

    @Test
    fun `a session row carries its running cost and token totals`() {
        val row = """
            {"id":"ses_2","title":"Context work","cost":1.25,
             "tokens":{"input":42000,"output":8000,"reasoning":1500,"cache":{"read":9000,"write":500}}}
        """.trimIndent()
        val session = json.decodeFromString(Session.serializer(), row)
        assertEquals(1.25, session.cost ?: 0.0, 0.0001)
        assertEquals(42000L, session.tokens?.input)
        assertEquals(9000L, session.tokens?.cache?.read)
        assertEquals(61000L, session.tokens?.total())
    }

    @Test
    fun `a model row exposes the context window used by the bar`() {
        val row = """
            {"id":"claude-sonnet-4","modelID":"claude-sonnet-4-20250514","providerID":"anthropic",
             "name":"Claude Sonnet 4","limit":{"context":200000,"output":64000,"input":180000}}
        """.trimIndent()
        val model = json.decodeFromString(ModelInfo.serializer(), row)
        assertEquals("claude-sonnet-4", model.id)
        assertEquals(200000L, model.limit?.context)
    }

    @Test
    fun `a model row lists its variants and context limit`() {
        val row = """
            {"id":"claude-sonnet-4","modelID":"claude-sonnet-4","providerID":"anthropic",
             "name":"Claude Sonnet 4","variants":[{"id":"high"},{"id":"low"}],
             "limit":{"context":200000,"output":64000,"input":180000}}
        """.trimIndent()
        val model = json.decodeFromString(ModelInfo.serializer(), row)
        assertEquals(listOf("high", "low"), model.variants?.map { it.id })
        assertEquals(200000L, model.limit?.context)
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

    @Test
    fun `a todo tool input yields its items in order with their status`() {
        val input = json.decodeFromString(
            JsonObject.serializer(),
            """{"todos":[{"content":"Read the roadmap","status":"completed","priority":"high"},
                         {"content":"Render the panel","status":"in_progress"}]}""",
        )
        assertEquals(
            listOf("Read the roadmap" to "completed", "Render the panel" to "in_progress"),
            todoItems(input),
        )
    }

    @Test
    fun `an older plan input reads entries and defaults a missing status`() {
        val input = json.decodeFromString(JsonObject.serializer(), """{"entries":[{"content":"Ship it"}]}""")
        assertEquals(listOf("Ship it" to "pending"), todoItems(input))
    }

    @Test
    fun `an agent row decodes its mode and hidden flag`() {
        val row = """
            {"id":"explore","name":"Explore","mode":"subagent","description":"Search the codebase.",
             "hidden":true,"model":null,"permissions":{},"steps":0}
        """.trimIndent()
        val agent = json.decodeFromString(AgentInfo.serializer(), row)
        assertEquals("explore", agent.id)
        assertEquals("subagent", agent.mode)
        assertEquals("Search the codebase.", agent.description)
        assertEquals(true, agent.hidden)
        // Older servers omit the field, so a bare row must stay visible.
        assertEquals(false, json.decodeFromString(AgentInfo.serializer(), """{"id":"build","name":"Build"}""").hidden)
    }

    @Test
    fun `a non-todo input has no items`() {
        val input = json.decodeFromString(JsonObject.serializer(), """{"command":"ls"}""")
        assertEquals(null, todoItems(input))
    }

    @Test
    fun `a prompt file carries its uri and an optional name`() {
        val named = json.encodeToJsonElement(
            PromptFile.serializer(),
            PromptFile(uri = "data:image/png;base64,AAAA", name = "shot.png"),
        ).jsonObject
        assertEquals("data:image/png;base64,AAAA", (named["uri"] as? JsonPrimitive)?.contentOrNull)
        assertEquals("shot.png", (named["name"] as? JsonPrimitive)?.contentOrNull)
        // A missing name leaves the key out entirely, so the server never sees a null.
        val nameless = json.encodeToJsonElement(PromptFile.serializer(), PromptFile(uri = "data:x;base64,AA")).jsonObject
        assertEquals(null, nameless["name"])
    }

    @Test
    fun `a model label parses back into a ref, with an optional variant`() {
        val plain = modelRefOf("anthropic/claude-sonnet-4")
        assertEquals("anthropic", plain?.providerID)
        assertEquals("claude-sonnet-4", plain?.id)
        assertEquals(null, plain?.variant)
        val variant = modelRefOf("opencode/nemotron-3.5-super-vl-free#high")
        assertEquals("opencode", variant?.providerID)
        assertEquals("nemotron-3.5-super-vl-free", variant?.id)
        assertEquals("high", variant?.variant)
        assertEquals("opencode/nemotron-3.5-super-vl-free#high", variant?.label())
        // A label with no provider or no id is not a model.
        assertEquals(null, modelRefOf("claude-sonnet-4"))
        assertEquals(null, modelRefOf("anthropic/"))
    }
}
