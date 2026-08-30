package com.example.inventory

import org.json.JSONArray
import org.json.JSONObject

/**
 * A row of the `vehicles` table.
 *
 * Every field the database allows to be null is nullable here. The alternative - defaulting them
 * on read - would put "0 km" and "0 DH" on cards for rows that simply have not been filled in
 * yet, which reads as bad data rather than as missing data.
 */
data class Vehicle(
    val id: String,
    val make: String,
    val model: String,
    val year: Int? = null,
    val fuelType: String? = null,
    val transmission: String? = null,
    val mileageKm: Int? = null,
    val color: String? = null,
    val priceMad: Double? = null,
    val location: String? = null,
    val status: String = STATUS_AVAILABLE,
    val photoUrl: String? = null,
    val rating: Int? = null,
    val specialOffer: Boolean = false,
    val offerPriceMad: Double? = null,
    val offerNote: String? = null
) {
    val isAvailable: Boolean get() = status == STATUS_AVAILABLE

    /** "Dacia Duster 2019", or just the make and model when the year is missing. */
    val title: String
        get() = listOfNotNull(make.trim().ifBlank { null }, model.trim().ifBlank { null }, year?.toString())
            .joinToString(" ")
            .ifBlank { "Untitled vehicle" }

    /** The price actually on offer, which is not always the list price. */
    val effectivePrice: Double? get() = if (specialOffer) offerPriceMad ?: priceMad else priceMad

    val displayPrice: String
        get() = effectivePrice?.let { "${VehicleFormat.money(it)} DH" } ?: "Price on request"

    /** Struck-through original, shown only when an offer actually lowers it. */
    val wasPrice: String?
        get() = if (specialOffer && offerPriceMad != null && priceMad != null && offerPriceMad < priceMad) {
            "${VehicleFormat.money(priceMad)} DH"
        } else {
            null
        }

    /** The second line on a card: what someone asks about on the phone. */
    val specLine: String
        get() = listOfNotNull(
            mileageKm?.let { "${VehicleFormat.money(it.toDouble())} km" },
            fuelType?.takeIf { it.isNotBlank() },
            transmission?.takeIf { it.isNotBlank() }
        ).joinToString(" · ").ifBlank { color?.takeIf { it.isNotBlank() } ?: "" }

    /** What goes to WhatsApp. Deliberately more than the card shows - it is the whole pitch. */
    fun toShareText(): String = buildString {
        append(title)
        if (specLine.isNotBlank()) append("\n").append(specLine)
        append("\n").append(displayPrice)
        if (specialOffer) {
            append("  ⚡ ")
            append(offerNote?.takeIf { it.isNotBlank() } ?: "Special offer")
        }
        location?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
    }

    companion object {
        const val STATUS_AVAILABLE = "available"
    }
}

/** Kept apart from Android so the number formatting is testable on the JVM. */
object VehicleFormat {

    /** Thin spaces every three digits, the way prices are written locally: 149 000. */
    fun money(value: Double): String {
        val whole = value.toLong()
        val digits = whole.toString()
        val negative = digits.startsWith("-")
        val bare = if (negative) digits.drop(1) else digits

        val grouped = bare.reversed().chunked(3).joinToString(" ").reversed()
        return if (negative) "-$grouped" else grouped
    }
}

/**
 * PostgREST returns a plain JSON array. Parsing is defensive in the same way the local store's
 * was: a row the app cannot make sense of is skipped rather than allowed to take the screen down,
 * because the data is edited elsewhere and this app does not get to decide what arrives.
 */
internal object VehicleJson {

    /** The cache is written in the same shape PostgREST returns, so one decoder serves both. */
    fun encode(vehicles: List<Vehicle>): String {
        val array = JSONArray()
        vehicles.forEach { v ->
            array.put(
                JSONObject().apply {
                    put("id", v.id)
                    put("make", v.make)
                    put("model", v.model)
                    put("year", v.year ?: JSONObject.NULL)
                    put("fuel_type", v.fuelType ?: JSONObject.NULL)
                    put("transmission", v.transmission ?: JSONObject.NULL)
                    put("mileage_km", v.mileageKm ?: JSONObject.NULL)
                    put("color", v.color ?: JSONObject.NULL)
                    put("price_mad", v.priceMad ?: JSONObject.NULL)
                    put("location", v.location ?: JSONObject.NULL)
                    put("status", v.status)
                    put("photo_url", v.photoUrl ?: JSONObject.NULL)
                    put("rating", v.rating ?: JSONObject.NULL)
                    put("special_offer", v.specialOffer)
                    put("offer_price_mad", v.offerPriceMad ?: JSONObject.NULL)
                    put("offer_note", v.offerNote ?: JSONObject.NULL)
                }
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<Vehicle> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let(::decodeRow)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun decodeRow(obj: JSONObject): Vehicle? {
        val id = obj.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: return null
        return Vehicle(
            id = id,
            make = obj.string("make").orEmpty(),
            model = obj.string("model").orEmpty(),
            year = obj.int("year"),
            fuelType = obj.string("fuel_type"),
            transmission = obj.string("transmission"),
            mileageKm = obj.int("mileage_km"),
            color = obj.string("color"),
            priceMad = obj.double("price_mad"),
            location = obj.string("location"),
            status = obj.string("status") ?: Vehicle.STATUS_AVAILABLE,
            photoUrl = obj.string("photo_url"),
            rating = obj.int("rating"),
            specialOffer = obj.optBoolean("special_offer", false),
            offerPriceMad = obj.double("offer_price_mad"),
            offerNote = obj.string("offer_note")
        )
    }

    /** org.json turns SQL nulls into the string "null", which is not a colour or a fuel type. */
    private fun JSONObject.string(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun JSONObject.int(key: String): Int? = if (isNull(key)) null else optInt(key).takeIf {
        it != 0 || optString(key) == "0"
    }

    private fun JSONObject.double(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

    /** The columns the app reads, so PostgREST is not asked for the whole row. */
    val SELECT = listOf(
        "id", "make", "model", "year", "fuel_type", "transmission", "mileage_km", "color",
        "price_mad", "location", "status", "photo_url", "rating", "special_offer",
        "offer_price_mad", "offer_note"
    ).joinToString(",")
}
