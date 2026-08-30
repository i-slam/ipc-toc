package com.example.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Parsing rows the app does not control. Stock is edited elsewhere, so half-filled rows and SQL
 * nulls are normal traffic rather than corruption, and the screen has to stay standing either way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 36])
class VehicleJsonTest {

    private val full = """
        [{"id":"v1","make":"Dacia","model":"Duster","year":2019,"fuel_type":"Diesel",
          "transmission":"Manual","mileage_km":120000,"color":"Grey","price_mad":149000,
          "location":"Rabat","status":"available","photo_url":"https://example.test/a.jpg",
          "rating":4,"special_offer":true,"offer_price_mad":139000,"offer_note":"This week"}]
    """.trimIndent()

    @Test
    fun `a full row parses`() {
        val v = VehicleJson.decode(full).single()

        assertEquals("v1", v.id)
        assertEquals("Dacia Duster 2019", v.title)
        assertEquals(120000, v.mileageKm)
        assertTrue(v.specialOffer)
        assertTrue(v.isAvailable)
    }

    /** A SQL null arrives as JSON null, and org.json will happily hand back the string "null". */
    @Test
    fun `nulls stay null instead of becoming the word null`() {
        val raw = """[{"id":"v2","make":"Kia","model":"Sportage","year":null,"color":null,
                      "fuel_type":null,"price_mad":null,"photo_url":null,"status":"available"}]"""

        val v = VehicleJson.decode(raw).single()

        assertNull(v.year)
        assertNull(v.color)
        assertNull(v.fuelType)
        assertNull(v.priceMad)
        assertNull(v.photoUrl)
        assertEquals("Kia Sportage", v.title)
        assertEquals("Price on request", v.displayPrice)
    }

    @Test
    fun `a row with no id is skipped and the rest survive`() {
        val raw = """[{"make":"No","model":"Id"},{"id":"v3","make":"VW","model":"Golf"}]"""

        val decoded = VehicleJson.decode(raw)

        assertEquals(1, decoded.size)
        assertEquals("VW Golf", decoded.single().title)
    }

    @Test
    fun `nothing usable reads as empty rather than throwing`() {
        listOf(null, "", "   ", "not json", "{}", "[").forEach {
            assertTrue("input: $it", VehicleJson.decode(it).isEmpty())
        }
    }

    @Test
    fun `the cache round trips through the same decoder the API uses`() {
        val original = VehicleJson.decode(full)
        assertEquals(original, VehicleJson.decode(VehicleJson.encode(original)))
    }

    @Test
    fun `an offer price replaces the list price and the old one is struck through`() {
        val v = VehicleJson.decode(full).single()

        assertEquals("139 000 DH", v.displayPrice)
        assertEquals("149 000 DH", v.wasPrice)
    }

    /** A "special offer" that is not cheaper should not claim a saving that is not there. */
    @Test
    fun `an offer that saves nothing shows no struck-through price`() {
        val v = Vehicle(
            id = "v4", make = "Kia", model = "Sportage",
            priceMad = 244000.0, specialOffer = true, offerPriceMad = 244000.0
        )

        assertNull(v.wasPrice)
    }

    @Test
    fun `an offer with no offer price falls back to the list price`() {
        val v = Vehicle(id = "v5", make = "VW", model = "Golf", priceMad = 198000.0, specialOffer = true)

        assertEquals("198 000 DH", v.displayPrice)
        assertNull(v.wasPrice)
    }

    @Test
    fun `a sold vehicle is not available`() {
        val raw = """[{"id":"v6","make":"Kia","model":"Rio","status":"sold"}]"""
        assertFalse(VehicleJson.decode(raw).single().isAvailable)
    }

    @Test
    fun `the share text carries the specs and the offer, not just a name`() {
        val text = VehicleJson.decode(full).single().toShareText()

        assertTrue(text, text.contains("Dacia Duster 2019"))
        assertTrue(text, text.contains("120 000 km"))
        assertTrue(text, text.contains("Diesel"))
        assertTrue(text, text.contains("139 000 DH"))
        assertTrue(text, text.contains("This week"))
        assertTrue(text, text.contains("Rabat"))
    }

    @Test
    fun `a spec line falls back to the colour when nothing mechanical is filled in`() {
        val v = Vehicle(id = "v7", make = "Kia", model = "Rio", color = "White")
        assertEquals("White", v.specLine)
    }

    /**
     * Real rows from the live table. Fields are typed in by hand, so a dash is how people write
     * "I do not know" - and read literally it puts a lone em dash on the card where the specs go.
     */
    @Test
    fun `hand-entered placeholders count as missing, not as values`() {
        val raw = """[{"id":"v8","make":"Land Rover","model":"Range Rover Sport","color":"\u2014",
                      "transmission":"-","fuel_type":"N/A","location":"none","status":"available"}]"""

        val v = VehicleJson.decode(raw).single()

        assertNull(v.color)
        assertNull(v.transmission)
        assertNull(v.fuelType)
        assertNull(v.location)
        assertEquals("", v.specLine)
    }

    @Test
    fun `a model recorded as a parenthetical note is left off the title`() {
        val raw = """[{"id":"v9","make":"Volvo","model":"(model unspecified)","year":2013,
                      "status":"sold"}]"""

        assertEquals("Volvo 2013", VehicleJson.decode(raw).single().title)
    }

    @Test
    fun `a real model in brackets is not confused with a placeholder`() {
        // Only a leading bracket is treated as a note; a bracket later in the name is a trim level.
        val raw = """[{"id":"v10","make":"BMW","model":"1 Series Coupe (E82)","status":"available"}]"""

        assertEquals("BMW 1 Series Coupe (E82)", VehicleJson.decode(raw).single().title)
    }
}

class VehicleFormatTest {

    @Test
    fun `prices are grouped in threes the way they are written locally`() {
        assertEquals("149 000", VehicleFormat.money(149_000.0))
        assertEquals("1 250 000", VehicleFormat.money(1_250_000.0))
        assertEquals("999", VehicleFormat.money(999.0))
        assertEquals("0", VehicleFormat.money(0.0))
    }

    @Test
    fun `a negative number keeps its sign outside the grouping`() {
        assertEquals("-1 500", VehicleFormat.money(-1500.0))
    }

    @Test
    fun `the decimal part is dropped rather than shown as pennies`() {
        assertEquals("149 000", VehicleFormat.money(149_000.49))
    }
}

class VehiclesApiTest {

    @Test
    fun `an unconfigured build is refused before any request is made`() {
        assertFalse(SupabaseConfig.NONE.isConfigured)
        assertFalse(SupabaseConfig("https://x.test", "").isConfigured)
        assertFalse(SupabaseConfig("", "key").isConfigured)
        assertTrue(SupabaseConfig("https://x.test", "key").isConfigured)
    }

    @Test
    fun `http failures are described in words a person can act on`() {
        assertEquals("The inventory key was refused", VehiclesApi.describe(401))
        assertEquals("The inventory key was refused", VehiclesApi.describe(403))
        assertEquals("The vehicles table was not found", VehiclesApi.describe(404))
        assertEquals("The inventory server is having trouble", VehiclesApi.describe(503))
        assertTrue(VehiclesApi.describe(418).contains("418"))
    }
}
