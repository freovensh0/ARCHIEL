package dev.scdeal.archiel

import android.content.Context

data class ServerConfig(val baseUrl: String, val password: String)

object Cfg {
    /** Emülatörde host makine 10.0.2.2, telefonda Termux ise 127.0.0.1 */
    const val DEFAULT_URL = "http://10.0.2.2:4096"

    // shortcut: parola düz SharedPreferences'ta; cihaz ele geçerse sunucu parolası ele geçer.
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
