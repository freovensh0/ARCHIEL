package dev.scdeal.archiel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

internal sealed interface Block {
    data class Paragraph(val text: String) : Block
    data class Code(val lines: List<String>, val language: String?) : Block
    data class Heading(val level: Int, val text: String) : Block
    data class Bullet(val text: String, val marker: String, val level: Int) : Block
    data class Task(val text: String, val done: Boolean, val level: Int) : Block
    data class Quote(val source: String) : Block
    data class Table(val header: List<String>, val rows: List<List<String>>) : Block
    data object Rule : Block
}

private val heading = Regex("^(#{1,6})\\s+(.+)$")

/** Fences, headings, nested lists, tasks, quotes, rules, GFM tables and blank-line paragraphs. */
internal fun parseBlocks(source: String): List<Block> {
    val lines = source.lines()
    val blocks = mutableListOf<Block>()
    val buffer = mutableListOf<String>()
    fun flush() {
        if (buffer.any { it.isNotBlank() }) blocks += Block.Paragraph(buffer.joinToString("\n").trimEnd())
        buffer.clear()
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.trimStart().startsWith("```")) {
            flush()
            val language = line.trimStart().removePrefix("```").trim().ifBlank { null }
            val code = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                code += lines[i]
                i++
            }
            i++
            blocks += Block.Code(code, language)
            continue
        }
        val indent = line.length - line.trimStart().length
        val plain = line.trimStart()
        if (plain.matches(Regex("(-{3,}|\\*{3,}|_{3,})\\s*"))) {
            flush()
            blocks += Block.Rule
            i++
            continue
        }
        // A table is a text row containing pipes and a |---|---| separator on the next line.
        if (plain.contains("|") && i + 1 < lines.size &&
            lines[i + 1].trimStart().matches(Regex("\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*"))
        ) {
            flush()
            val header = splitRow(plain)
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trimStart().contains("|")) {
                rows += splitRow(lines[i].trimStart())
                i++
            }
            blocks += Block.Table(header, rows)
            continue
        }
        if (plain.startsWith(">")) {
            flush()
            val quote = mutableListOf<String>()
            while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                quote += lines[i].trimStart().removePrefix(">").removePrefix(" ")
                i++
            }
            blocks += Block.Quote(quote.joinToString("\n"))
            continue
        }
        val h = heading.matchEntire(plain)
        val t = Regex("^[-*]\\s+\\[([ xX])]\\s+(.+)$").matchEntire(plain)
        val b = Regex("^[-*]\\s+(.+)$").matchEntire(plain)
        val o = Regex("^(\\d{1,3})[.)]\\s+(.+)$").matchEntire(plain)
        if (h != null || t != null || b != null || o != null) {
            flush()
            val level = (indent / 2).coerceAtMost(3)
            when {
                h != null -> blocks += Block.Heading(h.groupValues[1].length, h.groupValues[2])
                t != null -> blocks += Block.Task(t.groupValues[2], t.groupValues[1].isNotBlank(), level)
                b != null -> blocks += Block.Bullet(b.groupValues[1], "•", level)
                else -> blocks += Block.Bullet(o!!.groupValues[2], o!!.groupValues[1] + ".", level)
            }
            i++
            continue
        }
        if (line.isBlank()) {
            if (buffer.any { it.isNotBlank() }) blocks += Block.Paragraph(buffer.joinToString("\n"))
            buffer.clear()
            i++
            continue
        }
        buffer += line
        i++
    }
    if (buffer.any { it.isNotBlank() }) blocks += Block.Paragraph(buffer.joinToString("\n").trimEnd())
    return blocks
}

/** `| a | b |` -> ["a", "b"]; missing outer pipes and blank edge cells tolerated. */
private fun splitRow(row: String): List<String> =
    row.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

