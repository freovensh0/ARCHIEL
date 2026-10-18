package dev.scdeal.archiel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Transparent = Color.Transparent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { ArchielTheme { App() } }
    }
}

private enum class Screen { Sessions, Chat, Connection, Pickers }

private typealias BarAction = Triple<ImageVector, String, () -> Unit>

@Composable
private fun App() {
    val ctx = LocalContext.current
    var cfg by remember { mutableStateOf(Cfg.load(ctx)) }
    var screen by rememberSaveable { mutableStateOf(Screen.Sessions) }
    val vm: ChatViewModel = viewModel(key = "${cfg.baseUrl}|${cfg.password}")
    val state by vm.state.collectAsState()

    BackHandler(enabled = screen != Screen.Sessions) { screen = Screen.Sessions }

    when (screen) {
        Screen.Sessions -> SessionsScreen(
            state = state,
            address = vm.address,
            onOpen = { vm.open(it); screen = Screen.Chat },
            onNew = { vm.newSession { screen = Screen.Chat } },
            onPreview = vm::preview,
            onRefresh = vm::refresh,
            onSettings = { screen = Screen.Connection },
        )

        Screen.Chat -> ChatScreen(
            state = state,
            onBack = { vm.close(); screen = Screen.Sessions },
            onSend = vm::send,
            onStop = vm::stop,
            onRunCommand = vm::runCommand,
            onReplyPermission = vm::replyPermission,
            onOpenPickers = { vm.loadPickers(); screen = Screen.Pickers },
            onLoadCommands = vm::loadCommands,
        )

        Screen.Pickers -> PickerScreen(
            state = state,
            onBack = { screen = Screen.Chat },
            onPickModel = { provider, model ->
                vm.setModel(provider, model)
                screen = Screen.Chat
            },
            onPickAgent = { agent ->
                vm.setAgent(agent)
                screen = Screen.Chat
            },
        )

        Screen.Connection -> ConnectionScreen(
            cfg = cfg,
            state = state,
            onBack = { screen = Screen.Sessions },
            onRefresh = vm::refresh,
            onSave = { Cfg.save(ctx, it); cfg = it; screen = Screen.Sessions },
        )
    }
}

// ------------------------------------------------------------------ chrome

@Composable
private fun TopBar(title: String, onBack: (() -> Unit)? = null, status: String? = null, actions: List<BarAction> = emptyList()) {
    Column {
        // The actions sit flush right; only the top edge is inset so a side cutout cannot push them left.
        Row(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .fillMaxWidth()
                .height(Size.bar)
                .padding(start = if (onBack == null) Space.l else Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) BarButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
            // Filling the remaining width here is what pushes the actions onto the right edge; a
            // trailing weighted spacer leaves them short of it by whatever the title doesn't use.
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = Space.xs),
            )
            if (status != null) {
                Spacer(Modifier.width(Space.s))
                LiveDot()
                Spacer(Modifier.width(Space.xs))
                Text(status, style = MaterialTheme.typography.labelSmall)
            }
            actions.forEach { (icon, label, onClick) -> BarButton(icon, label, onClick) }
        }
        Hairline()
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(Size.hitSmall)
            .clip(RoundedCornerShape(Radius.chip))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(Size.hairline).background(MaterialTheme.colorScheme.outline))
}

@Composable
private fun LiveDot(color: Color = AccentFill) {
    Box(Modifier.size(Size.dot).clip(CircleShape).background(color))
}

@Composable
private fun LiveStrip(text: String, dot: Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveDot(dot)
        Spacer(Modifier.width(Space.s))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
    Hairline()
}

@Composable
private fun Banner(text: String) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(Space.l),
        verticalAlignment = Alignment.Top,
    ) {
        LiveDot(MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(Space.m))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
    Hairline()
}

// ------------------------------------------------------------------ sessions

