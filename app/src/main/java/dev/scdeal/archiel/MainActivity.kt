package dev.scdeal.archiel

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import rikka.shizuku.Shizuku
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Transparent = Color.Transparent

class MainActivity : ComponentActivity() {
    /** A share the activity still has to hand over: the view model only exists inside the composition. */
    private var shared by mutableStateOf<Intent?>(null)

    /** A screen capture the service wrote, handed over the same way a share is. */
    private var captured by mutableStateOf<String?>(null)

    /** The session a run notification asks for. */
    private var opened by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A rotation recreates the activity with the same intent; only a cold start is a new share.
        if (savedInstanceState == null) {
            shared = intent?.takeIf { it.action == Intent.ACTION_SEND }
            captured = intent?.getStringExtra(EXTRA_CAPTURE)
            opened = intent?.getStringExtra(EXTRA_SESSION)
        }
        setContent {
            ArchielTheme {
                App(
                    shared = shared,
                    captured = captured,
                    opened = opened,
                    onHandled = {
                        shared = null
                        captured = null
                        opened = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shared = intent.takeIf { it.action == Intent.ACTION_SEND }
        captured = intent.getStringExtra(EXTRA_CAPTURE)
        opened = intent.getStringExtra(EXTRA_SESSION)
    }

    companion object {
        /** The file the assistant service captured, and the session a notification names. */
        const val EXTRA_CAPTURE = "capture"
        const val EXTRA_SESSION = "session"
    }
}

private enum class Screen { Sessions, Chat, Shell, Terminal, Connection, Pickers }

/** The picker serves the open session or one of the two new-session defaults. */
private enum class PickerMode { Session, DefaultModel, DefaultAgent }

private typealias BarAction = Triple<ImageVector, String, () -> Unit>

/** The files a share carries: every clip item, otherwise the single stream. */
private fun sharedUris(intent: Intent): List<Uri> {
    val clip = intent.clipData
    if (clip != null && clip.itemCount > 0) return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    return listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
}

/** What the shell can do right now: not running, waiting for the permission, or ready. */
private fun shellState(ctx: Context): String = runCatching {
    when {
        !Shizuku.pingBinder() -> "not running"
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "ready"
        else -> "permission needed"
    }
}.getOrDefault("not running")

@Composable
private fun App(shared: Intent?, captured: String?, opened: String?, onHandled: () -> Unit) {
    val ctx = LocalContext.current
    var cfg by remember { mutableStateOf(Cfg.load(ctx)) }
    var profiles by remember { mutableStateOf(Cfg.list(ctx)) }
    var screen by rememberSaveable { mutableStateOf(Screen.Sessions) }
    var pickerMode by rememberSaveable { mutableStateOf(PickerMode.Session) }
    val vm: ChatViewModel = viewModel(key = "${cfg.baseUrl}|${cfg.password}")
    val state by vm.state.collectAsState()
    val canRetry by vm.canRetry.collectAsState()

    // The share arrives outside the composition, so it is handed over once and then cleared.
    LaunchedEffect(shared) {
        val intent = shared ?: return@LaunchedEffect
        val uris = sharedUris(intent)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        if (uris.isNotEmpty() || text != null) {
            // The chat opens once the session that carries the share exists.
            vm.share(uris, text.orEmpty()) { screen = Screen.Chat }
        }
        onHandled()
    }

    // A capture the service took is a file, so it joins the dock the same way a share does.
    LaunchedEffect(captured) {
        val path = captured ?: return@LaunchedEffect
        val uri = runCatching {
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(path))
        }.getOrNull()
        if (uri != null) vm.share(listOf(uri), "") { screen = Screen.Chat }
        onHandled()
    }

    // A run notification names the session the answer is in.
    LaunchedEffect(opened) {
        val id = opened ?: return@LaunchedEffect
        vm.open(id)
        screen = Screen.Chat
        onHandled()
    }

    // The assistant: the run alerts, and the shell that takes the screen shots.
    var alerts by remember { mutableStateOf(Assistant.alerts(ctx)) }
    var shell by remember { mutableStateOf(shellState(ctx)) }
    val notification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // The shell reports back twice: when the permission is answered, and when Shizuku itself starts.
    DisposableEffect(Unit) {
        val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> shell = shellState(ctx) }
        val binder = Shizuku.OnBinderReceivedListener { shell = shellState(ctx) }
        Shizuku.addRequestPermissionResultListener(permission)
        Shizuku.addBinderReceivedListener(binder)
        onDispose {
            Shizuku.removeRequestPermissionResultListener(permission)
            Shizuku.removeBinderReceivedListener(binder)
        }
    }
    // The shot is taken by the shell, so nothing of the screen is held open between the shots.
    val onSnap: () -> Unit = { AssistantService.snap(ctx) }

    // Both entry points fetch the picker lists the first time they are needed.
    val openPicker: (PickerMode) -> Unit = { mode -> vm.loadPickers(); pickerMode = mode; screen = Screen.Pickers }
    // The session picker walks back to the chat; the two defaults walk back to the connection.
    val closePicker: () -> Unit = { screen = if (pickerMode == PickerMode.Session) Screen.Chat else Screen.Connection }

    // The picker walks back to where it came from; the shell walks back to the chat; everything else
    // walks back to the session list.
    BackHandler(enabled = screen != Screen.Sessions) {
        screen = when (screen) {
            Screen.Pickers -> if (pickerMode == PickerMode.Session) Screen.Chat else Screen.Connection
            Screen.Shell -> Screen.Chat
            Screen.Terminal -> Screen.Chat
            else -> Screen.Sessions
        }
    }

    when (screen) {
        Screen.Sessions -> SessionsScreen(
            state = state,
            address = vm.address,
            onOpen = { id, readOnly -> vm.open(id, readOnly); screen = Screen.Chat },
            onNew = { vm.newSession { screen = Screen.Chat } },
            onPreview = vm::preview,
            onRefresh = vm::refresh,
            onSearch = vm::search,
            onSettings = { alerts = Assistant.alerts(ctx); shell = shellState(ctx); screen = Screen.Connection },
        )

        Screen.Chat -> ChatScreen(
            state = state,
            onRetry = if (canRetry) ({ vm.retry() }) else null,
            onBack = { vm.close(); screen = Screen.Sessions },
            onSend = vm::send,
            onDraft = vm::draft,
            onAttach = vm::attach,
            onDetach = vm::detach,
            onSnap = onSnap,
            onMention = vm::mention,
            onUnmention = vm::unmention,
            onAttachFile = vm::attachServerFile,
            onFindFiles = vm::findFiles,
            onStop = vm::stop,
            onRunCommand = vm::runCommand,
            onReplyPermission = vm::replyPermission,
            onReplyForm = vm::replyForm,
            onCancelForm = vm::cancelForm,
            onOpenPickers = { openPicker(PickerMode.Session) },
            onShell = { screen = Screen.Shell },
            onTerminal = { screen = Screen.Terminal },
            onLoadCommands = vm::loadCommands,
            onRename = { vm.runCommand("rename", it) },
            onDelete = { vm.deleteSession { vm.close(); screen = Screen.Sessions } },
            onExport = vm::exportSession,
            onShareDone = vm::clearShare,
        )

        Screen.Shell -> ShellScreen(
            state = state,
            onBack = { screen = Screen.Chat },
            onRun = vm::runShell,
        )

        Screen.Terminal -> TerminalScreen(
            state = state,
            onBack = { screen = Screen.Chat },
            onStart = vm::startPty,
            onInput = vm::ptyInput,
            onStop = vm::stopPty,
        )

        Screen.Pickers -> PickerScreen(
            state = state,
            mode = pickerMode,
            onBack = closePicker,
            onPickModel = { provider, model, variant ->
                when (pickerMode) {
                    PickerMode.Session -> vm.setModel(provider, model, variant)
                    PickerMode.DefaultModel -> vm.setDefaultModel(ModelRef(id = model, providerID = provider, variant = variant).label())
                    PickerMode.DefaultAgent -> Unit
                }
                closePicker()
            },
            onPickAgent = { agent ->
                when (pickerMode) {
                    PickerMode.Session -> vm.setAgent(agent)
                    PickerMode.DefaultAgent -> vm.setDefaultAgent(agent)
                    PickerMode.DefaultModel -> Unit
                }
                closePicker()
            },
            onClearDefault = {
                if (pickerMode == PickerMode.DefaultAgent) vm.setDefaultAgent(null) else vm.setDefaultModel(null)
                closePicker()
            },
            onToggleFavorite = vm::toggleFavorite,
            onToggleHidden = vm::toggleHidden,
        )

        Screen.Connection -> ConnectionScreen(
            cfg = cfg,
            profiles = profiles,
            state = state,
            onBack = { screen = Screen.Sessions },
            onRefresh = vm::refresh,
            onDefaultModel = { openPicker(PickerMode.DefaultModel) },
            onDefaultAgent = { openPicker(PickerMode.DefaultAgent) },
            onActivate = { name -> Cfg.activate(ctx, name); cfg = Cfg.load(ctx) },
            onAdd = { profile -> Cfg.add(ctx, profile); profiles = Cfg.list(ctx); cfg = Cfg.load(ctx) },
            onRemove = { name -> Cfg.remove(ctx, name); profiles = Cfg.list(ctx); cfg = Cfg.load(ctx) },
            alerts = alerts,
            onAlerts = { on ->
                Assistant.setAlerts(ctx, on)
                alerts = on
                if (on) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notification.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    AssistantService.start(ctx)
                } else {
                    AssistantService.stop(ctx)
                }
            },
            shell = shell,
            onShell = {
                when {
                    !Shizuku.pingBinder() -> Shell.open(ctx)
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> Unit
                    else -> Shell.ask()
                }
            },
            // Reloading keeps the active profile's name; the new key recreates the view model.
            onSave = { Cfg.save(ctx, it); cfg = Cfg.load(ctx); AssistantService.reconnect(ctx); screen = Screen.Sessions },
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
private fun Banner(text: String, dot: Color = MaterialTheme.colorScheme.error) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(Space.l),
        verticalAlignment = Alignment.Top,
    ) {
        LiveDot(dot)
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
    onOpen: (String, Boolean) -> Unit,
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
                                    onClick = { onOpen(row.session.id, true) },
                                )
                            } else {
                                SessionRow(
                                    title = row.session.title?.takeIf { it.isNotBlank() } ?: "Untitled session",
                                    preview = state.previews[row.session.id],
                                    stamp = stamp(row.session.time?.updated),
                                    onClick = { onOpen(row.session.id, false) },
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

/** A subagent's transcript opens read-only: the indent and the word carry that, not a colour. */
@Composable
private fun ChildSessionRow(title: String, agent: String?, stamp: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Size.row)
            .padding(start = Space.xl, top = Space.m, end = Space.l, bottom = Space.m),
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
    onDraft: (String) -> Unit,
    onAttach: (Uri) -> Unit,
    onDetach: (String) -> Unit,
    onSnap: () -> Unit,
    onMention: (String) -> Unit,
    onUnmention: (String) -> Unit,
    onAttachFile: (String) -> Unit,
    onFindFiles: (String) -> Unit,
    onStop: () -> Unit,
    onRunCommand: (String, String) -> Unit,
    onReplyPermission: (String, String) -> Unit,
    onReplyForm: (String, JsonObject) -> Unit,
    onCancelForm: (String) -> Unit,
    onOpenPickers: () -> Unit,
    onShell: () -> Unit,
    onTerminal: () -> Unit,
    onLoadCommands: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onShareDone: () -> Unit,
) {
    val list = rememberLazyListState()
    val context = LocalContext.current
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.messages.size, state.stream.length, state.thinking.length) {
        val count = list.layoutInfo.totalItemsCount
        if (count > 0) list.animateScrollToItem(count - 1)
    }

    // The view model only holds the file; clearing it as soon as the chooser opens is what keeps a
    // cancelled sheet from opening twice.
    LaunchedEffect(state.shareUri) {
        val uri = state.shareUri ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share session"))
        onShareDone()
    }

    // Commands are per directory and the server registers plugins late, so ask again when the dock
    // reaches for the list rather than trusting what was fetched at startup.
    LaunchedEffect(state.draft.startsWith("/")) { if (state.draft.startsWith("/")) onLoadCommands() }

    // One "@word" drives both the agent list and the server's own file search; no word clears the files.
    val mentionWord = if (state.draft.startsWith("/")) null else mentionQuery(state.draft)
    LaunchedEffect(mentionWord) { onFindFiles(mentionWord.orEmpty()) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(
            title = state.title?.takeIf { it.isNotBlank() } ?: "Chat",
            onBack = onBack,
            // The busy state wins; when idle the slot names the agent running the session.
            status = when {
                state.busy -> "working"
                !state.agent.isNullOrBlank() -> state.agent
                else -> null
            },
            actions = if (state.readOnly) {
                emptyList()
            } else {
                listOf(
                    BarAction(Icons.Outlined.PlayArrow, "Run a command on the server", onShell),
                    BarAction(Icons.Outlined.Terminal, "Open the terminal", onTerminal),
                    BarAction(Icons.Outlined.Tune, "Switch model or agent", onOpenPickers),
                )
            },
            trailing = {
                // Every item in the menu writes to the session, which a subagent's session does not allow.
                if (!state.readOnly) {
                    SessionMenu(
                        onRename = { renaming = true },
                        onFork = { onRunCommand("fork", "") },
                        onCompact = { onRunCommand("compact", "") },
                        onExport = onExport,
                        onDelete = { confirmingDelete = true },
                    )
                }
            },
        )
        if (state.readOnly) {
            Banner(
                "This is a subagent's session. It runs on its own; you can read it but not write to it.",
                dot = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        UsageStrip(state.usage, state.context)
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
            // A read-only session gets no cards: answering one would write into the subagent's session.
            if (!state.readOnly) {
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
        }
        // The dock is the write surface; a subagent's session has none.
        if (!state.readOnly) {
            Dock(
                input = state.draft,
                onInput = onDraft,
                busy = state.busy,
                commands = state.commands,
                model = state.model,
                attachments = state.attachments,
                mentions = state.mentions,
                agents = state.agents,
                files = state.fileMatches,
                onAttach = onAttach,
                onDetach = onDetach,
                onSnap = onSnap,
                onMention = onMention,
                onUnmention = onUnmention,
                onAttachFile = onAttachFile,
                onSend = {
                    val text = state.draft.trim()
                    if (text.startsWith("/")) {
                        val name = text.removePrefix("/").substringBefore(' ')
                        onRunCommand(name, text.removePrefix("/$name").trim())
                        // The dock owns the field, so a typed command clears it; the menu reuses
                        // runCommand without meaning to touch a draft the user is writing.
                        onDraft("")
                    } else {
                        onSend(state.draft)
                    }
                },
                onStop = onStop,
            )
        }
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

/**
 * Session totals and how much of the model's window the last turn left. The bar is only drawn when
 * the window is known; the numbers and the cost stand alone otherwise.
 */
@Composable
private fun UsageStrip(usage: Usage?, context: ContextUsage?) {
    if (usage == null && context == null) return
    val tokens = usage?.tokens
    val used = context?.used
    val limit = context?.limit
    val fraction = if (used != null && limit != null && limit > 0) (used.toFloat() / limit).coerceIn(0f, 1f) else null
    val nearFull = fraction != null && fraction >= 0.8f
    val headline = buildList {
        if (used != null) add("context " + tokenCount(used) + (limit?.let { " / ${tokenCount(it)}" } ?: ""))
        else if (tokens != null) add("session " + tokenCount(tokens.total()) + " tokens")
        usage?.let { add(costText(it.cost)) }
        if (nearFull) add("nearly full")
    }.joinToString(" · ")
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (fraction != null) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(MaterialTheme.colorScheme.outline),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(Space.xs)
                            .background(if (nearFull) MaterialTheme.colorScheme.error else AccentFill),
                    )
                }
                Spacer(Modifier.width(Space.m))
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(
                headline,
                style = MaterialTheme.typography.labelSmall,
                color = if (nearFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (tokens != null) {
            Spacer(Modifier.height(Space.xs))
            Text(
                "in ${tokenCount(tokens.input)} · out ${tokenCount(tokens.output)} · think ${tokenCount(tokens.reasoning)} · cache ${tokenCount(tokens.cache.read + tokens.cache.write)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
    Hairline()
}

private fun tokenCount(count: Long): String {
    val (value, suffix) = when {
        count >= 1_000_000 -> count / 1_000_000.0 to "M"
        count >= 1_000 -> count / 1_000.0 to "k"
        else -> return count.toString()
    }
    return String.format(Locale.US, "%.1f", value).removeSuffix(".0") + suffix
}

/** Never "$NaN" or "$-": a missing or odd cost reads as zero. */
private fun costText(cost: Double): String {
    val value = if (cost.isFinite() && cost > 0.0) cost else 0.0
    val text = if (value > 0.0 && value < 0.01) String.format(Locale.US, "%.4f", value) else String.format(Locale.US, "%.2f", value)
    return "\$$text"
}

/** Rename and fork ride the builtin slash path; delete confirms before it drops the session. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionMenu(onRename: () -> Unit, onFork: () -> Unit, onCompact: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
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
            SessionMenuItem("Share JSON") { open = false; onExport() }
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
    secret: Boolean = false,
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
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = if (secret) Mono else MaterialTheme.typography.bodyMedium.fontFamily,
        ),
        shape = RoundedCornerShape(Radius.chip),
        maxLines = if (singleLine) 1 else 3,
        singleLine = singleLine,
        trailingIcon = trailing,
        visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
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
        var viewing by remember { mutableStateOf<MessageFile?>(null) }
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
                    message.files.orEmpty().forEach { file ->
                        Spacer(Modifier.height(Space.s))
                        MessageFileView(file) { viewing = file }
                    }
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
        viewing?.let { file -> ImageViewer(file) { viewing = null } }
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

// ------------------------------------------------------------------ attachments

private const val THUMB_MAX_PX = 2048
private const val VIEWER_MAX_PX = 4096
private val ThumbWidth = 200.dp
private val ThumbMaxHeight = 200.dp

/** Key is (max side, base64), so a thumbnail and the viewer's copy coexist without re-decoding. */
private val attachmentCache = object : LruCache<Pair<Int, String>, ImageBitmap>(24 * 1024 * 1024) {
    // Four bytes per pixel is the usual ARGB_8888 size; close enough for a cache budget.
    override fun sizeOf(key: Pair<Int, String>, value: ImageBitmap): Int = value.width * value.height * 4
}

/**
 * A photo arrives as base64 and can be tens of megabytes, so the decode stays off the main thread and
 * `inSampleSize` bounds the bitmap to the longest side the caller asked for.
 */
private fun decodeAttachment(data: String, maxPx: Int): ImageBitmap? = runCatching {
    val bytes = Base64.decode(data, Base64.DEFAULT)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (longest / sample > maxPx) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}.getOrNull()

@Composable
private fun rememberAttachment(data: String, maxPx: Int): ImageBitmap? {
    val key = remember(data, maxPx) { maxPx to data }
    return produceState(initialValue = attachmentCache.get(key), key1 = key) {
        if (value == null) {
            val decoded = withContext(Dispatchers.Default) { decodeAttachment(data, maxPx) }
            if (decoded != null) attachmentCache.put(key, decoded)
            value = decoded
        }
    }.value
}

/** A file under a user message: an image is a tappable thumbnail, anything else a name-and-type card. */
@Composable
private fun MessageFileView(file: MessageFile, onOpen: () -> Unit) {
    val data = file.data
    if (data != null && file.mime?.startsWith("image/") == true) {
        val bitmap = rememberAttachment(data, THUMB_MAX_PX)
        if (bitmap != null) {
            val ratio = bitmap.height.toFloat() / bitmap.width
            Box(
                Modifier
                    .width(ThumbWidth)
                    .height((ThumbWidth * ratio).coerceAtMost(ThumbMaxHeight))
                    .clip(RoundedCornerShape(Radius.chip))
                    .clickable(onClick = onOpen),
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = file.name ?: "Attached image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            return
        }
    }
    // Unknown type, missing data or a failed decode: the name still says which file it was.
    FileCard(file)
}

@Composable
private fun FileCard(file: MessageFile) {
    Row(
        Modifier
            .clip(RoundedCornerShape(Radius.chip))
            .background(MaterialTheme.colorScheme.surface)
            .border(Size.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.chip))
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Space.s))
        Column {
            Text(
                file.name ?: "Attached file",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            file.mime?.takeIf { it.isNotBlank() }?.let { mime ->
                Text(
                    mime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ImageViewer(file: MessageFile, onClose: () -> Unit) {
    val bitmap = rememberAttachment(file.data.orEmpty(), VIEWER_MAX_PX)
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val backdrop = remember { MutableInteractionSource() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            // A full-size layer behind the image turns a tap on the empty area into a close.
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = backdrop, indication = null, onClick = onClose),
            )
            bitmap?.let { image ->
                Image(
                    bitmap = image,
                    contentDescription = file.name ?: "Attached image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        // Sized to the bitmap's aspect so the untouched area stays background and clickable.
                        .aspectRatio(image.width.toFloat() / image.height.coerceAtLeast(1))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                offset = if (scale > 1f) offset + pan else Offset.Zero
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                scale = 1f
                                offset = Offset.Zero
                            })
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(Space.m)
                    .size(Size.hit)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    "Close the image",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
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
    mentions: List<String>,
    agents: List<AgentInfo>,
    files: List<FileEntry>,
    model: String?,
    attachments: List<Attachment>,
    onAttach: (Uri) -> Unit,
    onDetach: (String) -> Unit,
    onSnap: () -> Unit,
    onMention: (String) -> Unit,
    onUnmention: (String) -> Unit,
    onAttachFile: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val active = busy || input.isNotBlank()
    // Both pickers hand a content Uri back; the view model reads it through the resolver.
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onAttach) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onAttach) }
    val matching = if (input.startsWith("/")) {
        val query = input.removePrefix("/").substringBefore(' ').lowercase()
        commands.filter { it.name.lowercase().startsWith(query) }.take(6)
    } else {
        emptyList()
    }
    // A trailing "@word" is a mention; only the subagents the server did not hide can be delegated to.
    val mention = if (input.startsWith("/")) null else mentionQuery(input)?.lowercase()
    val matchingAgents = mention?.let { query ->
        agents
            .filter { it.hidden != true && it.mode == "subagent" }
            .filter { it.name.lowercase().startsWith(query) || it.id.lowercase().startsWith(query) }
            .take(6)
    }.orEmpty()
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
        if (mention != null && (matchingAgents.isNotEmpty() || files.isNotEmpty())) {
            // Agents and files can fill more than the screen above the field, so the block scrolls in a bound.
            Column(
                Modifier
                    .padding(horizontal = Space.s, vertical = Space.xs)
                    .heightIn(max = Size.row * 6)
                    .verticalScroll(rememberScrollState()),
            ) {
                matchingAgents.forEach { agent ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.chip))
                            .clickable { onMention(agent.name) }
                            .padding(horizontal = Space.m, vertical = Space.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "@${agent.name}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                            color = AccentFill,
                        )
                        Spacer(Modifier.width(Space.m))
                        Text(
                            agent.description.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // The server's files for the same word; a path is the whole row, so it needs no label.
                files.forEach { file ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.chip))
                            .clickable { onAttachFile(file.path) }
                            .padding(horizontal = Space.m, vertical = Space.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            file.path,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (attachments.isNotEmpty() || mentions.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Space.s, vertical = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                attachments.forEach { attachment ->
                    AttachmentChip(attachment) { onDetach(attachment.name) }
                }
                mentions.forEach { name ->
                    MentionChip(name) { onUnmention(name) }
                }
            }
        }
        Row(Modifier.padding(Space.s), verticalAlignment = Alignment.Bottom) {
            AttachMenu(
                onImage = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onFile = { filePicker.launch(arrayOf("*/*")) },
                onScreen = onSnap,
            )
            Spacer(Modifier.width(Space.s))
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

/** A pending attachment: its name and size, with the only action that matters before sending. */
@Composable
private fun AttachmentChip(attachment: Attachment, onRemove: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = Size.hitSmall)
            .clip(RoundedCornerShape(Radius.chip))
            .background(MaterialTheme.colorScheme.surface)
            .border(Size.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.chip))
            .padding(start = Space.m, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // A server file is attached by path, so its size is unknown rather than zero.
            if (attachment.bytes > 0) "${attachment.name} · ${fileSize(attachment.bytes)}" else attachment.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Box(
            Modifier
                .size(Size.hitSmall)
                .clip(RoundedCornerShape(Radius.chip))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, "Remove the attachment", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** A mentioned subagent: the prompt names it, so the chip says who and carries the only action before sending. */
@Composable
private fun MentionChip(name: String, onRemove: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = Size.hitSmall)
            .clip(RoundedCornerShape(Radius.chip))
            .background(MaterialTheme.colorScheme.surface)
            .border(Size.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.chip))
            .padding(start = Space.m, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "@$name",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = Mono),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Box(
            Modifier
                .size(Size.hitSmall)
                .clip(RoundedCornerShape(Radius.chip))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, "Remove the mention", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** The paperclip sits left of the field; the menu picks between the photo picker and any document. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachMenu(onImage: () -> Unit, onFile: () -> Unit, onScreen: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarButton(Icons.Outlined.AttachFile, "Attach a file") { open = true }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(Radius.control),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(Size.hairline, MaterialTheme.colorScheme.outline),
        ) {
            SessionMenuItem("Image") { open = false; onImage() }
            SessionMenuItem("File") { open = false; onFile() }
            SessionMenuItem("Screen") { open = false; onScreen() }
        }
    }
}

/** A byte count as "240 kB" or "1.4 MB"; the server's 20 MB ceiling is the only size that matters here. */
private fun fileSize(bytes: Int): String = when {
    bytes >= 1_000_000 -> String.format(Locale.US, "%.1f", bytes / 1_000_000.0).removeSuffix(".0") + " MB"
    bytes >= 1_000 -> "${bytes / 1_000} kB"
    else -> "$bytes B"
}


// ------------------------------------------------------------------ shell

/** A quick runner, not a terminal: the command goes to the server and its paged output comes back. */
@Composable
private fun ShellScreen(state: ChatState, onBack: () -> Unit, onRun: (String) -> Unit) {
    // The field is local, so it starts empty every time the screen opens; the run itself lives in the view model.
    var command by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(title = "Shell", onBack = onBack)
        state.error?.let { sentence -> ErrorCard(sentence, null) }
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m)) {
            TextField(
                value = command,
                onValueChange = { command = it },
                placeholder = {
                    Text(
                        "Command to run on the server",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                shape = RoundedCornerShape(Radius.panel),
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
            state.location?.takeIf { it.isNotBlank() }?.let { directory ->
                Spacer(Modifier.height(Space.xs))
                Text(
                    "Runs in $directory",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(Space.s))
            PrimaryButton(
                label = if (state.shellBusy) "Running" else "Run",
                onClick = { onRun(command) },
                modifier = Modifier.fillMaxWidth(),
                enabled = command.isNotBlank() && !state.shellBusy,
            )
        }
        Hairline()
        state.shell?.let { run ->
            ShellCommandHead(run, state.shellBusy)
            Hairline()
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Space.l),
        ) {
            val run = state.shell
            if (run == null) {
                ShellNote("No command has been run in this session.")
            } else {
                when {
                    run.output.isBlank() && state.shellBusy -> ShellNote("The command is running.")
                    run.output.isBlank() -> ShellNote("The command produced no output.")
                    else -> SelectionContainer {
                        Text(
                            run.output,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** The command and its outcome: the exit code is the only thing worth reading back. */
@Composable
private fun ShellCommandHead(run: ShellRun, busy: Boolean) {
    val (outcome, tone) = when {
        busy || run.status == "running" -> "running" to MaterialTheme.colorScheme.onSurfaceVariant
        run.exit == 0 -> "exit 0" to MaterialTheme.colorScheme.onSurfaceVariant
        run.exit != null -> "failed · exit ${run.exit}" to MaterialTheme.colorScheme.error
        else -> (run.status ?: "done") to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                "\$ ${run.command}",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.m))
        Text(outcome, style = MaterialTheme.typography.labelSmall, color = tone)
    }
}

@Composable
private fun ShellNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ------------------------------------------------------------------ terminal

/** A program on the server with a real stdin: the command goes over the PTY socket, the output streams back. */
@Composable
private fun TerminalScreen(
    state: ChatState,
    onBack: () -> Unit,
    onStart: (String) -> Unit,
    onInput: (String) -> Unit,
    onStop: () -> Unit,
) {
    // Both fields are local, so they start empty every time the screen opens; the run lives in the view model.
    var command by remember { mutableStateOf("") }
    var line by remember { mutableStateOf("") }
    val run = state.pty
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(title = "Terminal", onBack = onBack)
        state.error?.let { sentence -> ErrorCard(sentence, null) }
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m)) {
            TextField(
                value = command,
                onValueChange = { command = it },
                placeholder = {
                    Text(
                        "Program to run on the server",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                shape = RoundedCornerShape(Radius.panel),
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
            state.location?.takeIf { it.isNotBlank() }?.let { directory ->
                Spacer(Modifier.height(Space.xs))
                Text(
                    "Runs in $directory",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(Space.s))
            // The one button carries both states: Start while nothing runs, Stop while it does.
            if (state.ptyBusy) {
                PrimaryButton("Stop", onStop, Modifier.fillMaxWidth())
            } else {
                PrimaryButton("Start", { onStart(command) }, Modifier.fillMaxWidth(), enabled = command.isNotBlank())
            }
        }
        Hairline()
        run?.let { current ->
            TerminalHead(current)
            Hairline()
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Space.l),
        ) {
            when {
                run == null && state.ptyBusy -> ShellNote("Starting the program.")
                run == null -> ShellNote("No program is running.")
                run.output.isEmpty() && state.ptyBusy -> ShellNote("The program is running.")
                run.output.isEmpty() -> ShellNote("The program produced no output.")
                else -> SelectionContainer {
                    Text(
                        run.output,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        if (run != null) {
            Hairline()
            Row(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = line,
                    onValueChange = { line = it },
                    placeholder = {
                        Text(
                            "Send a line to the program",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono, color = MaterialTheme.colorScheme.onSurface),
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.panel),
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
                PrimaryButton(
                    "Send",
                    onClick = { onInput(line); line = "" },
                    enabled = state.ptyBusy && line.isNotBlank(),
                )
            }
        }
    }
}

/** The program and its outcome: the exit code is the only thing worth reading back. */
@Composable
private fun TerminalHead(run: PtyRun) {
    val (outcome, tone) = when {
        run.status == "running" -> "running" to MaterialTheme.colorScheme.onSurfaceVariant
        run.exitCode == 0 -> "exit 0" to MaterialTheme.colorScheme.onSurfaceVariant
        run.exitCode != null -> "failed · exit ${run.exitCode}" to MaterialTheme.colorScheme.error
        else -> (run.status ?: "exited") to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                "\$ ${run.command}",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.m))
        Text(outcome, style = MaterialTheme.typography.labelSmall, color = tone)
    }
}

// ------------------------------------------------------------------ pickers

@Composable
private fun PickerScreen(
    state: ChatState,
    mode: PickerMode,
    onBack: () -> Unit,
    onPickModel: (String, String, String?) -> Unit,
    onPickAgent: (String) -> Unit,
    onClearDefault: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleHidden: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Session mode edits the open session; the two default modes edit what a new session starts with.
    val showModels = mode != PickerMode.DefaultAgent
    val showAgents = mode != PickerMode.DefaultModel
    val selectedModel = if (mode == PickerMode.DefaultModel) state.defaultModel else state.model
    val selectedAgent = if (mode == PickerMode.DefaultAgent) state.defaultAgent else state.agent
    val all = remember(state.models, query) {
        val q = query.trim().lowercase()
        state.models
            .filter { q.isEmpty() || it.modelID.lowercase().contains(q) || it.providerID.lowercase().contains(q) }
            .sortedBy { it.providerID }
    }
    // A hidden model leaves the live sections and waits in HIDDEN, so it can always be brought back.
    val favorites = all.filter { modelKey(it) in state.favorites }
    val hidden = all.filter { modelKey(it) in state.hidden }
    val rest = all.filterNot { modelKey(it) in state.favorites || modelKey(it) in state.hidden }.take(80)
    // A hidden agent is not meant to be listed; primaries read first, subagents in their own group.
    val agents = state.agents.filter { it.hidden != true }
    val primaries = agents.filter { it.mode != "subagent" }
    val subagents = agents.filter { it.mode == "subagent" }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(
            title = when (mode) {
                PickerMode.Session -> "Model and agent"
                PickerMode.DefaultModel -> "Default model"
                PickerMode.DefaultAgent -> "Default agent"
            },
            onBack = onBack,
        )
        if (mode == PickerMode.Session) {
            state.usage?.let { usage ->
                Text(
                    "This session · ${tokenCount(usage.tokens.total())} tokens · ${costText(usage.cost)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
                )
            }
        }
        if (showModels) {
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
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (mode != PickerMode.Session) {
                item("server-default") {
                    PickerRow(
                        title = "Server default",
                        detail = "Let the server pick for a new session",
                        selected = (if (mode == PickerMode.DefaultAgent) selectedAgent else selectedModel) == null,
                        mono = false,
                        onClick = onClearDefault,
                    )
                }
            }
            if (showAgents && query.isEmpty() && primaries.isNotEmpty()) {
                item("agents") { SectionLabel("AGENT") }
                items(primaries, key = { "agent-${it.id}" }) { agent ->
                    AgentRow(agent = agent, selected = selectedAgent == agent.id, onClick = { onPickAgent(agent.id) })
                }
            }
            if (showAgents && query.isEmpty() && subagents.isNotEmpty()) {
                item("subagents") { SectionLabel("SUBAGENT") }
                items(subagents, key = { "subagent-${it.id}" }) { agent ->
                    AgentRow(agent = agent, selected = selectedAgent == agent.id, onClick = { onPickAgent(agent.id) })
                }
            }
            if (showModels && favorites.isNotEmpty()) {
                if (query.isEmpty()) item("favorites") { SectionLabel("FAVORITES") }
                items(favorites, key = { "fav-${modelKey(it)}" }) { model ->
                    ModelRow(
                        model = model,
                        selectedModel = selectedModel,
                        favorite = true,
                        hidden = false,
                        enabled = true,
                        onPick = { variant -> onPickModel(model.providerID, model.modelID, variant) },
                        onToggleFavorite = { onToggleFavorite(modelKey(model)) },
                        onToggleHidden = { onToggleHidden(modelKey(model)) },
                    )
                }
            }
            if (showModels && rest.isNotEmpty()) {
                if (query.isEmpty()) item("models") { SectionLabel("MODEL") }
                items(rest, key = { "model-${modelKey(it)}" }) { model ->
                    ModelRow(
                        model = model,
                        selectedModel = selectedModel,
                        favorite = false,
                        hidden = false,
                        enabled = true,
                        onPick = { variant -> onPickModel(model.providerID, model.modelID, variant) },
                        onToggleFavorite = { onToggleFavorite(modelKey(model)) },
                        onToggleHidden = { onToggleHidden(modelKey(model)) },
                    )
                }
            }
            if (showModels && hidden.isNotEmpty()) {
                if (query.isEmpty()) item("hidden") { SectionLabel("HIDDEN") }
                items(hidden, key = { "hidden-${modelKey(it)}" }) { model ->
                    ModelRow(
                        model = model,
                        selectedModel = selectedModel,
                        favorite = modelKey(model) in state.favorites,
                        hidden = true,
                        enabled = false,
                        onPick = { variant -> onPickModel(model.providerID, model.modelID, variant) },
                        onToggleFavorite = { onToggleFavorite(modelKey(model)) },
                        onToggleHidden = { onToggleHidden(modelKey(model)) },
                    )
                }
            }
            if (showModels && all.isEmpty()) {
                item("none") { Centered("No match", "Nothing on this server matches that search.") }
            }
            if (showAgents && !showModels && agents.isEmpty()) {
                item("none") { Centered("No agents", "This server lists no agents to choose from.") }
            }
        }
    }
}

/** Favourites and hidden sets key a model by "provider/model", the shape its label takes. */
private fun modelKey(model: ModelInfo): String = "${model.providerID}/${model.modelID}"

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = Space.l, vertical = Space.s),
    )
}

@Composable
private fun ModelRow(
    model: ModelInfo,
    selectedModel: String?,
    favorite: Boolean,
    hidden: Boolean,
    enabled: Boolean,
    onPick: (String?) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleHidden: () -> Unit,
) {
    val plain = ModelRef(id = model.modelID, providerID = model.providerID).label()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = { onPick(null) })
            .heightIn(min = Size.row)
            .padding(start = Space.l, top = Space.s, end = Space.xs, bottom = Space.s)
            // Dimmed rather than dropped: a hidden model still has to be reachable from here.
            .alpha(if (hidden) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                model.modelID,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                color = if (selectedModel == plain) AccentFill else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                model.providerID,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selectedModel == plain) {
            Spacer(Modifier.width(Space.m))
            LiveDot(AccentFill)
        }
        PickerIcon(
            icon = if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
            label = if (favorite) "Remove from favourites" else "Add to favourites",
            onClick = onToggleFavorite,
        )
        PickerIcon(
            icon = Icons.Outlined.VisibilityOff,
            label = if (hidden) "Unhide this model" else "Hide this model",
            onClick = onToggleHidden,
        )
    }
    // A hidden model shows only the row that brings it back, so its variants do not stay pickable.
    if (enabled) {
        model.variants.orEmpty().forEach { variant ->
            VariantRow(
                id = variant.id,
                selected = selectedModel == ModelRef(id = model.modelID, providerID = model.providerID, variant = variant.id).label(),
                onPick = { onPick(variant.id) },
            )
        }
    }
    Hairline()
}

/** A variant sits indented under its model and picks that model with the variant set. */
@Composable
private fun VariantRow(id: String, selected: Boolean, onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .heightIn(min = Size.hit)
            .padding(start = Space.xxl, top = Space.s, end = Space.l, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            id,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
            color = if (selected) AccentFill else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Spacer(Modifier.width(Space.m))
            LiveDot(AccentFill)
        }
    }
    Hairline()
}

@Composable
private fun PickerIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
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

/** The server's own sentence reads better than the mode; the mode is the fallback when it has none. */
@Composable
private fun AgentRow(agent: AgentInfo, selected: Boolean, onClick: () -> Unit) {
    PickerRow(
        title = agent.name,
        detail = agent.description?.takeIf { it.isNotBlank() } ?: agent.mode ?: "primary",
        selected = selected,
        mono = false,
        onClick = onClick,
    )
}

// ------------------------------------------------------------------ connection

@Composable
private fun ConnectionScreen(
    cfg: ServerConfig,
    profiles: List<ServerConfig>,
    state: ChatState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDefaultModel: () -> Unit,
    onDefaultAgent: () -> Unit,
    onActivate: (String) -> Unit,
    onAdd: (ServerConfig) -> Unit,
    onRemove: (String) -> Unit,
    onSave: (ServerConfig) -> Unit,
    alerts: Boolean,
    onAlerts: (Boolean) -> Unit,
    shell: String,
    onShell: () -> Unit,
) {
    // A profile switch changes cfg underneath, so the fields reload from the new active one.
    var address by rememberSaveable(cfg) { mutableStateOf(cfg.baseUrl) }
    var password by rememberSaveable(cfg) { mutableStateOf(cfg.password) }
    var adding by rememberSaveable { mutableStateOf(false) }

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
            SettingRow("Default model", state.defaultModel ?: "not set", onDefaultModel)
            SettingRow("Default agent", state.defaultAgent ?: "not set", onDefaultAgent)
            Text(
                "ASSISTANT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = Space.l, top = Space.m),
            )
            Spacer(Modifier.height(Space.xs))
            SettingRow("Run alerts", if (alerts) "on" else "off") { onAlerts(!alerts) }
            SettingRow("Shizuku", shell, onShell)
            Text(
                "The run alerts keep a foreground service open, so the phone says when an agent has " +
                    "finished. The screen shots are taken by Shizuku, the shell of the phone itself: " +
                    "nothing of the screen is held open between the shots.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Space.l),
            )
            Spacer(Modifier.height(Space.m))
            Text(
                "SERVERS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = Space.l, top = Space.m),
            )
            Spacer(Modifier.height(Space.xs))
            profiles.forEach { profile ->
                val active = profile.name == cfg.name
                ProfileRow(
                    profile = profile,
                    active = active,
                    canRemove = profiles.size > 1,
                    onActivate = { if (!active) onActivate(profile.name) },
                    onRemove = { onRemove(profile.name) },
                )
            }
            AddServerRow { adding = true }
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

    if (adding) {
        var name by remember { mutableStateOf("") }
        var newAddress by remember { mutableStateOf(Cfg.DEFAULT_URL) }
        var newPassword by remember { mutableStateOf("") }
        val clean = newAddress.trim().trimEnd('/')
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radius.panel),
            title = {
                Text("Add a server", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            },
            text = {
                Column {
                    FormTextField(value = name, placeholder = "Name (optional)", onValue = { name = it }, singleLine = true)
                    Spacer(Modifier.height(Space.m))
                    FormTextField(
                        value = newAddress,
                        placeholder = "http://127.0.0.1:4096",
                        onValue = { newAddress = it },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(Space.m))
                    FormTextField(
                        value = newPassword,
                        placeholder = "Server password",
                        onValue = { newPassword = it },
                        singleLine = true,
                        secret = true,
                    )
                }
            },
            confirmButton = {
                Choice("Add", "add", true, onReply = {
                    if (clean.isNotEmpty()) onAdd(ServerConfig(clean, newPassword, name.trim().ifBlank { clean }))
                    adding = false
                }, enabled = clean.isNotEmpty())
            },
            dismissButton = {
                Choice("Cancel", "cancel", false, onReply = { adding = false })
            },
        )
    }
}

/** Name, address and the dot of the active profile; the close button removes the row. */
@Composable
private fun ProfileRow(
    profile: ServerConfig,
    active: Boolean,
    canRemove: Boolean,
    onActivate: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onActivate)
            .heightIn(min = Size.row)
            .padding(start = Space.l, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = Space.m)) {
            Text(
                profile.name.ifBlank { profile.baseUrl },
                style = MaterialTheme.typography.titleMedium,
                color = if (active) AccentFill else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (profile.name.isNotBlank()) {
                Text(
                    profile.baseUrl,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (active) {
            LiveDot(AccentFill)
            Spacer(Modifier.width(Space.s))
        }
        Box(
            Modifier
                .size(Size.hitSmall)
                .clip(RoundedCornerShape(Radius.chip))
                .clickable(enabled = canRemove, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Close,
                "Remove this server",
                tint = if (canRemove) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    Hairline()
}

@Composable
private fun AddServerRow(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Size.hit)
            .padding(horizontal = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, "Add a server", tint = AccentFill, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.m))
        Text("Add a server", style = MaterialTheme.typography.titleMedium, color = AccentFill)
    }
    Hairline()
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
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Size.hit)
            .padding(horizontal = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Hairline()
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
private fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(Radius.control))
            .background(if (enabled) AccentFill else MaterialTheme.colorScheme.surface)
            .then(if (enabled) Modifier else Modifier.border(Size.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.control)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (enabled) OnAccent else MaterialTheme.colorScheme.outline)
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