/** Code spans, strikes, bold, links and italics; a caret rides at the end while the answer streams. */
@Composable
internal fun inline(text: String, caret: Boolean): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text[i] == '`' && text.indexOf('`', i + 1) > i ->
                withStyle(
                    SpanStyle(fontFamily = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant),
                ) {
                    append(text.substring(i + 1, text.indexOf('`', i + 1)))
                }.also { i = text.indexOf('`', i + 1) + 1 }
            text.startsWith("~~", i) && text.indexOf("~~", i + 2) > i ->
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(text.substring(i + 2, text.indexOf("~~", i + 2)))
                }.also { i = text.indexOf("~~", i + 2) + 2 }
            text.startsWith("**", i) && text.indexOf("**", i + 2) > i ->
                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                    append(text.substring(i + 2, text.indexOf("**", i + 2)))
                }.also { i = text.indexOf("**", i + 2) + 2 }
            // shortcut: scheme-less urls become https; relative and anchor targets stay plain text.
            text.startsWith("[", i) && text.startsWith("](", text.indexOf(']', i)) -> {
                val close = text.indexOf(']', i)
                val end = text.indexOf(')', close + 2)
                if (end > close) {
                    val url = text.substring(close + 2, end)
                    withLink(
                        LinkAnnotation.Url(
                            if (url.contains("://")) url else "https://$url",
                            TextLinkStyles(SpanStyle(color = AccentFill)),
                        ),
                    ) {
                        append(text.substring(i + 1, close))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '*' && text.indexOf('*', i + 1) > i ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(text.substring(i + 1, text.indexOf('*', i + 1)))
                }.also { i = text.indexOf('*', i + 1) + 1 }
            else -> {
                append(text[i])
                i++
            }
        }
    }
    if (caret) withStyle(SpanStyle(color = AccentFill)) { append("\u258C") }
}

/** Renders every block kind; quotes recurse so quoted lists and code work too. */
@Composable
internal fun MarkdownView(blocks: List<Block>, caretOnLast: Boolean) {
    blocks.forEachIndexed { index, block ->
        val caret = caretOnLast && index == blocks.lastIndex
        when (block) {
            is Block.Code -> CodeCard(block)
            is Block.Heading -> Text(
                inline(block.text, caret),
                style = when (block.level) {
                    1 -> MaterialTheme.typography.titleMedium
                    2 -> MaterialTheme.typography.titleSmall
                    else -> MaterialTheme.typography.labelLarge
                },
                color = MaterialTheme.colorScheme.onSurface,
            )
            is Block.Bullet -> BulletRow(block) { inline(block.text, caret) }
            is Block.Task -> TaskRow(block) { inline(block.text, caret) }
            is Block.Paragraph -> SelectionContainer {
                Text(
                    inline(block.text, caret),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            is Block.Quote -> Column(
                Modifier
                    .padding(start = Space.s, end = Space.s, top = Space.xs, bottom = Space.xs)
                    .fillMaxWidth()
                    .border((2).dp, MaterialTheme.colorScheme.outline, RoundedCornerShape((2).dp))
                    .padding(start = Space.m, top = Space.xs, bottom = Space.xs, end = Space.m),
            ) {
                MarkdownView(remember(block) { parseBlocks(block.source) }, caretOnLast = false)
            }
            is Block.Rule ->
                Box(
                    Modifier
                        .padding(vertical = Space.m)
                        .height((1).dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.outline),
                )
            is Block.Table -> TableCard(block)
        }
    }
}

@Composable
private fun BulletRow(block: Block.Bullet, text: @Composable () -> AnnotatedString) {
    Row(Modifier.padding(start = Space.s + Space.m * block.level)) {
        Text(
            block.marker + " ",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                text(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TaskRow(block: Block.Task, text: @Composable () -> AnnotatedString) {
    Row(Modifier.padding(start = Space.s + Space.m * block.level)) {
        Icon(
            if (block.done) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
            contentDescription = if (block.done) "Done" else "Not done",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Space.xs))
        SelectionContainer {
            Text(
                text(),
                style = MaterialTheme.typography.bodyLarge,
                color = if (block.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CodeCard(block: Block.Code) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s)
            .clip(RoundedCornerShape(Radius.chip))
            .background(MaterialTheme.colorScheme.surface)
            .border(Size.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.chip))
            .padding(Space.m),
    ) {
        if (block.language != null) {
            Text(block.language, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(Space.xs))
        }
        SelectionContainer {
            Text(
                block.lines.joinToString("\n"),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TableCard(block: Block.Table) {
    // shortcut: cells wrap as plain text and alignment markers (:---:) are ignored.
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        Row {
            block.header.forEach { cell ->
                Text(
                    cell,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = Space.xs), color = MaterialTheme.colorScheme.outline)
        block.rows.forEach { row ->
            Row(Modifier.padding(top = Space.xs)) {
                row.forEach { cell ->
                    Text(
                        cell,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
