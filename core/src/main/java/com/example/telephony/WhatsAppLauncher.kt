package com.example.telephony

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Opens a WhatsApp chat with a number straight from the call log.
 *
 * The awkward part is the number itself: call log entries are stored however they were dialled -
 * "0803 123 4567", "+234 803 123 4567", with spaces, dashes or a leading zero - while wa.me wants
 * bare digits including a country code. The SIM's country is used to resolve local formats.
 */
object WhatsAppLauncher {

    private const val TAG = "WhatsAppLauncher"

    /** Consumer WhatsApp first, then Business. */
    private val PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun installedPackage(context: Context): String? = PACKAGES.firstOrNull { pkg ->
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isInstalled(context: Context): Boolean = installedPackage(context) != null

    /**
     * Normalises a call log number to the digits wa.me expects, or null when there is nothing
     * usable - a withheld number, or a local format with no SIM country to resolve it against.
     */
    fun toWaMeDigits(context: Context, rawNumber: String?): String? {
        val raw = rawNumber?.trim().orEmpty()
        if (raw.isEmpty()) return null

        val iso = countryIso(context)
        val e164 = iso?.let {
            runCatching { PhoneNumberUtils.formatNumberToE164(raw, it) }.getOrNull()
        }

        // formatNumberToE164 returns null for anything it cannot place, including a local number
        // with no country context. Falling back to the raw digits still works for numbers that
        // were already stored internationally.
        val digits = (e164 ?: raw).filter { it.isDigit() }

        return digits.takeIf { it.length >= MIN_DIGITS }
    }

    private fun countryIso(context: Context): String? {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return null
        val iso = telephony.networkCountryIso?.takeIf { it.isNotBlank() }
            ?: telephony.simCountryIso?.takeIf { it.isNotBlank() }
        return iso?.uppercase()
    }

    /** The chat intent, or null when the number cannot be turned into something WhatsApp accepts. */
    fun chatIntent(context: Context, rawNumber: String?, message: String = ""): Intent? {
        val digits = toWaMeDigits(context, rawNumber) ?: return null

        val uri = buildString {
            append("https://wa.me/").append(digits)
            if (message.isNotBlank()) {
                append("?text=").append(Uri.encode(message))
            }
        }

        return Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Targeting the package skips the browser hop. Without WhatsApp installed the intent
            // is left unpackaged so the link still resolves somewhere sensible.
            installedPackage(context)?.let { setPackage(it) }
        }
    }

    /** Returns what went wrong, or null when the chat opened. */
    fun openChat(context: Context, rawNumber: String?, message: String = ""): String? {
        val intent = chatIntent(context, rawNumber, message)
            ?: return "No usable number for WhatsApp"

        return try {
            context.startActivity(intent)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Could not open WhatsApp: ${e.message}")
            "WhatsApp could not be opened"
        }
    }

    /** Shorter than this and it is an emergency or service code, not something to message. */
    private const val MIN_DIGITS = 6
}
