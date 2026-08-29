package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What the floating rail can ask the host service to do. */
enum class FloatingAction {
    LAST_CALL,
    SHOW_POPUP,
    SIMULATE_CALL_END,
    ARM_EVERYTHING,
    TOGGLE_ENGINE,
    HIDE
}

/**
 * The system-wide floating rail: a bubble parked on the screen edge that lives above every app,
 * not just this one. Collapsed it is a single button; tapping opens the actions; dragging moves
 * it up and down.
 *
 * Actions are reported to the hosting service rather than performed here - an overlay window has
 * no activity to launch permission dialogs from.
 */
@Composable
fun FloatingRail(
    isEngineOn: Boolean,
    onAction: (FloatingAction) -> Unit,
    onDragVertically: (Float) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    if (!expanded) {
        FloatingBubble(
            isEngineOn = isEngineOn,
            onTap = { expanded = true },
            onDragVertically = onDragVertically
        )
    } else {
        ExpandedRail(
            isEngineOn = isEngineOn,
            onCollapse = { expanded = false },
            onAction = { action ->
                expanded = false
                onAction(action)
            },
            onDragVertically = onDragVertically
        )
    }
}

@Composable
private fun FloatingBubble(
    isEngineOn: Boolean,
    onTap: () -> Unit,
    onDragVertically: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(52.dp)
            .clip(CircleShape)
            .background(if (isEngineOn) Color(0xE6047857) else Color(0xE60F172A))
            .border(1.dp, Color(0x66FFFFFF), CircleShape)
            .pointerInput(Unit) { detectTapGestures { onTap() } }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragVertically(dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = "IPC tools",
            tint = Color.White,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun ExpandedRail(
    isEngineOn: Boolean,
    onCollapse: () -> Unit,
    onAction: (FloatingAction) -> Unit,
    onDragVertically: (Float) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xF20F172A),
        shadowElevation = 12.dp,
        modifier = Modifier
            .padding(4.dp)
            .width(210.dp)
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.width(194.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onDragVertically(dragAmount.y)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Move",
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    "IPC tools",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) { detectTapGestures { onCollapse() } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Collapse",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            FloatingRailButton(
                label = "Last Call Info",
                icon = Icons.Default.History,
                tint = Color.White,
                background = Color(0xFF0369A1)
            ) { onAction(FloatingAction.LAST_CALL) }

            FloatingRailButton(
                label = "Show popup now",
                icon = Icons.Default.Bolt,
                tint = Color(0xFF7DD3FC),
                background = Color(0xFF16213A)
            ) { onAction(FloatingAction.SHOW_POPUP) }

            FloatingRailButton(
                label = "Simulate call end",
                icon = Icons.Default.PhoneInTalk,
                tint = Color(0xFFFBBF24),
                background = Color(0xFF16213A)
            ) { onAction(FloatingAction.SIMULATE_CALL_END) }

            FloatingRailButton(
                label = "Arm everything",
                icon = Icons.Default.RocketLaunch,
                tint = Color(0xFF34D399),
                background = Color(0xFF16213A)
            ) { onAction(FloatingAction.ARM_EVERYTHING) }

            FloatingRailButton(
                label = if (isEngineOn) "Engine on" else "Engine off",
                icon = Icons.Default.PowerSettingsNew,
                tint = if (isEngineOn) Color(0xFF34D399) else Color(0xFF94A3B8),
                background = if (isEngineOn) Color(0xFF14342B) else Color(0xFF16213A)
            ) { onAction(FloatingAction.TOGGLE_ENGINE) }

            FloatingRailButton(
                label = "Hide this button",
                icon = Icons.Default.Close,
                tint = Color(0xFFF87171),
                background = Color(0xFF16213A)
            ) { onAction(FloatingAction.HIDE) }
        }
    }
}

@Composable
private fun FloatingRailButton(
    label: String,
    icon: ImageVector,
    tint: Color,
    background: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(194.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            label,
            color = Color(0xFFE2E8F0),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
