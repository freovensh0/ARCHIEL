package dev.scdeal.archiel

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
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
    val canRetry by vm.canRetry.collectAsState()

    BackHandler(enabled = screen != Screen.Sessions) { screen = Screen.Sessions }

    when (screen) {
        Screen.Sessions -> SessionsScreen(
            state = state,
            address = vm.address,
            onOpen = { vm.open(it); screen = Screen.Chat },
            onNew = { vm.newSession { screen = Screen.Chat } },
            onPreview = vm::preview,
            onRefresh = vm::refresh,
            onSearch = vm::search,
            onSettings = { screen = Screen.Connection },
        )

        Screen.Chat -> ChatScreen(
            state = state,
            onRetry = if (canRetry) ({ vm.retry() }) else null,
            onBack = { vm.close(); screen = Screen.Sessions },
            onSend = vm::send,
            onStop = vm::stop,
            onRunCommand = vm::runCommand,
            onReplyPermission = vm::replyPermission,
            onReplyForm = vm::replyForm,
            onCancelForm = vm::cancelForm,
            onOpenPickers = { vm.loadPickers(); screen = Screen.Pickers },
            onLoadCommands = vm::loadCommands,
            onRename = { vm.runCommand("rename", it) },
            onDelete = { vm.deleteSession { vm.close(); screen = Screen.Sessions } },
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
private fun TopBar(title: String, onBack: (() -> Unit)? = null, status: String? = null, actions: List<BarAction> = emptyList(), trailing: (@Composable () -> Unit)? = null) {
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
            trailing?.invoke()
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

/** A display row: a root session keeps its tap and preview; a subagent is read-only under it. */
private data class SessionRowItem(val session: Session, val child: Boolean, val startsGroup: Boolean = true)

/** Server order, each root immediately followed by its subagents. An orphan stays where the server put it. */
private fun sessionRows(sessions: List<Session>): List<SessionRowItem> {
    val ids = sessions.mapTo(HashSet()) { it.id }
    val children = sessions.filter { it.parentID != null }.groupBy { it.parentID }
    val out = ArrayList<SessionRowItem>(sessions.size)
    val placed = HashSet<String>()
    fun add(session: Session, child: Boolean, startsGroup: Boolean) {
        if (!placed.add(session.id)) return
        out += SessionRowItem(session, child, startsGroup)
        children[session.id]?.forEach { add(it, true, false) }
    }
    for (session in sessions) {
        if (session.parentID != null && session.parentID in ids) continue
        add(session, session.parentID != null, true)
    }
    return out
}

/** The time buckets the sessions list is read in; a missing timestamp counts as the oldest. */
private enum class SessionGroup(val key: String, val label: String) {
    Today("today", "Today"),
    ThisWeek("week", "This week"),
    Older("older", "Older"),
}

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

private fun sessionGroup(updated: Long, now: Long): SessionGroup {
    if (updated <= 0L) return SessionGroup.Older
    val day = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    if (day.format(Date(now)) == day.format(Date(updated))) return SessionGroup.Today
    return if (now - updated < WEEK_MS) SessionGroup.ThisWeek else SessionGroup.Older
}

/** A header, or a row that keeps its parent-then-subagents order. */
private sealed interface SessionListEntry {
    data class Header(val group: SessionGroup) : SessionListEntry
    data class Item(val row: SessionRowItem) : SessionListEntry
}

/** Headers are driven by the root session's time; subagents keep following their parent. */
private fun sessionEntries(rows: List<SessionRowItem>, now: Long): List<SessionListEntry> {
    val out = ArrayList<SessionListEntry>(rows.size + 3)
    var current: SessionGroup? = null
    for (row in rows) {
        // A subagent reads inside its parent's bucket; an orphan opens its own because it has none.
        if (row.startsGroup) {
            val group = sessionGroup(row.session.time?.updated ?: 0L, now)
            if (group != current) {
                out += SessionListEntry.Header(group)
                current = group
            }
        }
        out += SessionListEntry.Item(row)
    }
    return out
}

@Composable
private fun SessionsScreen(
    state: ChatState,
    address: String,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onPreview: (String) -> Unit,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
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
        Box(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m)) {
            FormTextField(
                value = state.search,
                placeholder = "Search sessions",
                onValue = onSearch,
                singleLine = true,
                trailing = if (state.search.isNotBlank()) ({ ClearSearchButton { onSearch("") } }) else null,
            )
        }

        val entries = remember(state.sessions) { sessionEntries(sessionRows(state.sessions), System.currentTimeMillis()) }

        when {
            state.sessions.isEmpty() && state.online == false -> OfflineState(address, onRefresh)
            state.sessions.isEmpty() && state.loading -> LoadingRows()
            state.sessions.isEmpty() && state.search.isNotBlank() -> NoMatchesRow(state.search)
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
                items(entries, key = { entry ->
                    when (entry) {
                        is SessionListEntry.Header -> "group:${entry.group.key}"
                        is SessionListEntry.Item -> if (entry.row.child) "subagent:${entry.row.session.id}" else entry.row.session.id
                    }
                }) { entry ->
                    when (entry) {
                        is SessionListEntry.Header -> GroupHeader(entry.group.label)
                        is SessionListEntry.Item -> {
                            val row = entry.row
                            if (row.child) {
                                ChildSessionRow(
                                    title = row.session.title?.takeIf { it.isNotBlank() } ?: "Subagent",
                                    agent = row.session.agent,
                                    stamp = stamp(row.session.time?.updated),
                                )
                            } else {
                                SessionRow(
                                    title = row.session.title?.takeIf { it.isNotBlank() } ?: "Untitled session",
                                    preview = state.previews[row.session.id],
                                    stamp = stamp(row.session.time?.updated),
                                    onClick = { onOpen(row.session.id) },
                                    onPreview = { onPreview(row.session.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.m),
    )
}

@Composable
private fun ClearSearchButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(Size.hitSmall)
            .clip(RoundedCornerShape(Radius.chip))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Close,
            "Clear search",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun NoMatchesRow(query: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "No sessions match \"$query\".",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

/** Subagents have no screen of their own: no tap, no preview. The indent and the word carry that, not a colour. */
@Composable
private fun ChildSessionRow(title: String, agent: String?, stamp: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Size.row)
            .padding(start = Space.xl, end = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull("subagent", agent?.takeIf { it.isNotBlank() }).joinToString(" · "),
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
    onRetry: (() -> Unit)?,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onRunCommand: (String, String) -> Unit,
    onReplyPermission: (String, String) -> Unit,
    onReplyForm: (String, JsonObject) -> Unit,
    onCancelForm: (String) -> Unit,
    onOpenPickers: () -> Unit,
    onLoadCommands: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val list = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

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
            trailing = {
                SessionMenu(
                    onRename = { renaming = true },
                    onFork = { onRunCommand("fork", "") },
                    onCompact = { onRunCommand("compact", "") },
                    onDelete = { confirmingDelete = true },
                )
            },
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
            items(state.messages, key = { it.id }) { message -> Turn(message, queued = message.id == state.queuedId) }
            if (state.busy) {
                item("live") { LiveTurn(state.tools, state.thinking, state.stream) }
            }
        }
        state.error?.let { sentence -> ErrorCard(sentence, onRetry) }
        // Prompts keep the server order; the run waits on each one.
        // shortcut: they stack without their own scroll, which fits the usual one-or-two asks on a phone.
        Column(Modifier.fillMaxWidth()) {
            state.permissions.forEach { request ->
                PermissionCard(request, onReply = { decision -> onReplyPermission(request.id, decision) })
            }
            state.forms.forEach { form ->
                FormCard(
                    form = form,
                    onSubmit = { answer -> onReplyForm(form.id, answer) },
                    onDismiss = { onCancelForm(form.id) },
                )
            }
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

    if (renaming) {
        var title by remember { mutableStateOf(state.title.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radius.panel),
            title = {
                Text("Rename session", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            },
            text = {
                FormTextField(value = title, placeholder = "Session title", onValue = { title = it })
            },
            confirmButton = {
                Choice("Rename", "rename", true, onReply = {
                    val next = title.trim()
                    if (next.isNotEmpty()) onRename(next)
                    renaming = false
                })
            },
            dismissButton = {
                Choice("Cancel", "cancel", false, onReply = { renaming = false })
            },
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radius.panel),
            title = {
                Text("Delete session", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            },
            text = {
                Text(
                    "The transcript is removed from the server. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Choice("Delete", "delete", true, onReply = {
                    confirmingDelete = false
                    onDelete()
                }, danger = true)
            },
            dismissButton = {
                Choice("Keep", "keep", false, onReply = { confirmingDelete = false })
            },
        )
    }
}

/** Rename and fork ride the builtin slash path; delete confirms before it drops the session. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionMenu(onRename: () -> Unit, onFork: () -> Unit, onCompact: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarButton(Icons.Outlined.MoreVert, "Session menu") { open = true }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(Radius.control),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(Size.hairline, MaterialTheme.colorScheme.outline),
        ) {
            SessionMenuItem("Rename") { open = false; onRename() }
            SessionMenuItem("Fork") { open = false; onFork() }
            SessionMenuItem("Compact") { open = false; onCompact() }
            SessionMenuItem("Delete", danger = true) { open = false; onDelete() }
        }
    }
}

@Composable
private fun SessionMenuItem(label: String, danger: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun ErrorCard(sentence: String, onRetry: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(Space.m)
            .clip(RoundedCornerShape(Radius.control))
            .background(MaterialTheme.colorScheme.surface)
            .border(Size.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.control))
            .padding(Space.m),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            LiveDot(MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(Space.m))
            Text(sentence, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        onRetry?.let { retry ->
            Spacer(Modifier.height(Space.s))
            Choice("Retry", "retry", true, onReply = { retry() })
        }
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
private fun Choice(label: String, decision: String, primary: Boolean, onReply: (String) -> Unit, enabled: Boolean = true, danger: Boolean = false) {
    val accent = if (danger) MaterialTheme.colorScheme.error else AccentFill
    val fill = when {
        !enabled -> MaterialTheme.colorScheme.surface
        primary -> accent
        else -> MaterialTheme.colorScheme.background
    }
    val ink = when {
        !enabled -> MaterialTheme.colorScheme.outline
        primary -> if (danger) MaterialTheme.colorScheme.onError else OnAccent
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        Modifier
            .heightIn(min = Size.hitSmall)
            .clip(RoundedCornerShape(Radius.control))
            .background(fill)
            .border(Size.hairline, if ((primary || danger) && enabled) accent else MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.control))
            .clickable(enabled = enabled) { onReply(decision) }
            .padding(horizontal = Space.m, vertical = Space.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ink)
    }
}

/** What one form field holds: the chosen labels and, when the field allows it, a typed answer. */
private data class FormAnswer(val selected: Set<String> = emptySet(), val text: String = "")

@Composable
private fun FormCard(form: FormInfo, onSubmit: (JsonObject) -> Unit, onDismiss: () -> Unit) {
    var answers by remember(form.id) { mutableStateOf<Map<String, FormAnswer>>(emptyMap()) }
    val complete = form.fields.filter { it.required }.all { field ->
        val answer = answers[field.key] ?: FormAnswer()
        answer.text.isNotBlank() || answer.selected.isNotEmpty()
    }
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
            LiveDot(AccentFill)
            Spacer(Modifier.width(Space.s))
            Text(
                form.title?.takeIf { it.isNotBlank() } ?: "Question",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        form.fields.forEach { field ->
            FormFieldControl(field, answers[field.key] ?: FormAnswer()) { next ->
                answers = answers + (field.key to next)
            }
        }
        Spacer(Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Choice("Submit", "submit", true, onReply = { onSubmit(formAnswer(form, answers)) }, enabled = complete)
            Choice("Dismiss", "dismiss", false, onReply = { onDismiss() })
        }
    }
}

@Composable
private fun FormFieldControl(field: FormField, answer: FormAnswer, onChange: (FormAnswer) -> Unit) {
    Spacer(Modifier.height(Space.m))
    field.title?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
    field.description?.takeIf { it.isNotBlank() }?.let {
        Spacer(Modifier.height(Space.xs))
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (field.options.isNotEmpty()) {
        Spacer(Modifier.height(Space.s))
        field.options.forEach { option ->
            val selected = option.label in answer.selected
            FormOptionRow(option, selected) {
                // Single choice replaces the pick; a multiselect toggles and keeps a typed answer.
                onChange(
                    if (field.type == "multiselect") {
                        answer.copy(selected = if (selected) answer.selected - option.label else answer.selected + option.label)
                    } else {
                        FormAnswer(selected = setOf(option.label))
                    },
                )
            }
            Spacer(Modifier.height(Space.xs))
        }
    }
    if (field.options.isEmpty() || field.custom) {
        Spacer(Modifier.height(Space.s))
        FormTextField(
            value = answer.text,
            placeholder = if (field.options.isEmpty()) "Type an answer" else "Or type your own",
            onValue = { text -> onChange(if (field.type == "multiselect") answer.copy(text = text) else FormAnswer(text = text)) },
        )
    }
}

@Composable
private fun FormOptionRow(option: FormOption, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Size.hitSmall)
            .clip(RoundedCornerShape(Radius.chip))
            .background(MaterialTheme.colorScheme.background)
            .border(
                Size.hairline,
                if (selected) AccentFill else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(Radius.chip),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveDot(if (selected) AccentFill else MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(Space.s))
        Column(Modifier.weight(1f)) {
            Text(
                option.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) AccentFill else MaterialTheme.colorScheme.onSurface,
            )
            option.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FormTextField(
    value: String,
    placeholder: String,
    onValue: (String) -> Unit,
    singleLine: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValue,
        placeholder = {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        shape = RoundedCornerShape(Radius.chip),
        maxLines = if (singleLine) 1 else 3,
        singleLine = singleLine,
        trailingIcon = trailing,
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

/** Question answers are labels, not option values, and a multiselect is always an array. */
private fun formAnswer(form: FormInfo, answers: Map<String, FormAnswer>): JsonObject = buildJsonObject {
    form.fields.forEach { field ->
        val answer = answers[field.key] ?: FormAnswer()
        if (field.type == "multiselect") {
            val labels = answer.selected.toList() + answer.text.trim().takeIf { it.isNotEmpty() }.orEmpty()
            put(field.key, JsonArray(labels.map { JsonPrimitive(it) }))
        } else {
            put(field.key, JsonPrimitive(answer.text.trim().takeIf { it.isNotEmpty() } ?: answer.selected.firstOrNull().orEmpty()))
        }
    }
}

@Composable
private fun Turn(message: Message, queued: Boolean = false) {
    if (message.type == "user") {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
            horizontalArrangement = Arrangement.End,
        ) {
            SelectionContainer {
                Column(
                    Modifier
                        .fillMaxWidth(0.88f)
                        .clip(RoundedCornerShape(Radius.control))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = Space.l, vertical = Space.m),
                ) {
                    Text(
                        message.text.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (queued) {
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            "queued",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
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
        MarkdownView(blocks, caretOnLast = streaming)
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
    val ctx = LocalContext.current
    val status = part.state?.status ?: "running"
    val command = (part.state?.input?.get("command") as? JsonPrimitive)?.contentOrNull
    val output = part.state?.content.orEmpty().mapNotNull { it.text }.joinToString("\n")
    val files = patchFiles(part.state)
    val todos = part.name?.lowercase()?.let { name ->
        if (name == "todowrite" || name == "todo" || name == "plan") todoItems(part.state?.input) else null
    }
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s)
            .clip(RoundedCornerShape(Radius.chip))
            .background(MaterialTheme.colorScheme.surface)
            .border(Size.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.chip))
            .padding(Space.m),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.chip))
                .clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveDot(
                when (status) {
                    "completed" -> AccentFill
                    "error" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.width(Space.s))
            Text(
                "${part.name ?: "tool"} · $status",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
            // Its own clickable consumes the tap, so copying never toggles the body.
            Box(
                Modifier
                    .size(Size.hitSmall)
                    .clip(RoundedCornerShape(Radius.chip))
                    .clickable {
                        val text = listOfNotNull(
                            command?.takeIf { it.isNotBlank() },
                            output.takeIf { it.isNotBlank() },
                        ).joinToString("\n\n")
                        if (text.isNotEmpty()) {
                            val clipboard = ctx.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Tool call", text))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "Copy command and output",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (!todos.isNullOrEmpty()) {
            Spacer(Modifier.height(Space.s))
            TodoPanel(todos)
        }
        if (open) {
            if (files.isNotEmpty()) {
                files.forEachIndexed { index, file ->
                    if (index > 0) {
                        Spacer(Modifier.height(Space.s))
                        Hairline()
                        Spacer(Modifier.height(Space.s))
                    }
                    PatchFileBlock(file)
                }
            } else {
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
                        )
                    }
                }
            }
        }
    }
}

/** One file of a `patch` call: the server hands the diff over in the tool metadata. */
private class PatchFile(
    val file: String,
    val patch: String,
    val status: String?,
    val additions: String?,
    val deletions: String?,
)

private fun patchFiles(state: PartState?): List<PatchFile> =
    (state?.metadata?.get("files") as? JsonArray).orEmpty().mapNotNull { element ->
        val row = element as? JsonObject ?: return@mapNotNull null
        val file = row.str("file") ?: return@mapNotNull null
        val patch = row.str("patch") ?: return@mapNotNull null
        PatchFile(file, patch, row.str("status"), row.str("additions"), row.str("deletions"))
    }

/**
 * The `todowrite`/`plan` input as (content, status) pairs, or null when the input is not a todo
 * list. The server has no builtin todo tool in v2.0.32; a list can only arrive from a plugin or an
 * MCP server, which is why the shape is probed rather than typed.
 */
internal fun todoItems(input: JsonObject?): List<Pair<String, String>>? {
    val rows = input?.get("todos") as? JsonArray ?: input?.get("entries") as? JsonArray ?: return null
    val items = rows.mapNotNull { element ->
        val row = element as? JsonObject ?: return@mapNotNull null
        val content = row.str("content")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val status = row.str("status")
        content to if (status == "completed" || status == "in_progress") status else "pending"
    }
    return items.ifEmpty { null }
}

/** Stays visible without expanding the card: the whole point is the list at a glance. */
@Composable
private fun TodoPanel(items: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, (content, status) ->
            if (index > 0) Spacer(Modifier.height(Space.xs))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (status == "completed") Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                    contentDescription = status,
                    tint = when (status) {
                        "completed" -> AccentFill
                        "in_progress" -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(Space.s))
                Text(
                    content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDecoration = if (status == "completed") TextDecoration.LineThrough else null,
                    ),
                    color = if (status == "completed") {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )
                if (status == "in_progress") {
                    Spacer(Modifier.width(Space.s))
                    Text("doing", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun PatchFileBlock(file: PatchFile) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            when (file.status) {
                "added" -> "A"
                "deleted" -> "D"
                "modified" -> "M"
                else -> "?"
            },
            style = MaterialTheme.typography.labelSmall,
            color = when (file.status) {
                "added" -> DiffAdd
                "deleted" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(Space.s))
        Text(
            file.file,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val counts = listOfNotNull(
            file.additions?.let { "+$it" },
            file.deletions?.let { "−$it" },
        ).joinToString(" ")
        if (counts.isNotEmpty()) {
            Spacer(Modifier.width(Space.s))
            Text(counts, style = MaterialTheme.typography.labelSmall)
        }
        Box(
            Modifier
                .size(Size.hitSmall)
                .clip(RoundedCornerShape(Radius.chip))
                .clickable {
                    ctx.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText(file.file, file.patch))
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = "Copy patch",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    Spacer(Modifier.height(Space.xs))
    SelectionContainer {
        Text(
            diffText(file.patch),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
        )
    }
}

@Composable
private fun diffText(patch: String): AnnotatedString {
    // Read the scheme before the builder lambda: it runs outside the composable scope.
    val base = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val removed = MaterialTheme.colorScheme.error
    return buildAnnotatedString {
        patch.lineSequence()
            // The header row above already names the file, so the diff's own ---/+++ line adds noise.
            .filterNot { it.startsWith("--- ") || it.startsWith("+++ ") }
            .forEachIndexed { index, line ->
                if (index > 0) append('\n')
                val color = when {
                    line.startsWith("@@") -> muted
                    line.startsWith("+") -> DiffAdd
                    line.startsWith("-") -> removed
                    else -> base
                }
                withStyle(SpanStyle(color = color)) { append(line) }
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
