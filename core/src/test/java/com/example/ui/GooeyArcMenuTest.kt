package com.example.ui

import androidx.compose.ui.unit.IntOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The arc geometry, which is where this is easy to get wrong: screen y grows downward while the
 * angles are measured the usual way round, so a missing sign sends the whole fan off the bottom
 * of the screen instead of up into free space.
 */
class GooeyArcMenuTest {

    private val radius = 100f

    @Test
    fun `closed, every blob sits under the toggle`() {
        val offsets = arcOffsets(4, ArcStyle.WIDE, radius, progress = 0f)

        assertEquals(4, offsets.size)
        offsets.forEach {
            assertEquals(0, it.x)
            assertEquals(0, it.y)
        }
    }

    @Test
    fun `open, the whole fan is up and to the left`() {
        ArcStyle.entries.forEach { style ->
            arcOffsets(4, style, radius, progress = 1f).forEachIndexed { i, offset ->
                assertTrue("${style.name}[$i] x=${offset.x} should be left of the toggle", offset.x < 0)
                assertTrue("${style.name}[$i] y=${offset.y} should be above the toggle", offset.y < 0)
            }
        }
    }

    /**
     * The fan is centred on the up-left diagonal, so mirroring it swaps the axes: the last blob's
     * horizontal reach equals the first blob's vertical one. That holds for any sweep, and it
     * fails the moment the y inversion is dropped.
     */
    @Test
    fun `the fan is symmetric about the up-left diagonal`() {
        ArcStyle.entries.forEach { style ->
            val offsets = arcOffsets(4, style, radius, progress = 1f)
            assertClose(style.name, offsets.first().y, offsets.last().x)
            assertClose(style.name, offsets.first().x, offsets.last().y)
        }
    }

    @Test
    fun `blobs travel outward in step with the animation`() {
        val half = arcOffsets(4, ArcStyle.WIDE, radius, progress = 0.5f)
        val full = arcOffsets(4, ArcStyle.WIDE, radius, progress = 1f)

        half.forEachIndexed { i, offset ->
            assertClose("x[$i]", full[i].x / 2, offset.x)
            assertClose("y[$i]", full[i].y / 2, offset.y)
        }
    }

    @Test
    fun `a wider sweep pushes the outer blobs further apart`() {
        val tight = arcOffsets(4, ArcStyle.TIGHT, radius, progress = 1f)
        val wide = arcOffsets(4, ArcStyle.WIDE, radius, progress = 1f)

        assertTrue(
            "wide=${spread(wide)} should exceed tight=${spread(tight)}",
            spread(wide) > spread(tight)
        )
    }

    @Test
    fun `a single item sits at the centre of the sweep`() {
        val only = arcOffsets(1, ArcStyle.TIGHT, radius, progress = 1f).single()

        // 135 degrees is equal parts left and up.
        assertClose("diagonal", only.x, only.y)
    }

    @Test
    fun `no items, no offsets`() {
        assertTrue(arcOffsets(0, ArcStyle.WIDE, radius, progress = 1f).isEmpty())
    }

    /** Squared distance between the two outermost blobs. */
    private fun spread(offsets: List<IntOffset>): Int {
        val dx = offsets.first().x - offsets.last().x
        val dy = offsets.first().y - offsets.last().y
        return dx * dx + dy * dy
    }

    /** Rounding to whole pixels means exact equality is not on offer. */
    private fun assertClose(what: String, expected: Int, actual: Int, tolerance: Int = 1) {
        assertTrue(
            "$what: expected $expected ± $tolerance but was $actual",
            abs(expected - actual) <= tolerance
        )
    }
}
