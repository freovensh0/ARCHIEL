package dev.scdeal.archiel

import android.content.Context

data class ServerConfig(val baseUrl: String, val password: String)

object Cfg {
    /** The emulator reaches the host machine at 10.0.2.2; in Termux the server is on 127.0.0.1. */
    const val DEFAULT_URL = "http://10.0.2.2:4096"

    // shortcut: the password is stored unencrypted; anyone with the device reaches the server.
    private const val FILE = "archiel"

    fun load(ctx: Context): ServerConfig {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return ServerConfig(
            sp.getString("url", DEFAULT_URL) ?: DEFAULT_URL,
            sp.getString("pw", "") ?: "",
        )
    }

    fun save(ctx: Context, cfg: ServerConfig) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("url", cfg.baseUrl.trim().trimEnd('/'))
            .putString("pw", cfg.password)
            .apply()
    }
}
