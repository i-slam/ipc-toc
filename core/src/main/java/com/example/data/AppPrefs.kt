package com.example.data

import android.content.Context

/**
 * The little state that has to outlive the process: whether the user armed the keep-alive
 * engine, so the boot receiver and the quick-settings tile know what to restore.
 */
object AppPrefs {

    private const val FILE = "ipc_poc_prefs"
    private const val KEY_ARMED = "keep_alive_armed"
    private const val KEY_FLOATING = "floating_rail_enabled"
    private const val KEY_AUTO_POP = "auto_pop_on_call_end"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isArmed(context: Context): Boolean = prefs(context).getBoolean(KEY_ARMED, false)

    fun setArmed(context: Context, armed: Boolean) {
        prefs(context).edit().putBoolean(KEY_ARMED, armed).apply()
    }

    /** Whether the system-wide floating rail should be on screen, across reboots. */
    fun isFloatingRailEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FLOATING, false)

    fun setFloatingRailEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FLOATING, enabled).apply()
    }

    /**
     * Whether the bubble opens itself when a call ends. On by default: reacting the moment a
     * call finishes is the point of the thing, and a bubble that waits to be tapped is just a
     * shortcut.
     */
    fun isAutoPopEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_POP, true)

    fun setAutoPopEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_POP, enabled).apply()
    }
}