@Composable
private fun SessionsScreen(
    state: ChatState,
    address: String,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onPreview: (String) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(
            title = "ARCHIEL",
            actions = listOf(
                BarAction(Icons.Outlined.Refresh, "Refresh", onRefresh),
                BarAction(Icons.Outlined.Settings, "Connection settings", onSettings),
            ),
        )
        LiveStrip(
            text = when (state.online) {
                true -> "$address · connected"
                false -> "$address · not answering"
                null -> "$address · checking"
            },
            dot = if (state.online == true) AccentFill else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            state.sessions.isEmpty() && state.online == false -> OfflineState(address, onRefresh)
            state.sessions.isEmpty() && state.loading -> LoadingRows()
            state.sessions.isEmpty() -> EmptyState(address)
            else -> LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                item("new") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNew)
                            .heightIn(min = Size.row)
                            .padding(horizontal = Space.l, vertical = Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("New session", style = MaterialTheme.typography.titleMedium, color = AccentFill)
                            Text(
                                "Starts a session on the connected server",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(Space.m))
                        Icon(Icons.Outlined.Add, "New session", tint = AccentFill, modifier = Modifier.size(20.dp))
                    }
                    Hairline()
                }
                items(state.sessions, key = { it.id }) { session ->
                    SessionRow(
                        title = session.title?.takeIf { it.isNotBlank() } ?: "Untitled session",
                        preview = state.previews[session.id],
                        stamp = stamp(session.time?.updated),
                        onClick = { onOpen(session.id) },
                        onPreview = { onPreview(session.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(title: String, preview: String?, stamp: String, onClick: () -> Unit, onPreview: () -> Unit) {
    LaunchedEffect(title) { onPreview() }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Size.row)
            .padding(horizontal = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                preview.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.m))
        Text(stamp, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 3.dp))
    }
    Hairline()
}

@Composable
private fun EmptyState(address: String) {
    Centered(
        title = "Nothing here yet",
        body = "The server is reachable but has no sessions. Open one, or let opencode start one for you.",
        foot = address,
    )
}

@Composable
private fun OfflineState(address: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "The server is not answering",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.s))
        Text(
            "$address refused the connection. Start it with opencode serve --port 4096, then retry.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        Spacer(Modifier.height(Space.xl))
        PrimaryButton("Retry", onRetry, Modifier.width(200.dp))
    }
}

@Composable
private fun LoadingRows() {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.l)) {
        repeat(5) { index ->
            Column(Modifier.fillMaxWidth().heightIn(min = Size.row).padding(vertical = Space.m)) {
                SkeletonBar(if (index % 3 == 0) 0.62f else 0.8f)
                Spacer(Modifier.height(Space.s))
                SkeletonBar(if (index % 2 == 0) 0.38f else 0.52f, smaller = true)
            }
            Hairline()
        }
    }
}

