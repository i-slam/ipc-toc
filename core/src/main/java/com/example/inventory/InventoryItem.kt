package com.example.inventory

import org.json.JSONArray
import org.json.JSONObject

/**
 * One thing you might send someone: a car, a part, a package. [imageFile] is a bare filename
 * inside the app's own inventory directory, not a path, so the store stays movable.
 */
data class InventoryItem(
    val id: String,
    val name: String,
    val price: String,
    val imageFile: String? = null,
    val tag: String = "",
    val addedAt: Long = System.currentTimeMillis()
) {
    val displayPrice: String get() = price.ifBlank { "Price on request" }

    fun toShareLine(): String = if (price.isBlank()) name else "$name — $price"
}

/**
 * Serialisation by hand through org.json.
 *
 * The alternative was Room, which would mean KSP in a module built for being small and quick -
 * a few dozen rows in a file is not a database problem.
 */
internal object InventoryJson {

    fun encode(items: List<InventoryItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("price", item.price)
                    put("imageFile", item.imageFile ?: JSONObject.NULL)
                    put("tag", item.tag)
                    put("addedAt", item.addedAt)
                }
            )
        }
        return array.toString()
    }

    /**
     * Never throws: a store that fails to parse should read as empty, not take the app down on
     * launch. A half-written file is the realistic case - the process can die mid-save.
     */
    fun decode(raw: String?): List<InventoryItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                InventoryItem(
                    id = id,
                    name = name,
                    price = obj.optString("price"),
                    imageFile = obj.optString("imageFile").takeIf {
                        it.isNotBlank() && it != "null"
                    },
                    tag = obj.optString("tag"),
                    addedAt = obj.optLong("addedAt", 0L)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
