package dev.scdeal.archiel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseBufferTest {
    private fun frames(vararg lines: String): List<String?> = SseBuffer().let { buf ->
        lines.map { buf.push(it) }
    }

    @Test
    fun `olay bos satirda tamamlanir`() {
        val out = frames("data: {\"type\":\"a\"}", "")
        assertNull(out[0])
        assertEquals("{\"type\":\"a\"}", out[1])
    }

    @Test
    fun `cok satirli veri birlestirilir`() {
        val out = frames("data: {\"type\":", "\"x\"}", "")
        assertNull(out[1])
        assertEquals("{\"type\":" + "\"x\"}", out[2])
    }

    @Test
    fun `heartbeat ve bosluklar olay uretmez`() {
        val buf = SseBuffer()
        assertNull(buf.push(": heartbeat"))
        assertNull(buf.push(""))
        assertNull(buf.push("data: {\"n\":1}"))
        assertEquals("{\"n\":1}", buf.push(""))
        assertNull(buf.push(""))
    }

    @Test
    fun `iki olay ardisik ayrilir`() {
        val out = frames("data: {\"n\":1}", "", "data: {\"n\":2}", "")
        assertEquals("{\"n\":1}", out[1])
        assertEquals("{\"n\":2}", out[3])
    }
}
