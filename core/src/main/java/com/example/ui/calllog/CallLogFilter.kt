package com.example.ui.calllog

import com.example.telephony.CallDirection
import com.example.telephony.CallRecord

/**
 * The tabs across the top of the call log. Deliberately four: any more and the labels stop
 * fitting on a phone, any fewer and "missed" - the one people actually come looking for - is
 * buried in the middle of everything else.
 */
enum class CallLogTab(val label: String) {
    ALL("All"),
    MISSED("Missed"),
    INCOMING("In"),
    OUTGOING("Out")
}

/**
 * A call log entry plus the one thing the row cannot work out cheaply: whether the number can be
 * turned into a WhatsApp chat.
 *
 * [waDigits] is resolved once, off the main thread, because [WhatsAppLauncher.toWaMeDigits] reads
 * the SIM country through TelephonyManager - not something to do per recomposition, per row.
 */
data class CallRow(val record: CallRecord, val waDigits: String?) {
    val canWhatsApp: Boolean get() = waDigits != null
}

/**
 * Which rows belong under which tab, and how many. Pure, so the mapping is pinned by unit tests
 * rather than by opening the app and counting.
 */
object CallLogFilter {

    fun matches(tab: CallLogTab, direction: CallDirection): Boolean = when (tab) {
        CallLogTab.ALL -> true
        // Rejected and blocked calls are ones the user did not take either, and someone scanning
        // for "who tried to reach me" wants them in the same place as a plain missed call.
        CallLogTab.MISSED -> direction == CallDirection.MISSED ||
                direction == CallDirection.REJECTED ||
                direction == CallDirection.BLOCKED
        // A voicemail is an incoming call that ended up somewhere else.
        CallLogTab.INCOMING -> direction == CallDirection.INCOMING ||
                direction == CallDirection.VOICEMAIL
        CallLogTab.OUTGOING -> direction == CallDirection.OUTGOING
    }

    fun filter(tab: CallLogTab, rows: List<CallRow>): List<CallRow> =
        rows.filter { matches(tab, it.record.direction) }

    /** Counts for every tab from a single pass, for the badges in the tab strip. */
    fun counts(rows: List<CallRow>): Map<CallLogTab, Int> =
        CallLogTab.entries.associateWith { tab -> rows.count { matches(tab, it.record.direction) } }

}
