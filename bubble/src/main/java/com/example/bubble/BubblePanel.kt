package com.example.bubble

import android.content.Context
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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telephony.CallLogReader
import com.example.telephony.CallRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class BubbleAction {
    WHATSAPP_LAST_CALL,
    OPEN_CALL_LOG,
    COPY_LAST_CALL,
    OPEN_DIALER,
    OPEN_DIAGNOSTICS,
    HIDE
}

/** Reads the most recent call for the bubble, off the main thread at the call site. */
object LastCall {
    fun read(context: Context): CallRecord? = CallLogReader.readLast(context)
}

/**
 * The bubble and the small panel it opens into. Collapsed it is one circle on the screen edge;
 * expanded it shows the last call inline, because a two-line answer is the thing worth having
 * without opening an app at all.
 */
@Composable
fun BubblePanel(
    onAction: (BubbleAction, CallRecord?) -> Unit,
    onDragVertically: (Float) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    if (!expanded) {
        Box(
            modifier = Modifier
                .padding(4.dp)
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xE60F172A))
                .border(1.dp, Color(0x66FFFFFF), CircleShape)
                .pointerInput(Unit) { detectTapGestures { expanded = true } }
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
                contentDescription = "Open the floating tools",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        return
    }

    ExpandedPanel(
        onCollapse = { expanded = false },
        onAction = { action, record ->
            expanded = false
            onAction(action, record)
        },
        onDragVertically = onDragVertically
    )
}

@Composable
private fun ExpandedPanel(
    onCollapse: () -> Unit,
    onAction: (BubbleAction, CallRecord?) -> Unit,
    onDragVertically: (Float) -> Unit
) {
    val context = LocalContext.current
    var lastCall by remember { mutableStateOf<CallRecord?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        lastCall = withContext(Dispatchers.IO) { LastCall.read(context) }
        loaded = true
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xF20F172A),
        shadowElevation = 12.dp,
        modifier = Modifier
            .padding(4.dp)
            .width(226.dp)
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.width(210.dp)
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
                    "Last call",
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

            LastCallSummary(record = lastCall, loaded = loaded)

            PanelButton(
                label = "WhatsApp this number",
                icon = Icons.Default.Chat,
                tint = Color(0xFF25D366),
                background = Color(0xFF11351F)
            ) {
                onAction(BubbleAction.WHATSAPP_LAST_CALL, lastCall)
            }
            PanelButton(
                label = "All calls + WhatsApp",
                icon = Icons.AutoMirrored.Filled.FormatListBulleted
            ) {
                onAction(BubbleAction.OPEN_CALL_LOG, lastCall)
            }
            PanelButton("Copy details", Icons.Default.ContentCopy) {
                onAction(BubbleAction.COPY_LAST_CALL, lastCall)
            }
            PanelButton("Open dialer", Icons.Default.Dialpad) {
                onAction(BubbleAction.OPEN_DIALER, lastCall)
            }
            PanelButton("Open diagnostics app", Icons.Default.OpenInNew) {
                onAction(BubbleAction.OPEN_DIAGNOSTICS, lastCall)
            }
            PanelButton("Hide this button", Icons.Default.Close, Color(0xFFF87171)) {
                onAction(BubbleAction.HIDE, lastCall)
            }
        }
    }
}

@Composable
private fun LastCallSummary(record: CallRecord?, loaded: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF16213A),
        modifier = Modifier.width(210.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            when {
                !loaded -> Text(
                    "Reading call log…",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )

                record == null -> Text(
                    "No call log access yet - open the app once to grant it",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )

                else -> {
                    Text(
                        record.displayName,
                        color = Color(0xFFF8FAFC),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        "${record.direction.label} · ${record.formattedDuration}",
                        color = Color(record.direction.badgeColor),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        record.relativeTime(),
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelButton(
    label: String,
    icon: ImageVector,
    tint: Color = Color(0xFF7DD3FC),
    background: Color = Color(0xFF16213A),
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(210.dp)
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