@Composable
private fun SkeletonBar(fraction: Float, smaller: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(if (smaller) 9.dp else 11.dp)
            .clip(RoundedCornerShape(Radius.chip))
            .background(MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun Centered(title: String, body: String, foot: String? = null) {
    Column(
        Modifier.fillMaxSize().padding(Space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Space.s))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        if (foot != null) {
            Spacer(Modifier.height(Space.m))
            Text(foot, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ------------------------------------------------------------------ chat

@Composable
private fun ChatScreen(
    state: ChatState,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onRunCommand: (String, String) -> Unit,
    onReplyPermission: (String, String) -> Unit,
    onOpenPickers: () -> Unit,
    onLoadCommands: () -> Unit,
) {
    val list = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.messages.size, state.stream.length, state.thinking.length) {
        val count = list.layoutInfo.totalItemsCount
        if (count > 0) list.animateScrollToItem(count - 1)
    }

    // Commands are per directory and the server registers plugins late, so ask again when the dock
    // reaches for the list rather than trusting what was fetched at startup.
    LaunchedEffect(input.startsWith("/")) { if (input.startsWith("/")) onLoadCommands() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(
            title = state.title?.takeIf { it.isNotBlank() } ?: "Chat",
            onBack = onBack,
            status = if (state.busy) "working" else null,
            actions = listOf(
                BarAction(Icons.Outlined.Tune, "Switch model or agent", onOpenPickers),
            ),
        )
        if (state.cutShort) {
            Banner("The stream ended before the answer finished. The text below is all the server sent.")
        }
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = Space.s, bottom = Space.xl),
        ) {
            if (state.messages.isEmpty() && state.stream.isEmpty()) {
                item("empty") {
                    Centered("Start here", "Messages run in the opencode session on the server.")
                }
            }
            items(state.messages, key = { it.id }) { message -> Turn(message) }
            if (state.busy) {
                item("live") { LiveTurn(state.tools, state.thinking, state.stream) }
            }
        }
        state.permissions.firstOrNull()?.let { request ->
            PermissionCard(request, onReply = { decision -> onReplyPermission(request.id, decision) })
        }
        Dock(
            input = input,
            onInput = { input = it },
            busy = state.busy,
            commands = state.commands,
            model = state.model,
            onSend = {
                val text = input.trim()
                if (text.startsWith("/")) {
                    val name = text.removePrefix("/").substringBefore(' ')
                    onRunCommand(name, text.removePrefix("/$name").trim())
                } else {
                    onSend(input)
                }
                input = ""
            },
            onStop = onStop,
        )
    }
}

@Composable
private fun PermissionCard(request: PermissionRequest, onReply: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(Space.m)
            .clip(RoundedCornerShape(Radius.control))
            .background(MaterialTheme.colorScheme.surface)
            .border(Size.hairline, AccentFill, RoundedCornerShape(Radius.control))
            .padding(Space.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(Space.s))
            Text("Permission needed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(Space.s))
        Text(
            request.message ?: "The agent wants to run ${request.action}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        request.resources.take(2).forEach { resource ->
            Spacer(Modifier.height(Space.xs))
            SelectionContainer {
                Text(
                    resource,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Choice("Allow once", "once", true, onReply)
            Choice("Always", "always", false, onReply)
            Choice("Reject", "reject", false, onReply)
        }
    }
}

@Composable
private fun Choice(label: String, decision: String, primary: Boolean, onReply: (String) -> Unit) {
    Box(
        Modifier
            .heightIn(min = Size.hitSmall)
            .clip(RoundedCornerShape(Radius.control))
            .background(if (primary) AccentFill else MaterialTheme.colorScheme.background)
            .border(Size.hairline, if (primary) AccentFill else MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.control))
            .clickable { onReply(decision) }
            .padding(horizontal = Space.m, vertical = Space.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (primary) OnAccent else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Turn(message: Message) {
    if (message.type == "user") {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
            horizontalArrangement = Arrangement.End,
        ) {
            SelectionContainer {
                Text(
                    message.text.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .clip(RoundedCornerShape(Radius.control))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = Space.l, vertical = Space.m),
                )
            }
        }
        return
    }
    val parts = message.content.orEmpty()
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.l)) {
        if (parts.isEmpty() && message.outcome == "failed") {
            Text(
                "No answer came back.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
        parts.forEach { part ->
            when (part.type) {
                "text" -> AssistantTurn(part.text.orEmpty())
                "tool" -> ToolBlock(part)
                "reasoning" -> ReasoningBlock(part)
                else -> Unit
            }
        }
        if (parts.any { it.type == "text" }) {
            Spacer(Modifier.height(Space.xs))
            Text(stamp(message.time?.created), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(Space.m))
    }
}

@Composable
private fun AssistantTurn(text: String, streaming: Boolean = false) {
    val blocks = remember(text) { parseBlocks(text) }
    Column {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is Block.Code -> CodeBlock(block)
                is Block.Heading -> Text(
                    inline(
                        block.text,
                        streaming && index == blocks.lastIndex,
                    ),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleMedium
                        2 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.labelLarge
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                )
                is Block.Bullet -> Row(Modifier.padding(start = Space.s)) {
                    Text(
                        block.marker,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectionContainer {
                        Text(
                            inline(
                                block.text,
                                streaming && index == blocks.lastIndex,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                is Block.Paragraph -> {
                    val caret = streaming && index == blocks.lastIndex
                    SelectionContainer {
                        Text(
                            inline(block.text, caret),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** The live turn while a run streams: thinking tail, tool calls as they run, then the answer. */
@Composable
private fun LiveTurn(tools: List<Part>, thinking: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.l)) {
        if (thinking.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot()
                Spacer(Modifier.width(Space.s))
                Text("thinking", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(Space.xs))
            SelectionContainer {
                Text(
                    thinking.takeLast(600),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Space.m))
        }
        tools.forEach { ToolBlock(it) }
        if (text.isNotEmpty()) AssistantTurn(text, streaming = true)
    }
}

/** Adapted from opencode's TUI ReasoningPart: collapsed by default, tap toggles the muted body. */
@Composable
private fun ReasoningBlock(part: Part) {
    // OpenRouter encrypts some reasoning blocks; drop the frozen placeholder like the TUI does.
    val text = (part.text ?: "").replace("[REDACTED]", "").trim()
    if (text.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val ran = (part.time?.completed ?: 0) - (part.time?.created ?: 0)
    val duration =
        if (ran > 0) {
            val total = ran / 1000
            if (total >= 60) "${total / 60}m ${total % 60}s" else "${total}s"
        } else {
            null
        }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.chip))
            .clickable { open = !open }
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
            contentDescription = if (open) "Hide thinking" else "Show thinking",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Space.xs))
        Text(
            duration?.let { "Thought $it" } ?: "Thought",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open) {
        Spacer(Modifier.height(Space.xs))
        SelectionContainer {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Space.xs))
    }
}

internal sealed interface Block {
    data class Paragraph(val text: String) : Block
    data class Code(val lines: List<String>, val language: String?) : Block
    data class Heading(val level: Int, val text: String) : Block
    data class Bullet(val text: String, val marker: String) : Block
}

/** Fenced code, headings, list items and blank-line paragraphs. */
internal fun parseBlocks(source: String): List<Block> {
    val lines = source.lines()
    val blocks = mutableListOf<Block>()
    val buffer = mutableListOf<String>()
    fun flush() {
        if (buffer.any { it.isNotBlank() }) {
            blocks += Block.Paragraph(buffer.joinToString("\n").trimEnd())
            buffer.clear()
        }
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
        val plain = line.trimStart()
        val heading = Regex("^(#{1,6})\\s+(.+)$").matchEntire(plain)
        val bullet = Regex("^[-*]\\s+(.+)$").matchEntire(plain)
        val ordered = Regex("^(\\d{1,3})[.)]\\s+(.+)$").matchEntire(plain)
        if (heading != null || bullet != null || ordered != null) {
            flush()
            when {
                heading != null -> blocks += Block.Heading(heading.groupValues[1].length, heading.groupValues[2])
                bullet != null -> blocks += Block.Bullet(bullet.groupValues[1], "• ")
                ordered != null -> blocks += Block.Bullet(ordered.groupValues[2], ordered.groupValues[1] + ". ")
            }
            i++
            continue
        }
        if (line.isBlank() && buffer.isNotEmpty()) {
            blocks += Block.Paragraph(buffer.joinToString("\n"))
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

/** Inline code, bold and italics; a caret rides at the end while the answer streams. */
private fun inline(text: String, caret: Boolean) = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text[i] == '`' && text.indexOf('`', i + 1) > i ->
                withStyle(SpanStyle(fontFamily = Mono, color = Color(0xFFA8A8AD))) {
                    append(text.substring(i + 1, text.indexOf('`', i + 1)))
                }.also { i = text.indexOf('`', i + 1) + 1 }
            text.startsWith("**", i) && text.indexOf("**", i + 2) > i ->
                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                    append(text.substring(i + 2, text.indexOf("**", i + 2)))
                }.also { i = text.indexOf("**", i + 2) + 2 }
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

@Composable
private fun CodeBlock(block: Block.Code) {
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
private fun Caret() {
    val pulse = rememberInfiniteTransition(label = "caret")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "caret-alpha",
    )
    Box(
        Modifier
            .size(width = Size.caretWidth, height = Size.caretHeight)
            .alpha(alpha)
            .clip(RoundedCornerShape(1.dp))
            .background(AccentFill),
    )
}

@Composable
private fun ToolBlock(part: Part) {
    val status = part.state?.status ?: "running"
    val command = (part.state?.input?.get("command") as? JsonPrimitive)?.contentOrNull
    val output = part.state?.content.orEmpty().mapNotNull { it.text }.joinToString("\n")
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s)
            .clip(RoundedCornerShape(Radius.chip))
            .background(MaterialTheme.colorScheme.surface)
            .border(Size.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.chip))
            .padding(Space.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(
                when (status) {
                    "completed" -> AccentFill
                    "error" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.width(Space.s))
            Text("${part.name ?: "tool"} · $status", style = MaterialTheme.typography.labelSmall)
        }
        if (command != null) {
            Spacer(Modifier.height(Space.s))
            SelectionContainer {
                Text(
                    "\$ $command",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (output.isNotBlank()) {
            Spacer(Modifier.height(Space.s))
            Hairline()
            Spacer(Modifier.height(Space.s))
            SelectionContainer {
                Text(
                    output,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Dock(
    input: String,
    onInput: (String) -> Unit,
    busy: Boolean,
    commands: List<CommandInfo>,
    model: String?,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val active = busy || input.isNotBlank()
    val matching = if (input.startsWith("/")) {
        val query = input.removePrefix("/").substringBefore(' ').lowercase()
        commands.filter { it.name.lowercase().startsWith(query) }.take(6)
    } else {
        emptyList()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Hairline()
        if (matching.isNotEmpty()) {
            Column(Modifier.padding(horizontal = Space.s, vertical = Space.xs)) {
                matching.forEach { command ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.chip))
                            .clickable { onInput("/${command.name} ") }
                            .padding(horizontal = Space.m, vertical = Space.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "/${command.name}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                            color = AccentFill,
                        )
                        Spacer(Modifier.width(Space.m))
                        Text(
                            command.description.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(Space.s), verticalAlignment = Alignment.Bottom) {
            TextField(
                value = input,
                onValueChange = onInput,
                placeholder = {
                    Text(
                        "What should we build?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                shape = RoundedCornerShape(Radius.panel),
                maxLines = 5,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Transparent,
                    unfocusedIndicatorColor = Transparent,
                    disabledIndicatorColor = Transparent,
                    cursorColor = AccentFill,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Space.s))
            Box(
                Modifier
                    .size(Size.hit)
                    .clip(CircleShape)
                    .background(if (active) AccentFill else MaterialTheme.colorScheme.surface)
                    .then(
                        if (active) Modifier
                        else Modifier.border(Size.hairline, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    .clickable(enabled = active) { if (busy) onStop() else onSend() },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    Box(Modifier.size(13.dp).clip(RoundedCornerShape(2.dp)).background(OnAccent))
                } else {
                    Icon(
                        Icons.Outlined.Send,
                        "Send",
                        modifier = Modifier.size(20.dp),
                        tint = if (input.isNotBlank()) OnAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = Space.m, end = Space.m, top = 3.dp, bottom = 5.dp),
        ) {
            Text("enter to send", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.weight(1f))
            Text(
                model ?: "opencode",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(Space.s))
    }
}


// ------------------------------------------------------------------ pickers

@Composable
private fun PickerScreen(
    state: ChatState,
    onBack: () -> Unit,
    onPickModel: (String, String) -> Unit,
    onPickAgent: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val models = remember(state.models, query) {
        val q = query.trim().lowercase()
        state.models
            .filter { q.isEmpty() || it.modelID.lowercase().contains(q) || it.providerID.lowercase().contains(q) }
            .sortedBy { it.providerID }
            .take(80)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(title = "Model and agent", onBack = onBack)
        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = {
                Text(
                    if (state.models.isEmpty()) "Search models" else "Search ${state.models.size} models",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            shape = RoundedCornerShape(Radius.chip),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Transparent,
                unfocusedIndicatorColor = Transparent,
                disabledIndicatorColor = Transparent,
                cursorColor = AccentFill,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m),
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (query.isEmpty()) {
                item("agents") {
                    Text(
                        "AGENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = Space.l, vertical = Space.s),
                    )
                }
                items(state.agents, key = { "agent-${it.id}" }) { agent ->
                    PickerRow(
                        title = agent.name,
                        detail = agent.mode ?: "primary",
                        selected = state.agent == agent.id,
                        mono = false,
                        onClick = { onPickAgent(agent.id) },
                    )
                }
                item("models") {
                    Text(
                        "MODEL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = Space.l, vertical = Space.s),
                    )
                }
            }
            items(models, key = { "${it.providerID}/${it.modelID}" }) { model ->
                PickerRow(
                    title = model.modelID,
                    detail = model.providerID,
                    selected = state.model == "${model.providerID}/${model.modelID}",
                    mono = true,
                    onClick = { onPickModel(model.providerID, model.modelID) },
                )
            }
            if (models.isEmpty()) {
                item("none") { Centered("No match", "Nothing on this server matches that search.") }
            }
        }
    }
}

@Composable
private fun PickerRow(title: String, detail: String, selected: Boolean, mono: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Size.row)
            .padding(horizontal = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (mono) MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono) else MaterialTheme.typography.titleMedium,
                color = if (selected) AccentFill else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Spacer(Modifier.width(Space.m))
            LiveDot(AccentFill)
        }
    }
    Hairline()
}

// ------------------------------------------------------------------ connection

@Composable
private fun ConnectionScreen(
    cfg: ServerConfig,
    state: ChatState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSave: (ServerConfig) -> Unit,
) {
    var address by rememberSaveable { mutableStateOf(cfg.baseUrl) }
    var password by rememberSaveable { mutableStateOf(cfg.password) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(
            title = "Connection",
            onBack = onBack,
            actions = listOf(BarAction(Icons.Outlined.Refresh, "Check again", onRefresh)),
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(Space.l))
            StatusCard(state)
            FieldGroup("SERVER ADDRESS", address, { address = it }, "http://127.0.0.1:4096")
            Hairline()
            FieldGroup("PASSWORD", password, { password = it }, "printed by opencode serve", secret = true)
            Hairline()
            Text(
                "The password is the \"server password\" line that opencode serve prints when it starts. " +
                    "If the server runs in Termux on this phone, the address is 127.0.0.1:4096.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Space.l),
            )
            Spacer(Modifier.height(Space.xl))
            PrimaryButton(
                "Save and reconnect",
                { onSave(ServerConfig(address.trim(), password)) },
                Modifier.padding(horizontal = Space.l).fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.xl))
        }
    }
}

@Composable
private fun StatusCard(state: ChatState) {
    Column(
        Modifier
            .padding(horizontal = Space.l)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.control))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Space.l, vertical = Space.s),
    ) {
        StatusRow(
            "Status",
            when (state.online) {
                true -> "connected"
                false -> "not answering"
                null -> "checking"
            },
            AccentFill.takeIf { state.online == true },
        )
        StatusRow("Server", state.serverVersion ?: "unknown")
        StatusRow("Latency", state.latencyMs?.let { "$it ms" } ?: "-")
        StatusRow("Sessions", if (state.online == true) state.sessions.size.toString() else "-")
    }
}

@Composable
private fun StatusRow(key: String, value: String, dot: Color? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                LiveDot(dot)
                Spacer(Modifier.width(Space.s))
            }
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun FieldGroup(label: String, value: String, onChange: (String) -> Unit, hint: String, secret: Boolean = false) {
    Column(Modifier.padding(horizontal = Space.l, vertical = Space.m)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(Space.s))
        TextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            placeholder = {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = if (secret) Mono else MaterialTheme.typography.bodyLarge.fontFamily,
            ),
            visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            shape = RoundedCornerShape(Radius.chip),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Transparent,
                unfocusedIndicatorColor = Transparent,
                disabledIndicatorColor = Transparent,
                cursorColor = AccentFill,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(Radius.control))
            .background(AccentFill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = OnAccent)
    }
}

private fun stamp(ms: Long?): String {
    if (ms == null || ms <= 0L) return ""
    val locale = Locale.getDefault()
    val now = Date()
    val then = Date(ms)
    val sameDay = SimpleDateFormat("yyyyMMdd", locale).format(now) == SimpleDateFormat("yyyyMMdd", locale).format(then)
    val sameYear = SimpleDateFormat("yyyy", locale).format(now) == SimpleDateFormat("yyyy", locale).format(then)
    val pattern = when {
        sameDay -> "HH:mm"
        sameYear -> "d MMM"
        else -> "d MMM yyyy"
    }
    return SimpleDateFormat(pattern, locale).format(then)
}
