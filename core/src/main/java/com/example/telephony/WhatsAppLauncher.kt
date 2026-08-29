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
    fun toWaMeDigits(context: Context, rawNumber: String?): String? =
        normalise(rawNumber, platformE164(context, rawNumber))

    /**
     * The decision, separated from the platform call so it can be tested without a device:
     * [e164] is whatever `PhoneNumberUtils` made of the number, or null when it could not place it.
     *
     * A number that still carries a national trunk prefix after that is deliberately refused.
     * Passing "07700900123" to wa.me does not fail politely - WhatsApp opens and says the number
     * is invalid, which looks like the app is broken rather than the number being unresolvable.
     */
    fun normalise(rawNumber: String?, e164: String?): String? {
        val raw = rawNumber?.trim().orEmpty()
        if (raw.isEmpty()) return null

        if (e164 != null) {
            return e164.filter { it.isDigit() }.takeIf { it.length >= MIN_DIGITS }
        }

        val digits = raw.filter { it.isDigit() }

        // A leading zero is a trunk prefix, meaningless without a country code.
        if (raw.trimStart().startsWith("0")) return null

        // Anything shorter than a full international number is a short code or an unresolved
        // local one; either way it is not a WhatsApp account.
        return digits.takeIf { it.length >= MIN_INTERNATIONAL_DIGITS }
    }

    private fun platformE164(context: Context, rawNumber: String?): String? {
        val raw = rawNumber?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val iso = countryIso(context) ?: return null
        return runCatching { PhoneNumberUtils.formatNumberToE164(raw, iso) }.getOrNull()
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

    /** Without a country to resolve against, only an already-international number is usable. */
    private const val MIN_INTERNATIONAL_DIGITS = 10
}
