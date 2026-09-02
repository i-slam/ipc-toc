package com.example.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class EventSource(val displayName: String, val badgeColor: Long) {
    STATIC_BROADCAST("Static Receiver", 0xFFE57373),
    DYNAMIC_BROADCAST("Dynamic Receiver", 0xFF81C784),
    DIRECT_SERVICE("Direct Service Intent", 0xFF64B5F6),
    MESSENGER_IPC("Messenger IPC", 0xFFFFB74D),
    ALARM_PULSE("AlarmManager Pulse", 0xFFBA68C8),
    CONTENT_OBSERVER("Content Observer", 0xFF4DD0E1),
    TELEPHONY_CALLBACK("Telephony Callback", 0xFFFFD54F),
    OVERLAY_WINDOW("Overlay Window", 0xFF80CBC4),
    SYSTEM_DIAGNOSTIC("System Diagnostic", 0xFF90A4AE)
}

data class LogEvent(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val source: EventSource,
    val action: String,
    val details: String,
    val processName: String,
    val pid: Int,
    val threadName: String,
    val isSuccess: Boolean = true
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
}
