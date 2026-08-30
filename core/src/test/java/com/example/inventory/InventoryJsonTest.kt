package com.example.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The store is a file the process can be killed halfway through writing, so what matters is that
 * a damaged one reads as empty rather than taking the app down on launch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 36])
class InventoryJsonTest {

    private val items = listOf(
        InventoryItem("a", "Dacia Duster 1.5 dCi", "149 000 DH", "a.jpg", "SUV", 1_700_000_000_000L),
        InventoryItem("b", "Renault Clio V", "118 500 DH", null, "", 1_700_000_001_000L)
    )

    @Test
    fun `items survive a round trip`() {
        assertEquals(items, InventoryJson.decode(InventoryJson.encode(items)))
    }

    @Test
    fun `an item with no photo keeps a null filename rather than the string null`() {
        val decoded = InventoryJson.decode(InventoryJson.encode(items)).last()
        assertNull(decoded.imageFile)
    }

    @Test
    fun `a truncated file reads as empty instead of throwing`() {
        val half = InventoryJson.encode(items).substring(0, 30)
        assertTrue(InventoryJson.decode(half).isEmpty())
    }

    @Test
    fun `nothing at all reads as empty`() {
        assertTrue(InventoryJson.decode(null).isEmpty())
        assertTrue(InventoryJson.decode("").isEmpty())
        assertTrue(InventoryJson.decode("   ").isEmpty())
        assertTrue(InventoryJson.decode("not json").isEmpty())
    }

    @Test
    fun `a row missing its id or name is skipped, the rest survive`() {
        val raw = """[{"name":"no id"},{"id":"x"},{"id":"ok","name":"Kept","price":"1 DH"}]"""

        val decoded = InventoryJson.decode(raw)

        assertEquals(1, decoded.size)
        assertEquals("Kept", decoded.single().name)
    }

    @Test
    fun `a blank price reads as price on request rather than an empty line`() {
        val item = InventoryItem("c", "Kia Sportage", "")
        assertEquals("Price on request", item.displayPrice)
        assertEquals("Kia Sportage", item.toShareLine())
    }

    @Test
    fun `a priced item shares as one line`() {
        assertEquals("Kia Sportage — 244 000 DH", InventoryItem("c", "Kia Sportage", "244 000 DH").toShareLine())
    }
}
