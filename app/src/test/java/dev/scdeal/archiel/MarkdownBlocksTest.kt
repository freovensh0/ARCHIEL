package dev.scdeal.archiel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBlocksTest {
    @Test
    fun `plain text is one paragraph`() {
        val blocks = parseBlocks("first line\nsecond line")
        assertEquals(1, blocks.size)
        assertEquals("first line\nsecond line", (blocks[0] as Block.Paragraph).text)
    }

    @Test
    fun `a blank line splits paragraphs`() {
        val blocks = parseBlocks("one\n\ntwo")
        assertEquals(2, blocks.size)
        assertEquals("one", (blocks[0] as Block.Paragraph).text)
        assertEquals("two", (blocks[1] as Block.Paragraph).text)
    }

    @Test
    fun `a fenced block becomes code and keeps its language`() {
        val blocks = parseBlocks("before\n\n```kotlin\nval x = 1\nval y = 2\n```\n\nafter")
        assertEquals(3, blocks.size)
        val code = blocks[1] as Block.Code
        assertEquals("kotlin", code.language)
        assertEquals(listOf("val x = 1", "val y = 2"), code.lines)
    }

    @Test
    fun `an unterminated fence still closes the answer`() {
        val blocks = parseBlocks("```sh\nls -la")
        assertEquals(1, blocks.size)
        assertEquals(listOf("ls -la"), (blocks[0] as Block.Code).lines)
    }

    @Test
    fun `a fence inside text does not lose the text around it`() {
        val blocks = parseBlocks("intro\n```\ncode\n```\noutro")
        assertEquals(3, blocks.size)
        assertTrue((blocks[0] as Block.Paragraph).text.startsWith("intro"))
        assertTrue((blocks[2] as Block.Paragraph).text.startsWith("outro"))
    }
}