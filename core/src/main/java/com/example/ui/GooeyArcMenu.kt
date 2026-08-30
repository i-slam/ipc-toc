package com.example.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Crm
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How far the fan spreads. Offered as a setting, because which one reads better depends on where
 * the bubble has been dragged to.
 *
 * Both sweeps stay inside the quadrant between straight up and straight left. The concept draws
 * a 180-degree fan, but its bubble is inset from the corner; docked against the screen edge the
 * way this one is, anything past vertical goes off the right of the screen and anything past
 * horizontal goes off the bottom. So the wider style reaches further out rather than further
 * round.
 */
enum class ArcStyle(val label: String, val sweepDegrees: Float, val radius: Dp) {
    TIGHT("Tight", 64f, 84.dp),
    WIDE("Wide", 84f, 106.dp)
}

data class ArcItem(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val badge: Boolean = false
)

private val ToggleSize = 62.dp
private val ItemSize = 46.dp

/** Up and to the left: the bubble lives on the bottom-right edge, so that is the free space. */
private const val CENTRE_DEGREES = 135f

/**
 * The bubble and the arc of actions it fans out into.
 *
 * The blobs merge into each other on the way out - the effect the concept gets from an SVG
 * `feGaussianBlur` + `feColorMatrix` pair. There is no equivalent filter in Compose, but the same
 * two steps exist as a chained [android.graphics.RenderEffect] from API 31, so that is what this
 * uses, and below 31 the fan simply opens without the merge.
 *
 * The circles and the icons are drawn as two stacked layers: the effect blurs whatever it is
 * applied to, and blurring the icons before the alpha threshold turns them to mush.
 */
@Composable
fun GooeyArcMenu(
    items: List<ArcItem>,
    expanded: Boolean,
    style: ArcStyle,
    onToggle: () -> Unit,
    onItem: (ArcItem) -> Unit,
    onDragVertically: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "arc"
    )

    val density = LocalDensity.current
    val radiusPx = with(density) { style.radius.toPx() }

    // Collapsed, the window is only as big as the bubble - an overlay consumes touches anywhere
    // inside its own bounds, so a permanently arc-sized window would eat taps meant for the app
    // underneath.
    val boxSize = if (progress > 0f) style.radius + ToggleSize else ToggleSize

    // Not remembered: progress changes every frame while the fan animates, so caching on it
    // would allocate a new list per frame anyway.
    val offsets = arcOffsets(items.size, style, radiusPx, progress)

    Box(
        modifier = modifier.size(boxSize),
        contentAlignment = Alignment.BottomEnd
    ) {
        // Layer 1: the blobs, merged.
        Box(
            modifier = Modifier
                .size(boxSize)
                .then(gooeyLayer()),
            contentAlignment = Alignment.BottomEnd
        ) {
            items.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .offset { offsets[index] }
                        .size(ItemSize)
                        .clip(CircleShape)
                        .background(blobColour(progress))
                )
            }
            Box(
                modifier = Modifier
                    .size(ToggleSize)
                    .clip(CircleShape)
                    .background(Crm.Danger)
            )
        }

        // Layer 2: icons and hit targets, crisp.
        Box(modifier = Modifier.size(boxSize), contentAlignment = Alignment.BottomEnd) {
            items.forEachIndexed { index, item ->
                Box(
                    modifier = Modifier
                        .offset { offsets[index] }
                        .size(ItemSize)
                        .clip(CircleShape)
                        .semantics { contentDescription = item.label }
                        .pointerInput(item.id) { detectTapGestures { onItem(item) } },
                    contentAlignment = Alignment.Center
                ) {
                    if (progress > 0.4f) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = Crm.Surface2,
                            modifier = Modifier.size(20.dp)
                        )
                        if (item.badge) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-6).dp, y = 6.dp)
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(Crm.Accent)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(ToggleSize)
                    .clip(CircleShape)
                    .pointerInput(Unit) { detectTapGestures { onToggle() } }
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            onDragVertically(drag.y)
                        }
                    }
                    .semantics {
                        contentDescription = if (expanded) "Close the menu" else "Open the menu"
                    },
                contentAlignment = Alignment.Center
            ) {
                Burger(progress = progress)
            }
        }
    }
}

/** Three bars that fold into a cross, the way the concept's toggle does. */
@Composable
private fun Burger(progress: Float) {
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .offset(y = ((-7) * (1f - progress)).dp)
                .size(width = 22.dp, height = 3.dp)
                .rotate(45f * progress)
                .clip(CircleShape)
                .background(Color.White)
        )
        Box(
            modifier = Modifier
                .offset(y = (7 * (1f - progress)).dp)
                .size(width = 22.dp, height = 3.dp)
                .rotate(-45f * progress)
                .clip(CircleShape)
                .background(Color.White)
        )
        Box(
            modifier = Modifier
                .size(width = 22.dp, height = 3.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 1f - progress))
        )
    }
}

/** Grey while tucked behind the toggle, white once out - the concept's colour change on open. */
private fun blobColour(progress: Float): Color =
    if (progress > 0.5f) Color.White else Crm.Surface2

/**
 * Positions on the arc, scaled by [progress] so every blob starts underneath the toggle. That
 * overlap is what the merge needs: a filter can only fuse shapes that touch.
 */
internal fun arcOffsets(
    count: Int,
    style: ArcStyle,
    radiusPx: Float,
    progress: Float
): List<IntOffset> {
    if (count == 0) return emptyList()

    // One item has no sweep to spread over, so it belongs in the middle of the arc rather than at
    // the end where it started.
    val start = if (count == 1) CENTRE_DEGREES else CENTRE_DEGREES - style.sweepDegrees / 2f
    val step = if (count == 1) 0f else style.sweepDegrees / (count - 1)

    return List(count) { index ->
        val radians = Math.toRadians((start + step * index).toDouble())
        val distance = radiusPx * progress
        IntOffset(
            x = (cos(radians) * distance).roundToInt(),
            // Screen y grows downward, the arc is measured the usual way round, so it inverts.
            y = (-sin(radians) * distance).roundToInt()
        )
    }
}

@Composable
private fun gooeyLayer(): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return Modifier
    val blurPx = with(LocalDensity.current) { GOO_BLUR.toPx() }
    val effect = remember(blurPx) { gooEffect(blurPx) }
    return Modifier.graphicsLayer { renderEffect = effect }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun gooEffect(blurPx: Float): androidx.compose.ui.graphics.RenderEffect {
    val blur = android.graphics.RenderEffect.createBlurEffect(
        blurPx, blurPx, Shader.TileMode.DECAL
    )
    // Multiply alpha hard and subtract a constant: the blurred edges fall below zero and vanish,
    // while the overlap between two blobs sums back above the threshold and reads as one shape.
    val threshold = ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, ALPHA_GAIN, -ALPHA_BIAS
        )
    )
    return android.graphics.RenderEffect
        .createColorFilterEffect(ColorMatrixColorFilter(threshold), blur)
        .asComposeRenderEffect()
}

private val GOO_BLUR = 11.dp
private const val ALPHA_GAIN = 18f

/** The colour matrix offset column is on a 0-255 scale, unlike the SVG's 0-1. */
private const val ALPHA_BIAS = 7f * 255f
