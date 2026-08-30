package com.example.ui.calllog

import android.content.Context
import com.example.telephony.CallLogReader
import com.example.telephony.WhatsAppLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the call log and works out, once per row, whether WhatsApp can be opened for it.
 *
 * Both halves are provider / telephony calls, so the whole thing runs on IO and the UI only ever
 * sees the finished list.
 */
object CallLogLoader {

    /** Comfortably more than anyone scrolls, and still one cheap query. */
    const val DEFAULT_LIMIT = 200

    suspend fun load(context: Context, limit: Int = DEFAULT_LIMIT): List<CallRow> =
        withContext(Dispatchers.IO) {
            CallLogReader.readRecent(context, limit).map { record ->
                CallRow(
                    record = record,
                    waDigits = WhatsAppLauncher.toWaMeDigits(context, record.number)
                )
            }
        }
}
