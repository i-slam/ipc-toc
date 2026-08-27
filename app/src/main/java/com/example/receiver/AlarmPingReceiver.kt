package com.example.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.example.data.EventSource
import com.example.data.LogEventBus
import com.example.service.SeparateProcessOverlayService

class AlarmPingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "AlarmPingReceiver onReceive: $action")

        LogEventBus.log(
            source = EventSource.ALARM_PULSE,
            action = "Alarm Fired",
            details = "Exact alarm woke up device/process and executed payload"
        )

        val showOverlay = intent.getBooleanExtra("show_overlay", false)
        if (showOverlay) {
            SeparateProcessOverlayService.show(context, "exact_alarm_pulse")
        }
    }

    companion object {
        private const val TAG = "AlarmPingReceiver"
        const val ACTION_ALARM_PING = "com.example.ACTION_ALARM_PING"

        fun scheduleExactPing(context: Context, delaySeconds: Int, showOverlay: Boolean = false) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmPingReceiver::class.java).apply {
                action = ACTION_ALARM_PING
                putExtra("show_overlay", showOverlay)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                101,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerTime = SystemClock.elapsedRealtime() + (delaySeconds * 1000L)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }

            LogEventBus.log(
                source = EventSource.ALARM_PULSE,
                action = "Alarm Scheduled",
                details = "Scheduled setExactAndAllowWhileIdle in $delaySeconds seconds"
            )
        }
    }
}
