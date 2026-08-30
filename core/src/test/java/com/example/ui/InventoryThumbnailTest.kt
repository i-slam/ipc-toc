package com.example.ui

import com.example.ui.inventory.sampleSizeFor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A grid of full-resolution phone photos is how a screen like this runs out of memory, so the
 * downsampling factor is worth pinning - including the degenerate inputs, where returning 0 would
 * make BitmapFactory throw rather than decode.
 */
class InventoryThumbnailTest {

    @Test
    fun `an image already small enough is not downsampled`() {
        assertEquals(1, sampleSizeFor(sourceWidth = 480, targetWidth = 480))
        assertEquals(1, sampleSizeFor(sourceWidth = 300, targetWidth = 480))
    }

    @Test
    fun `a large photo halves until it is close to the target`() {
        assertEquals(2, sampleSizeFor(sourceWidth = 960, targetWidth = 480))
        assertEquals(4, sampleSizeFor(sourceWidth = 1920, targetWidth = 480))
        assertEquals(8, sampleSizeFor(sourceWidth = 4032, targetWidth = 480))
    }

    @Test
    fun `the result is always a power of two`() {
        listOf(700, 1100, 2600, 3800, 5000).forEach { width ->
            val sample = sampleSizeFor(width, 480)
            assertEquals(
                "width=$width gave $sample",
                0,
                sample and (sample - 1)
            )
        }
    }

    @Test
    fun `a sample size is never below one`() {
        // decodeFile treats 0 or a negative as an error rather than as "do not scale".
        listOf(0, -1, -4032).forEach { assertEquals(1, sampleSizeFor(it, 480)) }
        assertEquals(1, sampleSizeFor(1920, 0))
        assertEquals(1, sampleSizeFor(1920, -1))
    }
}
