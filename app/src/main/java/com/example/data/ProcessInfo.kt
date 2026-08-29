package com.example.data

import android.app.Application
import android.os.Build
import android.os.Process

/**
 * Resolves the current OS process name, which this app reports everywhere because the overlay
 * runs in a separate `:overlay` process.
 *
 * `Process.myProcessName()` is API 33, not API 28 - calling it under a `>= P` guard throws
 * NoSuchMethodError and kills the app on every device from Android 8 through 12.
 */
object ProcessInfo {

    fun currentProcessName(): String = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Process.myProcessName()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> Application.getProcessName()
        else -> "pid_${Process.myPid()}"
    }

    /** "name (pid)" for the compact readouts in the diagnostic header and the overlay card. */
    fun currentProcessLabel(): String = "${currentProcessName()} (${Process.myPid()})"
}
