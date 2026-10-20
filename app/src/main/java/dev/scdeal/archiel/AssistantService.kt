package dev.scdeal.archiel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
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
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * The assistant half of the app: a foreground service that holds the screen capture and the event
 * stream. It can snap the screen from any app, and it says when the watched run has finished.
 *
 * The service is the only owner of the MediaProjection, because a projection is one per process
 * and the consent behind it cannot be reused.
 */
class AssistantService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var client: OpenCodeClient? = null
    private var stream: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        channels()
        Assistant.setOn(this, true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CAPTURE -> {
                capture()
                return START_NOT_STICKY
            }
            ACTION_RECONNECT -> {
                connect()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                stop()
                return START_NOT_STICKY
            }
        }
        val code = intent?.getIntExtra(EXTRA_CODE, NO_CODE) ?: NO_CODE
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java) }
        // The foreground service has to be up before the projection is asked for, or the system refuses it.
        foreground(code != NO_CODE && data != null)
        if (code != NO_CODE && data != null) attach(code, data)
        connect()
        if (intent?.getBooleanExtra(EXTRA_CAPTURE, false) == true) capture()
        // The consent cannot be replayed, so a restarted service would have nothing to capture with.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Assistant.setOn(this, false)
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ the capture

    /** The user pulled the sharing chip: the capture is gone, and the media projection type with it. */
    private val revoked = object : MediaProjection.Callback() {
        override fun onStop() {
            stop()
        }
    }

    private fun attach(code: Int, data: Intent) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val granted = runCatching { manager.getMediaProjection(code, data) }.getOrNull()
        if (granted == null) {
            stop()
            return
        }
        // A second consent replaces the first one; the old projection cannot be used twice.
        projection?.let {
            runCatching { it.unregisterCallback(revoked) }
            runCatching { it.stop() }
        }
        granted.registerCallback(revoked, main)
        projection = granted
    }

    private fun capture() {
        val source = projection
        if (source == null) {
            // Without the consent there is nothing to capture with, so the assistant is off.
            failed("The screen capture is off. Open ARCHIEL to turn it on again.")
            stop()
            return
        }
        scope.launch {
            val file = snap(source)
            if (file == null) {
                failed("The screen could not be captured.")
                return@launch
            }
            handOver(file)
        }
    }

    /** One frame of the screen, written as a PNG in the cache; the app picks it up from there. */
    private suspend fun snap(source: MediaProjection): File? = withContext(Dispatchers.IO) {
        val size = screenSize()
        val reader = ImageReader.newInstance(size.first, size.second, PixelFormat.RGBA_8888, 2)
        // Without the mirror flag the virtual display shows the app's own windows and nothing else.
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
        } else {
            0
        }
        val display = source.createVirtualDisplay(
            "archiel",
            size.first,
            size.second,
            resources.displayMetrics.densityDpi,
            flags,
            reader.surface,
            null,
            null,
        )
        try {
            val first = awaitImage(reader) ?: return@withContext null
            // The first frame of a fresh display can be blank, so a later one is the picture.
            delay(150)
            val second = runCatching { reader.acquireLatestImage() }.getOrNull()
            val shot = second ?: first
            val bitmap = try {
                toBitmap(shot)
            } finally {
                shot.close()
                if (second != null) first.close()
            }
            val dir = File(cacheDir, "captures").apply { mkdirs() }
            val file = File(dir, "capture-${System.currentTimeMillis()}.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            file
        } finally {
            display.release()
            reader.close()
        }
    }

    private suspend fun awaitImage(reader: ImageReader): Image? {
        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline) {
            val image = runCatching { reader.acquireLatestImage() }.getOrNull()
            if (image != null) return image
            delay(40)
        }
        return null
    }

    private fun toBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val width = image.width
        val height = image.height
        // The row can be padded, so it is copied line by line instead of in one block.
        val rowStride = plane.rowStride
        val pixels = ByteArray(width * height * 4)
        val row = ByteArray(rowStride)
        val buffer = plane.buffer
        for (y in 0 until height) {
            val start = y * rowStride
            if (start >= buffer.limit()) break
            buffer.position(start)
            val take = min(rowStride, buffer.remaining())
            buffer.get(row, 0, take)
            System.arraycopy(row, 0, pixels, y * width * 4, min(width * 4, take))
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels))
        return scaled(bitmap)
    }

    /** A phone screen is taller than the model wants; the long edge is what the picture is read at. */
    private fun scaled(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_EDGE) return bitmap
        val factor = MAX_EDGE.toFloat() / longest
        val smaller = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * factor).toInt(),
            (bitmap.height * factor).toInt(),
            true,
        )
        bitmap.recycle()
        return smaller
    }

    private fun screenSize(): Pair<Int, Int> {
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    /** The capture is a file, so the app picks it up the same way it takes a share from another app. */
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

    private fun foreground(captures: Boolean) {
        val notification = assistantNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = if (captures) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
            // The event stream has to stay open for hours, so it is a special use service: a data
            // sync service would be capped at six hours a day on Android 15.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun assistantNotification(): Notification {
        val capture = PendingIntent.getService(
            this,
            1,
            Intent(this, AssistantService::class.java).setAction(ACTION_CAPTURE),
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
            .addAction(0, "Capture", capture)
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
        projection?.let {
            runCatching { it.unregisterCallback(revoked) }
            runCatching { it.stop() }
        }
        projection = null
        Assistant.setOn(this, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_CAPTURE = "dev.scdeal.archiel.CAPTURE"
        const val ACTION_RECONNECT = "dev.scdeal.archiel.RECONNECT"
        const val ACTION_STOP = "dev.scdeal.archiel.STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val EXTRA_CAPTURE = "capture-now"

        private const val NO_CODE = Int.MIN_VALUE
        private const val NOTIFICATION_ID = 1
        private const val RUN_ID = 2
        private const val FAIL_ID = 3
        private const val CHANNEL_ASSISTANT = "assistant"
        private const val CHANNEL_RUNS = "runs"
        private const val MAX_EDGE = 1600

        /** Starts the service with the consent the user just gave. */
        fun start(ctx: Context, code: Int, data: Intent, capture: Boolean) {
            val intent = Intent(ctx, AssistantService::class.java)
                .putExtra(EXTRA_CODE, code)
                .putExtra(EXTRA_DATA, data)
                .putExtra(EXTRA_CAPTURE, capture)
            ContextCompat.startForegroundService(ctx, intent)
        }

        /** Asks the running service for a screen capture; false when it is not running. */
        fun capture(ctx: Context): Boolean {
            if (!Assistant.isOn(ctx)) return false
            return runCatching {
                ctx.startService(Intent(ctx, AssistantService::class.java).setAction(ACTION_CAPTURE))
            }.isSuccess
        }

        fun stop(ctx: Context) {
            runCatching {
                ctx.startService(Intent(ctx, AssistantService::class.java).setAction(ACTION_STOP))
            }
        }

        /** The server the phone talks to changed, so the service reads the config again. */
        fun reconnect(ctx: Context) {
            if (!Assistant.isOn(ctx)) return
            runCatching {
                ctx.startService(Intent(ctx, AssistantService::class.java).setAction(ACTION_RECONNECT))
            }
        }
    }
}
