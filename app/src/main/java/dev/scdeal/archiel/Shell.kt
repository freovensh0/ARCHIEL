package dev.scdeal.archiel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.File

/**
 * The shell, reached through Shizuku: the phone's own user (adb) runs the commands. It is how the
 * screen is captured without a MediaProjection consent, so nothing of the screen is held open
 * between the shots.
 */
object Shell {
    /** The binder is alive and this app may use the shell. */
    fun ready(): Boolean =
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    /** Asks the Shizuku app for the shell; the answer arrives in the listener. */
    fun ask() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    /** The Shizuku app, so the user can start it. */
    fun open(ctx: Context) {
        val intent = ctx.packageManager.getLaunchIntentForPackage(SHIZUKU) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
    }

    /**
     * Runs a command as the shell user and returns its exit code.
     *
     * shortcut: this calls the server side newProcess, which the Shizuku library keeps for itself.
     * If a future server drops it, the fallback is a user service of our own.
     */
    fun run(cmd: List<String>): Int {
        val binder = Shizuku.getBinder() ?: return -1
        val service = IShizukuService.Stub.asInterface(binder) ?: return -1
        val remote = service.newProcess(cmd.toTypedArray(), null, null)
        // The output is drained, so a chatty command cannot block on a full pipe.
        val stream = remote.inputStream
        if (stream != null) {
            val buffer = ByteArray(4096)
            ParcelFileDescriptor.AutoCloseInputStream(stream).use { input ->
                while (input.read(buffer) > 0) Unit
            }
        }
        return remote.waitFor()
    }

    /** The screen as a PNG, in the app's own external files, where the shell may write it. */
    fun snap(ctx: Context): File? {
        val dir = ctx.getExternalFilesDir(null) ?: return null
        val out = File(dir, "snap.png")
        out.delete()
        val code = run(listOf("screencap", "-p", out.absolutePath))
        return out.takeIf { code == 0 && it.length() > 0 }
    }

    const val REQUEST_CODE = 4207

    private const val SHIZUKU = "moe.shizuku.privileged.api"
}
