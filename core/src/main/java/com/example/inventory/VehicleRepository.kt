package com.example.inventory

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What the screen renders: rows, where they came from, and what went wrong if anything did. */
data class InventorySnapshot(
    val vehicles: List<Vehicle>,
    val fromCache: Boolean,
    val problem: String? = null
) {
    val isEmpty: Boolean get() = vehicles.isEmpty()
}

/**
 * The database is the source of truth; the cache exists only so the bubble has something to show
 * when a call ends somewhere with no signal.
 *
 * So: always ask the network first, and fall back to the last good answer rather than to an empty
 * screen. A cached answer is labelled as one, because stale prices are worse than no prices if
 * nobody knows they are stale.
 */
object VehicleRepository {

    private const val TAG = "VehicleRepository"
    private const val CACHE = "vehicles-cache.json"

    suspend fun load(context: Context, config: SupabaseConfig): InventorySnapshot {
        return when (val result = VehiclesApi.fetch(config)) {
            is VehiclesApi.Result.Ok -> {
                writeCache(context, result.vehicles)
                InventorySnapshot(result.vehicles, fromCache = false)
            }

            is VehiclesApi.Result.Failed -> {
                val cached = readCache(context)
                InventorySnapshot(
                    vehicles = cached,
                    fromCache = cached.isNotEmpty(),
                    problem = result.reason
                )
            }
        }
    }

    private fun cacheFile(context: Context) = File(context.filesDir, CACHE)

    private suspend fun writeCache(context: Context, vehicles: List<Vehicle>) =
        withContext(Dispatchers.IO) {
            runCatching { cacheFile(context).writeText(VehicleJson.encode(vehicles)) }
                .onFailure { Log.w(TAG, "Could not cache the inventory: ${it.message}") }
            Unit
        }

    suspend fun readCache(context: Context): List<Vehicle> = withContext(Dispatchers.IO) {
        val file = cacheFile(context)
        if (!file.exists()) return@withContext emptyList()
        VehicleJson.decode(runCatching { file.readText() }.getOrNull())
    }
}
