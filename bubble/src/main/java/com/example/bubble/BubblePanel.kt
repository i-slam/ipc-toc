package com.example.bubble

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
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
import com.example.ui.ArcItem
import com.example.ui.ArcStyle
import com.example.ui.GooeyArcMenu
import com.example.ui.theme.Crm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class BubbleAction {
    WHATSAPP_LAST_CALL,
    OPEN_CALL_LOG,
    OPEN_INVENTORY,
    COPY_LAST_CALL,
    OPEN_DIALER,
    OPEN_DIAGNOSTICS,
    HIDE
}

/** Reads the most recent call for the bubble, off the main thread at the call site. */
object LastCall {
    fun read(context: Context): CallRecord? = CallLogReader.readLast(context)
}

private const val ARC_CALL = "call"
private const val ARC_LIST = "list"
private const val ARC_INVENTORY = "inventory"
private const val ARC_DIALER = "dialer"
private const val ARC_MORE = "more"

/**
 * The bubble: a toggle on the screen edge that fans out into an arc of actions, and the panel
 * one of those actions opens.
 *
 * Tapping a blob opens its popup rather than firing straight away - the last call is worth
 * *seeing* before deciding what to send, which is the whole reason for the panel.
 */
@Composable
fun BubblePanel(
    onAction: (BubbleAction, CallRecord?) -> Unit,
    onDragVertically: (Float) -> Unit,
    arcStyle: ArcStyle = ArcStyle.WIDE,
    autoPopAt: Long = 0L
) {
    var expanded by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<String?>(null) }
    var unseenCall by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var lastCall by remember { mutableStateOf<CallRecord?>(null) }
    var loaded by remember { mutableStateOf(false) }

    // Loaded when the arc opens, not when a panel does, so the popup has its answer already.
    LaunchedEffect(expanded) {
        if (expanded && !loaded) {
            lastCall = withContext(Dispatchers.IO) { LastCall.read(context) }
            loaded = true
        }
    }

    // A call just ended: re-read the log - the record is new, so whatever was loaded is stale -
    // then open straight onto it.
    LaunchedEffect(autoPopAt) {
        if (autoPopAt <= 0L) return@LaunchedEffect
        lastCall = withContext(Dispatchers.IO) { LastCall.read(context) }
        loaded = true
        unseenCall = true
        expanded = true
        panel = ARC_CALL
    }

    val items = listOf(
        ArcItem(ARC_CALL, Icons.Default.Phone, "Last call and WhatsApp", badge = unseenCall),
        ArcItem(ARC_LIST, Icons.AutoMirrored.Filled.FormatListBulleted, "All calls"),
        ArcItem(ARC_INVENTORY, Icons.Default.DirectionsCar, "Send from inventory"),
        ArcItem(ARC_DIALER, Icons.Default.Dialpad, "Dialer"),
        ArcItem(ARC_MORE, Icons.Default.Settings, "More actions")
    )

    Box(contentAlignment = Alignment.BottomEnd) {
        AnimatedVisibility(
            visible = panel != null,
            enter = fadeIn() + scaleIn(initialScale = 0.94f),
            exit = fadeOut() + scaleOut(targetScale = 0.94f),
            modifier = Modifier.padding(bottom = 74.dp, end = 4.dp)
        ) {
            ActionPanel(
                record = lastCall,
                loaded = loaded,
                showEverything = panel == ARC_MORE,
                onClose = { panel = null },
                onAction = { action ->
                    panel = null
                    expanded = false
                    onAction(action, lastCall)
                }
            )
        }

        GooeyArcMenu(
            items = items,
            expanded = expanded,
            style = arcStyle,
            onToggle = {
                expanded = !expanded
                if (!expanded) panel = null
            },
            onItem = { item ->
                when (item.id) {
                    ARC_LIST -> {
                        expanded = false
                        onAction(BubbleAction.OPEN_CALL_LOG, lastCall)
                    }

                    ARC_INVENTORY -> {
                        expanded = false
                        onAction(BubbleAction.OPEN_INVENTORY, lastCall)
                    }

                    ARC_DIALER -> {
                        expanded = false
                        onAction(BubbleAction.OPEN_DIALER, lastCall)
                    }

                    else -> {
                        if (item.id == ARC_CALL) unseenCall = false
                        panel = if (panel == item.id) null else item.id
                    }
                }
            },
            onDragVertically = onDragVertically
        )
    }
}

@Composable
private fun ActionPanel(
    record: CallRecord?,
    loaded: Boolean,
    showEverything: Boolean,
    onClose: () -> Unit,
    onAction: (BubbleAction) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Crm.OverlaySurface,
        shadowElevation = 14.dp,
        modifier = Modifier
            .width(238.dp)
            .border(1.dp, Crm.Line, RoundedCornerShape(28.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.width(210.dp)
            ) {
                Text(
                    if (showEverything) "More actions" else "Last call",
                    color = Crm.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) { detectTapGestures { onClose() } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Crm.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            LastCallCard(record = record, loaded = loaded)

            PanelButton(
                label = "WhatsApp this number",
                icon = Icons.Default.Chat,
                tint = Crm.WhatsApp,
                background = Crm.WhatsAppInk
            ) { onAction(BubbleAction.WHATSAPP_LAST_CALL) }

            PanelButton(
                label = "Send from inventory",
                icon = Icons.Default.DirectionsCar,
                tint = Crm.Accent
            ) { onAction(BubbleAction.OPEN_INVENTORY) }

            PanelButton("All calls + WhatsApp", Icons.AutoMirrored.Filled.FormatListBulleted) {
                onAction(BubbleAction.OPEN_CALL_LOG)
            }

            if (showEverything) {
                PanelButton("Copy details", Icons.Default.ContentCopy) {
                    onAction(BubbleAction.COPY_LAST_CALL)
                }
                PanelButton("Open diagnostics app", Icons.Default.OpenInNew) {
                    onAction(BubbleAction.OPEN_DIAGNOSTICS)
                }
                PanelButton("Hide this button", Icons.Default.Close, Crm.Danger) {
                    onAction(BubbleAction.HIDE)
                }
            }
        }
    }
}

@Composable
private fun LastCallCard(record: CallRecord?, loaded: Boolean) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Crm.Surface,
        modifier = Modifier
            .width(210.dp)
            .border(1.dp, Crm.Line, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            when {
                !loaded -> Text("Reading call log…", color = Crm.TextMuted, fontSize = 11.sp)

                record == null -> Text(
                    "No call log access yet - open the app once to grant it",
                    color = Crm.TextMuted,
                    fontSize = 11.sp
                )

                else -> {
                    Text(
                        record.displayName,
                        color = Crm.Text,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        "${record.direction.label} · ${record.formattedDuration}",
                        color = Crm.Accent2,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        record.relativeTime(),
                        color = Crm.TextMuted,
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
    tint: Color = Crm.Accent2,
    background: Color = Crm.Surface2,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(210.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        Text(label, color = Crm.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
