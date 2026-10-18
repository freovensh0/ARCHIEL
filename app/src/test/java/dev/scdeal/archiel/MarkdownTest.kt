package dev.scdeal.archiel

import org.junit.Assert.assertEquals
import org.junit.Test

/** The markdown the dock renders: fences, headings, list markers, plain paragraphs. */
class MarkdownTest {
    @Test
    fun `headings and list markers split on their own lines`() {
        val blocks = parseBlocks(
            """
            ## Scope
            - one *italic* and **bold**
            2. two

            ```kotlin
            val a = 1
            ```
            trailing line
            """.trimIndent(),
        )
        assertEquals(5, blocks.size)
        assertEquals(Block.Heading(2, "Scope"), blocks[0])
        assertEquals(Block.Bullet("one *italic* and **bold**", "• "), blocks[1])
        assertEquals(Block.Bullet("two", "2. "), blocks[2])
        assertEquals(Block.Code(listOf("val a = 1"), "kotlin"), blocks[3])
        assertEquals(Block.Paragraph("trailing line"), blocks[4])
    }
}
