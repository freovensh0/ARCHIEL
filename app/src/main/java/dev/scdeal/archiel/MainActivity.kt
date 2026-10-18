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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
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

private enum class Screen { Sessions, Chat, Connection }

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
        Row(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth()
                .height(Size.bar)
                .padding(start = if (onBack == null) Space.l else Space.xs, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) BarButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(start = if (onBack == null) Space.xs else Space.xs),
            )
            if (status != null) {
                Spacer(Modifier.width(Space.s))
                LiveDot()
                Spacer(Modifier.width(Space.xs))
                Text(status, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.weight(1f))
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
private fun ChatScreen(state: ChatState, onBack: () -> Unit, onSend: (String) -> Unit, onStop: () -> Unit) {
    val list = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.messages.size, state.stream.length) {
        val count = list.layoutInfo.totalItemsCount
        if (count > 0) list.animateScrollToItem(count - 1)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(
            title = state.title?.takeIf { it.isNotBlank() } ?: "Chat",
            onBack = onBack,
            status = if (state.busy) "working" else null,
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
            if (state.stream.isNotEmpty()) {
                item("stream") { AssistantTurn(state.stream, streaming = true) }
            }
        }
        Dock(
            input = input,
            onInput = { input = it },
            busy = state.busy,
            onSend = { onSend(input); input = "" },
            onStop = onStop,
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
                else -> Unit // reasoning stays hidden in v1
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
    SelectionContainer {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (streaming) {
                Spacer(Modifier.width(2.dp))
                Caret()
            }
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
private fun Dock(input: String, onInput: (String) -> Unit, busy: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    val active = busy || input.isNotBlank()
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Hairline()
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
            Text("opencode", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
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
