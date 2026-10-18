package dev.scdeal.archiel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TR = Locale("tr")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ArchielTheme { App() } }
    }
}

private enum class Screen { Sessions, Chat, Settings }

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
            onOpen = { vm.open(it); screen = Screen.Chat },
            onNew = { vm.newSession { screen = Screen.Chat } },
            onRefresh = vm::refresh,
            onSettings = { screen = Screen.Settings },
        )

        Screen.Chat -> ChatScreen(
            state = state,
            onBack = { vm.close(); screen = Screen.Sessions },
            onSend = vm::send,
            onStop = vm::stop,
        )

        Screen.Settings -> SettingsScreen(
            cfg = cfg,
            onBack = { screen = Screen.Sessions },
            onSave = { Cfg.save(ctx, it); cfg = it; screen = Screen.Sessions },
        )
    }
}

// ---------------------------------------------------------------- oturumlar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionsScreen(
    state: ChatState,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("ARCHIEL", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, "Yenile", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, "Bağlantı ayarları", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNew,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
            ) { Icon(Icons.Outlined.Add, "Yeni oturum") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            NoticeBar(state.error)
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CenterHint("Oturumlar alınıyor…")
                }
                state.sessions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CenterHint("Henüz oturum yok.\nSağ alttaki + ile ilk sohbeti başlat.")
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.sessions, key = { it.id }) { session ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(session.id) }
                                .padding(horizontal = Space.l, vertical = Space.m),
                        ) {
                            Text(
                                session.title?.takeIf { it.isNotBlank() } ?: "Adsız oturum",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stamp(session.time?.updated),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- sohbet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(state: ChatState, onBack: () -> Unit, onSend: (String) -> Unit, onStop: () -> Unit) {
    val list = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.messages.size, state.stream.length) {
        val count = list.layoutInfo.totalItemsCount
        if (count > 0) list.animateScrollToItem(count - 1)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Geri", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                title = {
                    Text(
                        state.title?.takeIf { it.isNotBlank() } ?: "Sohbet",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            Composer(
                input = input,
                onInput = { input = it },
                busy = state.busy,
                onSend = { onSend(input); input = "" },
                onStop = onStop,
            )
        },
    ) { pad ->
        LazyColumn(
            state = list,
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = Space.l, end = Space.l, top = Space.m, bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            if (state.messages.isEmpty() && state.stream.isEmpty()) {
                item("empty") {
                    CenterHint("Sohbete başla. Mesajların sunucudaki opencode oturumunda işlenir.")
                }
            }
            items(state.messages, key = { it.id }) { message -> MessageBlock(message) }
            if (state.stream.isNotEmpty()) {
                item("stream") { Answer(state.stream, streaming = true) }
            }
        }
    }
}

@Composable
private fun Composer(
    input: String,
    onInput: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val sendable = !busy && input.isNotBlank()
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(Space.s),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            placeholder = {
                Text("Mesajını yaz…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            shape = RoundedCornerShape(16.dp),
            maxLines = 5,
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.background,
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Spacer(Modifier.width(Space.s))
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (busy || sendable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = busy || sendable) { if (busy) onStop() else onSend() },
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                Box(Modifier.size(13.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onPrimary))
            } else {
                Icon(
                    Icons.Outlined.Send,
                    "Gönder",
                    modifier = Modifier.size(20.dp),
                    tint = if (sendable) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MessageBlock(message: Message) {
    if (message.type == "user") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            SelectionContainer {
                Text(
                    message.text.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = Space.m, vertical = Space.s),
                )
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        val parts = message.content.orEmpty()
        if (parts.isEmpty() && message.outcome == "failed") {
            Text(
                "Yanıt üretilemedi.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
        parts.forEach { part ->
            when (part.type) {
                "text" -> Answer(part.text.orEmpty())
                "tool" -> ToolLine(part)
                else -> Unit // reasoning: sürüm 1'de gizli
            }
        }
    }
}

@Composable
private fun Answer(text: String, streaming: Boolean = false) {
    val body = if (streaming) {
        buildAnnotatedString {
            append(text)
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("▌") }
        }
    } else {
        buildAnnotatedString { append(text) }
    }
    SelectionContainer {
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ToolLine(part: Part) {
    val status = part.state?.status ?: "running"
    val command = (part.state?.input?.get("command") as? JsonPrimitive)?.contentOrNull
    val dot = when (status) {
        "completed" -> MaterialTheme.colorScheme.primary
        "error" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(Space.s),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 5.dp).size(6.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(Space.s))
        Column {
            Text(
                part.name ?: "tool",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = Mono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (command != null) {
                Text(
                    "$ $command",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- ayarlar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(cfg: ServerConfig, onBack: () -> Unit, onSave: (ServerConfig) -> Unit) {
    var url by rememberSaveable { mutableStateOf(cfg.baseUrl) }
    var password by rememberSaveable { mutableStateOf(cfg.password) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Geri", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                title = { Text("Bağlantı", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(Space.l)) {
            Field("Sunucu adresi", url, { url = it }, "http://10.0.2.2:4096")
            Spacer(Modifier.height(Space.m))
            Field("Parola", password, { password = it }, "opencode serve yazdırır", secret = true)
            Spacer(Modifier.height(Space.l))
            Text(
                "Parolayı sunucuyu başlattığın terminalde görürsün: opencode serve satırının altındaki " +
                    "\"server password\". Termux'ta çalıştırıyorsan adres 127.0.0.1:4096 olur.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.xl))
            Button(
                onClick = { onSave(ServerConfig(url.trim(), password)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) { Text("Kaydet", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    secret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        placeholder = { Text(hint, style = MaterialTheme.typography.bodyMedium) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = if (secret) Mono else MaterialTheme.typography.bodyLarge.fontFamily,
        ),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

// ---------------------------------------------------------------- parçalar

@Composable
private fun NoticeBar(text: String?) {
    if (text == null) return
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
        Spacer(Modifier.width(Space.m))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun CenterHint(text: String) {
    // LazyColumn içinde fillMaxSize yerine sabit min yükseklik: liste sonsuz yükseklik verir.
    Box(Modifier.fillMaxWidth().heightIn(min = 220.dp).padding(Space.xl), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun stamp(ms: Long?): String =
    if (ms == null || ms <= 0L) "zaman bilgisi yok" else SimpleDateFormat("d MMM yyyy · HH:mm", TR).format(Date(ms))
