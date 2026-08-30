package com.example.telephony

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Sends photos to a WhatsApp chat.
 *
 * [WhatsAppLauncher] opens a conversation through wa.me, which carries text and nothing else.
 * Attachments have to go the other way round - as a share intent aimed at the WhatsApp package -
 * and share intents normally land on WhatsApp's own contact picker rather than in a particular
 * chat.
 *
 * The `jid` extra is what skips that picker. It is not a documented API: WhatsApp reads it, it
 * has worked for years, and it could stop working in any release. So it is treated as an
 * optimisation, never a requirement - without it the share still goes through, with one extra
 * tap to choose the recipient.
 */
object WhatsAppSender {

    private const val TAG = "WhatsAppSender"

    /** WhatsApp addresses individual chats as <digits>@s.whatsapp.net. */
    internal fun jidFor(waDigits: String?): String? =
        waDigits?.takeIf { it.isNotBlank() }?.let { "$it@s.whatsapp.net" }

    /**
     * A share intent for [uris], aimed at the chat with [rawNumber] where that number can be
     * resolved, and at WhatsApp generally where it cannot.
     *
     * Returns null only when WhatsApp is absent or there is nothing to send - a picture has no
     * sensible browser fallback the way a wa.me link does.
     */
    fun mediaIntent(
        context: Context,
        rawNumber: String?,
        uris: List<Uri>,
        caption: String = ""
    ): Intent? {
        if (uris.isEmpty()) return null
        val target = WhatsAppLauncher.installedPackage(context) ?: return null

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris.first()) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }

        return intent.apply {
            type = "image/*"
            setPackage(target)
            if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
            jidFor(WhatsAppLauncher.toWaMeDigits(context, rawNumber))?.let { putExtra("jid", it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Returns what went wrong, or null when the share opened. */
    fun sendMedia(
        context: Context,
        rawNumber: String?,
        uris: List<Uri>,
        caption: String = ""
    ): String? {
        if (uris.isEmpty()) return "Nothing selected to send"
        val intent = mediaIntent(context, rawNumber, uris, caption)
            ?: return "WhatsApp is not installed"

        return try {
            context.startActivity(intent)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the WhatsApp share sheet: ${e.message}")
            "WhatsApp could not be opened"
        }
    }

    /**
     * Text-only, for a selection whose items have no photos. Goes through wa.me so it lands in
     * the chat directly rather than on the picker.
     */
    fun sendText(context: Context, rawNumber: String?, text: String): String? =
        WhatsAppLauncher.openChat(context, rawNumber, text)
}
