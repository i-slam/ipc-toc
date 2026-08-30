package com.example.inventory

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Where the inventory lives. Empty when the build was not given one, which is the case for any
 * build made without local.properties - the app then falls back to whatever it last cached.
 */
data class SupabaseConfig(val url: String, val key: String) {
    val isConfigured: Boolean get() = url.isNotBlank() && key.isNotBlank()

    companion object {
        val NONE = SupabaseConfig("", "")
    }
}

/**
 * Read-only PostgREST client for the `vehicles` table.
 *
 * Deliberately HttpURLConnection and org.json rather than a client library: this module is shared
 * with an app whose whole point is being small, and one authenticated GET does not justify
 * several hundred kilobytes of dependency.
 *
 * It only ever selects. The app is a reader - the database is the source of truth and is edited
 * elsewhere - so there is no code path here that could write to it even if the key allowed it.
 */
object VehiclesApi {

    private const val TAG = "VehiclesApi"
    private const val TIMEOUT_MS = 12_000

    sealed interface Result {
        data class Ok(val vehicles: List<Vehicle>) : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun fetch(config: SupabaseConfig): Result = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.Failed("No inventory database configured for this build")
        }

        val endpoint = buildString {
            append(config.url.trimEnd('/'))
            append("/rest/v1/vehicles?select=").append(VehicleJson.SELECT)
            // Newest first, and only what can actually be sold - filtering server side keeps the
            // response small on a phone connection.
            append("&status=eq.").append(Vehicle.STATUS_AVAILABLE)
            append("&order=created_at.desc")
        }

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("apikey", config.key)
                setRequestProperty("Authorization", "Bearer ${config.key}")
                setRequestProperty("Accept", "application/json")
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                Log.w(TAG, "vehicles returned HTTP $code: ${detail?.take(200)}")
                return@withContext Result.Failed(describe(code))
            }

            val body = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            Result.Ok(VehicleJson.decode(body))
        } catch (e: Exception) {
            Log.w(TAG, "Could not reach the inventory: ${e.message}")
            Result.Failed("Could not reach the inventory")
        } finally {
            connection?.disconnect()
        }
    }

    /** HTTP codes a phone actually hits, said in words rather than numbers. */
    internal fun describe(code: Int): String = when (code) {
        401, 403 -> "The inventory key was refused"
        404 -> "The vehicles table was not found"
        in 500..599 -> "The inventory server is having trouble"
        else -> "The inventory refused the request ($code)"
    }
}
