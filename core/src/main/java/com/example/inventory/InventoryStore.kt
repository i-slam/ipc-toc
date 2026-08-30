package com.example.inventory

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The inventory on disk: one JSON index plus the images beside it, all inside the app's own
 * files directory so nothing needs storage permissions and uninstalling takes it with it.
 */
object InventoryStore {

    private const val TAG = "InventoryStore"
    private const val INDEX = "inventory.json"
    private const val IMAGES = "inventory"

    private fun indexFile(context: Context) = File(context.filesDir, INDEX)

    private fun imagesDir(context: Context) =
        File(context.filesDir, IMAGES).apply { if (!exists()) mkdirs() }

    suspend fun load(context: Context): List<InventoryItem> = withContext(Dispatchers.IO) {
        val file = indexFile(context)
        if (!file.exists()) return@withContext emptyList()
        InventoryJson.decode(runCatching { file.readText() }.getOrNull())
            .sortedByDescending { it.addedAt }
    }

    suspend fun save(context: Context, items: List<InventoryItem>) = withContext(Dispatchers.IO) {
        runCatching { indexFile(context).writeText(InventoryJson.encode(items)) }
            .onFailure { Log.e(TAG, "Could not write the inventory index", it) }
        Unit
    }

    /** Copies the picked image in, so the item survives the source being deleted or revoked. */
    suspend fun add(
        context: Context,
        name: String,
        price: String,
        source: Uri?
    ): List<InventoryItem> = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val stored = source?.let { copyImage(context, it, id) }
        val items = load(context) + InventoryItem(id = id, name = name, price = price, imageFile = stored)
        save(context, items)
        items.sortedByDescending { it.addedAt }
    }

    suspend fun remove(context: Context, id: String): List<InventoryItem> =
        withContext(Dispatchers.IO) {
            val items = load(context)
            items.firstOrNull { it.id == id }?.imageFile?.let {
                runCatching { File(imagesDir(context), it).delete() }
            }
            val left = items.filterNot { it.id == id }
            save(context, left)
            left
        }

    private fun copyImage(context: Context, source: Uri, id: String): String? = try {
        val name = "$id.jpg"
        context.contentResolver.openInputStream(source)?.use { input ->
            File(imagesDir(context), name).outputStream().use { input.copyTo(it) }
        }
        name
    } catch (e: Exception) {
        Log.w(TAG, "Could not copy the image in: ${e.message}")
        null
    }

    fun imagePath(context: Context, item: InventoryItem): File? =
        item.imageFile?.let { File(imagesDir(context), it) }?.takeIf { it.exists() }

    /**
     * A content:// URI another app may read. WhatsApp cannot open a file:// path out of our
     * private directory, so everything shared has to go through the provider.
     */
    fun shareUri(context: Context, item: InventoryItem): Uri? {
        val file = imagePath(context, item) ?: return null
        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrElse {
            Log.w(TAG, "No FileProvider authority for ${context.packageName}: ${it.message}")
            null
        }
    }
}
