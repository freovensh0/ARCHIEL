package dev.scdeal.archiel

import android.content.Context

/**
 * The assistant's own facts, shared by the activity and the service: whether it runs, and the
 * session it watches. They live in preferences because the two are separate callers.
 */
object Assistant {
    private const val FILE = "assistant"
    private const val KEY_ALERTS = "alerts"
    private const val KEY_SESSION = "session"
    private const val KEY_TITLE = "title"

    /** The run alerts: the service stays open and says when a run has finished. */
    fun alerts(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ALERTS, false)

    fun setAlerts(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ALERTS, on).apply()
    }

    /** The session the assistant watches: the last one the phone talked to. */
    fun watch(ctx: Context, sessionID: String, title: String?) {
        prefs(ctx).edit().putString(KEY_SESSION, sessionID).putString(KEY_TITLE, title).apply()
    }

    fun watched(ctx: Context): String? = prefs(ctx).getString(KEY_SESSION, null)

    fun watchedTitle(ctx: Context): String? = prefs(ctx).getString(KEY_TITLE, null)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

/** The one event the assistant turns into a notification: the watched run has finished. */
internal fun isWatchedRun(type: String, sessionID: String?, watched: String?): Boolean =
    type == "session.execution.succeeded" && sessionID != null && sessionID == watched
