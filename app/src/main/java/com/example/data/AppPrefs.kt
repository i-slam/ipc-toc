package com.example.data

import android.content.Context

/**
 * The little state that has to outlive the process: whether the user armed the keep-alive
 * engine, so the boot receiver and the quick-settings tile know what to restore.
 */
object AppPrefs {

    private const val FILE = "ipc_poc_prefs"
    private const val KEY_ARMED = "keep_alive_armed"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isArmed(context: Context): Boolean = prefs(context).getBoolean(KEY_ARMED, false)

    fun setArmed(context: Context, armed: Boolean) {
        prefs(context).edit().putBoolean(KEY_ARMED, armed).apply()
    }
}
