package com.example.data

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Device fingerprint helpers focused on Transsion (TECNO / Infinix / itel) HiOS builds, which is
 * where the background IPC and overlay restrictions this app diagnoses actually bite.
 */
object DeviceProfile {

    private const val TAG = "DeviceProfile"

    data class Entry(val label: String, val value: String)

    private val TRANSSION_BRANDS = listOf("tecno", "infinix", "itel", "transsion")

    /** HiOS exposes its own version under one of these props depending on the build. */
    private val HIOS_VERSION_PROPS = listOf(
        "ro.tranos.version",
        "ro.tranos.type",
        "ro.os_version",
        "ro.build.version.hios"
    )

    fun isTranssionDevice(): Boolean {
        val fingerprint = "${Build.MANUFACTURER} ${Build.BRAND} ${Build.PRODUCT}".lowercase()
        return TRANSSION_BRANDS.any { fingerprint.contains(it) }
    }

    fun osFlavor(): String {
        val hios = HIOS_VERSION_PROPS.firstNotNullOfOrNull { key ->
            readProp(key)?.takeIf { it.isNotBlank() }
        }
        return when {
            hios != null && isTranssionDevice() -> "HiOS $hios"
            hios != null -> hios
            isTranssionDevice() -> "HiOS (version property unavailable)"
            else -> "Stock / non-Transsion ROM"
        }
    }

    fun hardwareSummary(): List<Entry> = listOf(
        Entry("Manufacturer", Build.MANUFACTURER),
        Entry("Brand", Build.BRAND),
        Entry("Model", Build.MODEL),
        Entry("Device", Build.DEVICE),
        Entry("ROM flavor", osFlavor()),
        Entry("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
        Entry("Build ID", Build.DISPLAY)
    )

    /**
     * The exemptions that decide whether the call-end pipeline can actually fire while the app is
     * backgrounded on HiOS.
     */
    fun restrictionSummary(context: Context): List<Entry> {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryExempt = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        return listOf(
            Entry("Overlay permission", if (Settings.canDrawOverlays(context)) "Granted" else "Denied"),
            Entry("Battery exemption", if (batteryExempt) "Ignoring optimizations" else "Optimized (may freeze)"),
            Entry("Power save mode", if (powerManager?.isPowerSaveMode == true) "ON" else "off"),
            Entry(
                "Doze / idle",
                if (powerManager?.isDeviceIdleMode == true) "Device idle (Doze)" else "Interactive"
            )
        )
    }

    /**
     * Reads a system property. `android.os.SystemProperties` is hidden API, so fall back to the
     * `getprop` binary when reflection is blocked.
     */
    fun readProp(key: String): String? {
        readPropViaReflection(key)?.let { return it }
        return readPropViaGetprop(key)
    }

    private fun readPropViaReflection(key: String): String? = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java, String::class.java)
        (get.invoke(null, key, "") as? String)?.takeIf { it.isNotBlank() }
    } catch (e: Throwable) {
        Log.d(TAG, "SystemProperties reflection unavailable for $key: ${e.message}")
        null
    }

    private fun readPropViaGetprop(key: String): String? = try {
        val process = ProcessBuilder("/system/bin/getprop", key).redirectErrorStream(true).start()
        val value = BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine() }
        process.waitFor()
        value?.trim()?.takeIf { it.isNotBlank() }
    } catch (e: Throwable) {
        Log.d(TAG, "getprop unavailable for $key: ${e.message}")
        null
    }
}
