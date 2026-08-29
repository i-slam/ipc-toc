package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.data.AppPrefs
import com.example.data.EventSource
import com.example.data.LogEventBus
import com.example.service.KeepAliveForegroundService

/**
 * One tap, one chain: request the runtime permissions, then the overlay grant, then the battery
 * exemption, then start the keep-alive service and take the wake lock. Each step is skipped when
 * it is already satisfied, so a second tap on a configured phone is a no-op that just re-arms.
 *
 * Returns the trigger to call from a button, a shortcut or a tile.
 */
@Composable
fun rememberQuickArm(): () -> Unit {
    val context = LocalContext.current

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { QuickArm.finish(context) }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (QuickArm.needsBatteryExemption(context)) {
            batteryLauncher.launch(QuickArm.batteryIntent(context))
        } else {
            QuickArm.finish(context)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        when {
            QuickArm.needsOverlay(context) -> overlayLauncher.launch(QuickArm.overlayIntent(context))
            QuickArm.needsBatteryExemption(context) -> batteryLauncher.launch(QuickArm.batteryIntent(context))
            else -> QuickArm.finish(context)
        }
    }

    return {
        val missing = QuickArm.missingRuntimePermissions(context)
        when {
            missing.isNotEmpty() -> permissionLauncher.launch(missing)
            QuickArm.needsOverlay(context) -> overlayLauncher.launch(QuickArm.overlayIntent(context))
            QuickArm.needsBatteryExemption(context) -> batteryLauncher.launch(QuickArm.batteryIntent(context))
            else -> QuickArm.finish(context)
        }
    }
}

object QuickArm {

    fun missingRuntimePermissions(context: Context): Array<String> {
        val wanted = mutableListOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted += Manifest.permission.POST_NOTIFICATIONS
        }
        return wanted
            .filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()
    }

    fun needsOverlay(context: Context): Boolean = !Settings.canDrawOverlays(context)

    fun needsBatteryExemption(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) != true
    }

    fun overlayIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )

    fun batteryIntent(context: Context): Intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    )

    /** Everything that can be granted has been asked for: start the engine with what we have. */
    fun finish(context: Context) {
        KeepAliveForegroundService.start(context)
        KeepAliveForegroundService.toggleWakeLock(context, true)
        AppPrefs.setArmed(context, true)

        val outstanding = buildList {
            if (missingRuntimePermissions(context).isNotEmpty()) add("call permissions")
            if (needsOverlay(context)) add("overlay")
            if (needsBatteryExemption(context)) add("battery exemption")
        }

        val message = if (outstanding.isEmpty()) {
            "Armed: service, wake lock and every permission are in place"
        } else {
            "Armed, but still missing ${outstanding.joinToString(", ")}"
        }

        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        LogEventBus.log(
            source = EventSource.SYSTEM_DIAGNOSTIC,
            action = "Quick Arm",
            details = message,
            isSuccess = outstanding.isEmpty()
        )
    }
}
