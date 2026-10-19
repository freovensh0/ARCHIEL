package dev.scdeal.archiel

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class ServerConfig(val baseUrl: String, val password: String, val name: String = "")

/** A profile as it sits in the preference file; its name is the key the other calls use. */
@Serializable
data class StoredProfile(val name: String = "", val baseUrl: String = "", val password: String = "")

object Cfg {
    /** The emulator reaches the host machine at 10.0.2.2; in Termux the server is on 127.0.0.1. */
    const val DEFAULT_URL = "http://10.0.2.2:4096"

    // shortcut: the active password and the whole profile list are stored unencrypted; anyone with the device reaches the server.
    private const val FILE = "archiel"
    private const val KEY_URL = "url"
    private const val KEY_PW = "pw"
    private const val KEY_PROFILES = "profiles"
    private const val KEY_ACTIVE = "active"

    private val json = Json { ignoreUnknownKeys = true }
    private val profilesSerializer = ListSerializer(StoredProfile.serializer())

    private fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The active profile, mirrored in `url` and `pw` so the single-server layout keeps reading one pair. */
    fun load(ctx: Context): ServerConfig {
        val sp = prefs(ctx)
        return ServerConfig(
            sp.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL,
            sp.getString(KEY_PW, "") ?: "",
            sp.getString(KEY_ACTIVE, "") ?: "",
        )
    }

    /** Every profile; a first run, or a file from the single-server version, holds only the active one. */
    fun list(ctx: Context): List<ServerConfig> {
        val stored = prefs(ctx).getString(KEY_PROFILES, null)
            ?.let { raw -> runCatching { json.decodeFromString(profilesSerializer, raw) }.getOrNull() }
            .orEmpty()
        return stored.map { ServerConfig(it.baseUrl, it.password, it.name) }
            .ifEmpty { listOf(load(ctx)) }
    }

    fun save(ctx: Context, cfg: ServerConfig) {
        // The fields belong to the active profile, so its name comes from storage rather than the caller.
        val active = load(ctx).name
        val next = cleaned(cfg).copy(name = active)
        val profiles = list(ctx).map { if (it.name == active) next else it }
        write(ctx, profiles, next)
    }

    fun add(ctx: Context, cfg: ServerConfig) {
        val next = cleaned(cfg)
        // The name is the key, so adding an existing one replaces it rather than storing a duplicate.
        val profiles = list(ctx).filterNot { it.name == next.name } + next
        write(ctx, profiles, next)
    }

    /** Removing the last profile would leave the app with no server to open. */
    fun remove(ctx: Context, name: String) {
        val profiles = list(ctx)
        if (profiles.size <= 1) return
        val remaining = profiles.filterNot { it.name == name }
        if (remaining.size == profiles.size) return
        // Removing the active profile moves the app to the first one left.
        write(ctx, remaining, load(ctx).takeIf { it.name != name } ?: remaining.first())
    }

    fun activate(ctx: Context, name: String) {
        val profiles = list(ctx)
        write(ctx, profiles, profiles.firstOrNull { it.name == name } ?: return)
    }

    private fun cleaned(cfg: ServerConfig) = cfg.copy(baseUrl = cfg.baseUrl.trim().trimEnd('/'))

    private fun write(ctx: Context, profiles: List<ServerConfig>, active: ServerConfig) {
        val stored = profiles.map { StoredProfile(it.name, it.baseUrl, it.password) }
        prefs(ctx).edit()
            .putString(KEY_PROFILES, json.encodeToString(profilesSerializer, stored))
            .putString(KEY_ACTIVE, active.name)
            .putString(KEY_URL, active.baseUrl)
            .putString(KEY_PW, active.password)
            .apply()
    }
}
