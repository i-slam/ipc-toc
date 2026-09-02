package com.example.data

import android.os.Process
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object LogEventBus {
    private const val TAG = "LogEventBus"
    private const val MAX_LOGS = 150

    private val _logs = MutableStateFlow<List<LogEvent>>(emptyList())
    val logs: StateFlow<List<LogEvent>> = _logs.asStateFlow()

    fun log(
        source: EventSource,
        action: String,
        details: String,
        isSuccess: Boolean = true
    ) {
        val processName = ProcessInfo.currentProcessName()
        val pid = Process.myPid()
        val threadName = Thread.currentThread().name

        val event = LogEvent(
            source = source,
            action = action,
            details = details,
            processName = processName,
            pid = pid,
            threadName = threadName,
            isSuccess = isSuccess
        )

        Log.i(TAG, "[${event.source.displayName}] ${event.action} | Proc: $processName ($pid) Thread: $threadName | $details")

        _logs.update { current ->
            (listOf(event) + current).take(MAX_LOGS)
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
