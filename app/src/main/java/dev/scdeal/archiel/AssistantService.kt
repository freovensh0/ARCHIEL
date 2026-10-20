package dev.scdeal.archiel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlin.math.min

/**
 * The assistant half of the app: a foreground service that keeps the event stream open, so the
 * phone can say that a run has finished, and that snaps the screen on request.
 *
 * The screen is never held: the shot is taken by the shell through Shizuku and the file is handed
 * to the app, so nothing of the screen is recorded between the shots.
 */
class AssistantService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var client: OpenCodeClient? = null
    private var stream: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        channels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stop()
                return START_NOT_STICKY
            }
            ACTION_RECONNECT -> {
                connect()
                return START_NOT_STICKY
            }
        }
        foreground()
        if (Assistant.alerts(this)) connect()
        if (intent?.action == ACTION_SNAP) snap()
        // The service is not sticky: it exists while the user wants it, and not one moment longer.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ the snapshot

    /**
     * One shot of the screen, taken by the shell. The wait is for the notification shade: it
     * closes after the action is tapped, and a picture of the shade is not what the user asked for.
     */
    private fun snap() {
        if (!Shell.ready()) {
            failed("Shizuku is not ready. Open ARCHIEL and allow the shell.")
            return
        }
        scope.launch {
            delay(400)
            val file = withContext(Dispatchers.IO) { Shell.snap(this@AssistantService) }
            if (file == null) {
                failed("The screen could not be captured.")
                return@launch
            }
            handOver(file)
            // A service that was only started for the shot has nothing left to do.
            if (!Assistant.alerts(this@AssistantService)) stop()
        }
    }

    /** The shot is a file, so the app picks it up the same way it takes a share from another app. */
    private fun handOver(file: File) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_CAPTURE, file.absolutePath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    // ------------------------------------------------------------------ the event stream

    private fun connect() {
        stream?.cancel()
        val cfg = Cfg.load(this)
        val client = OpenCodeClient(cfg.baseUrl, cfg.password)
        this.client = client
        stream = scope.launch {
            client.events()
                .retryWhen { _, attempt ->
                    // The phone sleeps and sockets die; the same backoff the view model uses.
                    delay(min(15_000L, 1_000L shl min(attempt.toInt(), 4)))
                    true
                }
                .collect { event -> onEvent(event) }
        }
    }

    private fun onEvent(event: JsonObject) {
        val type = event.str("type") ?: return
        val data = event["data"] as? JsonObject ?: return
        val sessionID = data.str("sessionID") ?: return
        if (!isWatchedRun(type, sessionID, Assistant.watched(this))) return
        scope.launch { announce(sessionID) }
    }

    private suspend fun announce(sessionID: String) {
        val client = client ?: return
        val message = runCatching { client.lastMessage(sessionID) }.getOrNull() ?: return
        val text = message.preview().ifBlank { "The run has finished." }.take(200)
        val title = Assistant.watchedTitle(this) ?: "Assistant"
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_SESSION, sessionID)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val open = PendingIntent.getActivity(this, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager().notify(
            RUN_ID,
            NotificationCompat.Builder(this, CHANNEL_RUNS)
                .setSmallIcon(R.drawable.ic_stat_archiel)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build(),
        )
    }

    // ------------------------------------------------------------------ the notifications

    private fun foreground() {
        val notification = assistantNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // The event stream has to stay open for hours, so it is a special use service: a data
            // sync service would be capped at six hours a day on Android 15.
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun assistantNotification(): Notification {
        val snap = PendingIntent.getService(
            this,
            1,
            Intent(this, AssistantService::class.java).setAction(ACTION_SNAP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val ask = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ASSISTANT)
            .setSmallIcon(R.drawable.ic_stat_archiel)
            .setContentTitle("ARCHIEL assistant")
            .setContentText("Watching the agent, ready to snap the screen")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Snap", snap)
            .addAction(0, "Ask", ask)
            .build()
    }

    private fun failed(sentence: String) {
        manager().notify(
            FAIL_ID,
            NotificationCompat.Builder(this, CHANNEL_RUNS)
                .setSmallIcon(R.drawable.ic_stat_archiel)
                .setContentTitle("ARCHIEL")
                .setContentText(sentence)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun channels() {
        val manager = manager()
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ASSISTANT, "Assistant", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_RUNS, "Run finished", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun manager() = getSystemService(NotificationManager::class.java)

    private fun stop() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_SNAP = "dev.scdeal.archiel.SNAP"
        const val ACTION_RECONNECT = "dev.scdeal.archiel.RECONNECT"
        const val ACTION_STOP = "dev.scdeal.archiel.STOP"

        private const val NOTIFICATION_ID = 1
        private const val RUN_ID = 2
        private const val FAIL_ID = 3
        private const val CHANNEL_ASSISTANT = "assistant"
        private const val CHANNEL_RUNS = "runs"

        /** Starts the service so the run alerts begin. */
        fun start(ctx: Context) {
            runCatching {
                ContextCompat.startForegroundService(ctx, Intent(ctx, AssistantService::class.java))
            }
        }

        /** Asks the service for a shot of the screen. */
        fun snap(ctx: Context) {
            runCatching {
                ContextCompat.startForegroundService(ctx, Intent(ctx, AssistantService::class.java).setAction(ACTION_SNAP))
            }
        }

        fun stop(ctx: Context) {
            runCatching {
                ctx.startService(Intent(ctx, AssistantService::class.java).setAction(ACTION_STOP))
            }
        }

        /** The server the phone talks to changed, so the service reads the config again. */
        fun reconnect(ctx: Context) {
            if (!Assistant.alerts(ctx)) return
            runCatching {
                ctx.startService(Intent(ctx, AssistantService::class.java).setAction(ACTION_RECONNECT))
            }
        }
    }
}
