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
        assertEquals(Block.Bullet("one *italic* and **bold**", "•", 0), blocks[1])
        assertEquals(Block.Bullet("two", "2.", 0), blocks[2])
        assertEquals(Block.Code(listOf("val a = 1"), "kotlin"), blocks[3])
        assertEquals(Block.Paragraph("trailing line"), blocks[4])
    }

    @Test
    fun `gfm tables quotes rules tasks and nesting split on their own lines`() {
        val blocks = parseBlocks(
            """
            A | B
            --- | ---
            1 | 2

            > quoted **note**

            ---

            - top
              - nested *deep*
            - [x] done task
            - [ ] open task

            text ~~struck~~ and [a link](https://docs.dev/x) plus `` `c` ``
            """.trimIndent(),
        )
        assertEquals(8, blocks.size)
        assertEquals(Block.Table(listOf("A", "B"), listOf(listOf("1", "2"))), blocks[0])
        assertEquals(Block.Quote("quoted **note**"), blocks[1])
        assertEquals(Block.Rule, blocks[2])
        assertEquals(Block.Bullet("top", "•", 0), blocks[3])
        assertEquals(Block.Bullet("nested *deep*", "•", 1), blocks[4])
        assertEquals(Block.Task("done task", done = true, 0), blocks[5])
        assertEquals(Block.Task("open task", done = false, 0), blocks[6])
        assertEquals(Block.Paragraph("text ~~struck~~ and [a link](https://docs.dev/x) plus `` `c` ``"), blocks[7])
    }
}
