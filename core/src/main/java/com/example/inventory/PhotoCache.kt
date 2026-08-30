package com.example.inventory

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Photos come from the database as URLs, and WhatsApp cannot be handed a URL: a share intent
 * carries a content:// URI backed by a real file. So anything being sent is fetched to disk
 * first, and kept there because the same car gets sent to more than one caller.
 */
object PhotoCache {

    private const val TAG = "PhotoCache"
    private const val DIR = "inventory"
    private const val TIMEOUT_MS = 15_000

    /** Max bytes accepted for one photo: a dealership photo that large is a mistake, not a photo. */
    private const val MAX_BYTES = 8L * 1024 * 1024

    private fun dir(context: Context) =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    /** Same directory the FileProvider exposes, so a cached photo is shareable as it lands. */
    fun fileFor(context: Context, vehicle: Vehicle): File =
        File(dir(context), "${vehicle.id.replace(Regex("[^A-Za-z0-9_-]"), "_")}.jpg")

    fun cached(context: Context, vehicle: Vehicle): File? =
        fileFor(context, vehicle).takeIf { it.exists() && it.length() > 0 }

    /** Downloads the photo if it is not already here. Returns null when there is nothing to get. */
    suspend fun ensure(context: Context, vehicle: Vehicle): File? = withContext(Dispatchers.IO) {
        cached(context, vehicle)?.let { return@withContext it }

        val source = vehicle.photoUrl?.takeIf { it.startsWith("http") } ?: return@withContext null
        val target = fileFor(context, vehicle)

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(source).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
            }
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "photo for ${vehicle.id} returned HTTP ${connection.responseCode}")
                return@withContext null
            }

            // Written to a temporary name and moved into place, so a download cut off halfway
            // does not leave a truncated file that later reads as a valid cache hit.
            val partial = File(target.parentFile, "${target.name}.part")
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    var total = 0L
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) {
                            Log.w(TAG, "photo for ${vehicle.id} is over the size limit, dropping it")
                            partial.delete()
                            return@withContext null
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (!partial.renameTo(target)) {
                partial.delete()
                return@withContext null
            }
            target
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch the photo for ${vehicle.id}: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun shareUri(context: Context, file: File): Uri? = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrElse {
        Log.w(TAG, "No FileProvider authority for ${context.packageName}: ${it.message}")
        null
    }
}
