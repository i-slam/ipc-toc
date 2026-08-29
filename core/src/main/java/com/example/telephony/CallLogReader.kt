package com.example.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Direction / outcome of a call log entry, with the badge color used across the UI. */
enum class CallDirection(val label: String, val badgeColor: Long) {
    INCOMING("Incoming", 0xFF34D399),
    OUTGOING("Outgoing", 0xFF38BDF8),
    MISSED("Missed", 0xFFF87171),
    REJECTED("Rejected", 0xFFFB923C),
    BLOCKED("Blocked", 0xFFA78BFA),
    VOICEMAIL("Voicemail", 0xFF22D3EE),
    UNKNOWN("Unknown", 0xFF94A3B8);

    companion object {
        fun fromCallLogType(type: Int): CallDirection = when (type) {
            CallLog.Calls.INCOMING_TYPE -> INCOMING
            CallLog.Calls.OUTGOING_TYPE -> OUTGOING
            CallLog.Calls.MISSED_TYPE -> MISSED
            CallLog.Calls.REJECTED_TYPE -> REJECTED
            CallLog.Calls.BLOCKED_TYPE -> BLOCKED
            CallLog.Calls.VOICEMAIL_TYPE -> VOICEMAIL
            else -> UNKNOWN
        }
    }
}

data class CallRecord(
    val id: Long,
    val number: String,
    val cachedName: String?,
    val direction: CallDirection,
    val timestamp: Long,
    val durationSeconds: Long,
    val phoneAccountId: String?,
    val geocodedLocation: String?,
    val isNew: Boolean,
    val viaNumber: String?
) {
    val displayName: String
        get() = cachedName?.takeIf { it.isNotBlank() }
            ?: number.takeIf { it.isNotBlank() }
            ?: "Unknown number"

    val displayNumber: String
        get() = number.takeIf { it.isNotBlank() } ?: "Private / withheld"

    val formattedDuration: String get() = CallFormat.duration(durationSeconds)

    val formattedTimestamp: String get() = CallFormat.absolute(timestamp)

    fun relativeTime(now: Long = System.currentTimeMillis()): String = CallFormat.relative(timestamp, now)

    /** Multi-line summary used by the copy / share actions. */
    fun toShareText(): String = buildString {
        appendLine("Last call")
        appendLine("Contact  : $displayName")
        appendLine("Number   : $displayNumber")
        appendLine("Type     : ${direction.label}")
        appendLine("When     : $formattedTimestamp (${relativeTime()})")
        appendLine("Duration : $formattedDuration")
        if (!geocodedLocation.isNullOrBlank()) appendLine("Location : $geocodedLocation")
        if (!phoneAccountId.isNullOrBlank()) appendLine("SIM/Acct : $phoneAccountId")
        if (!viaNumber.isNullOrBlank()) appendLine("Via      : $viaNumber")
        append("Unread   : $isNew")
    }
}

/** Pure formatting helpers, kept free of Android APIs so they are unit testable on the JVM. */
object CallFormat {

    fun duration(seconds: Long): String {
        if (seconds <= 0L) return "0s (not connected)"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m ${secs}s"
            minutes > 0 -> "${minutes}m ${secs}s"
            else -> "${secs}s"
        }
    }

    fun absolute(timestamp: Long): String =
        SimpleDateFormat("EEE dd MMM yyyy, HH:mm:ss", Locale.US).format(Date(timestamp))

    fun clockTime(timestamp: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestamp))

    fun relative(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val delta = now - timestamp
        if (delta < 0L) return "in the future"
        val seconds = delta / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            seconds < 60 -> "just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours h ago"
            days < 7 -> "$days d ago"
            else -> "${days / 7} w ago"
        }
    }
}

/**
 * Reads the device call log. Every query is defensive: on Transsion/HiOS builds the provider can
 * throw a SecurityException even after the runtime grant, and returns null cursors while the
 * process is frozen in the background.
 */
object CallLogReader {

    private const val TAG = "CallLogReader"

    val requiredPermissions: Array<String> = arrayOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE
    )

    fun hasCallLogPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
                PackageManager.PERMISSION_GRANTED

    fun hasPhoneStatePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED

    fun readRecent(context: Context, limit: Int = 15): List<CallRecord> {
        if (!hasCallLogPermission(context)) {
            Log.w(TAG, "readRecent: READ_CALL_LOG not granted")
            return emptyList()
        }

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.PHONE_ACCOUNT_ID,
            CallLog.Calls.GEOCODED_LOCATION,
            CallLog.Calls.NEW,
            CallLog.Calls.VIA_NUMBER
        )

        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
                val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val accountIdx = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
                val geoIdx = cursor.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION)
                val newIdx = cursor.getColumnIndex(CallLog.Calls.NEW)
                val viaIdx = cursor.getColumnIndex(CallLog.Calls.VIA_NUMBER)

                val records = mutableListOf<CallRecord>()
                while (cursor.moveToNext() && records.size < limit) {
                    records += CallRecord(
                        id = if (idIdx >= 0) cursor.getLong(idIdx) else records.size.toLong(),
                        number = if (numberIdx >= 0) cursor.getString(numberIdx).orEmpty() else "",
                        cachedName = if (nameIdx >= 0) cursor.getString(nameIdx) else null,
                        direction = CallDirection.fromCallLogType(
                            if (typeIdx >= 0) cursor.getInt(typeIdx) else -1
                        ),
                        timestamp = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L,
                        durationSeconds = if (durationIdx >= 0) cursor.getLong(durationIdx) else 0L,
                        phoneAccountId = if (accountIdx >= 0) cursor.getString(accountIdx) else null,
                        geocodedLocation = if (geoIdx >= 0) cursor.getString(geoIdx) else null,
                        isNew = newIdx >= 0 && cursor.getInt(newIdx) == 1,
                        viaNumber = if (viaIdx >= 0) cursor.getString(viaIdx) else null
                    )
                }
                Log.i(TAG, "readRecent: returning ${records.size} record(s)")
                records
            } ?: emptyList()
        } catch (e: SecurityException) {
            Log.w(TAG, "readRecent: SecurityException from CallLog provider: ${e.message}")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "readRecent: query failed", e)
            emptyList()
        }
    }

    fun readLast(context: Context): CallRecord? = readRecent(context, limit = 1).firstOrNull()
}
