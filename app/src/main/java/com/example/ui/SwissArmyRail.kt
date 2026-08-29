package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DeviceProfile
import com.example.data.EventSource
import com.example.data.LogEventBus
import com.example.receiver.AlarmPingReceiver
import com.example.service.KeepAliveForegroundService
import kotlin.math.roundToInt

/**
 * A single tool on the rail. [primary] entries stay visible while the rail is collapsed.
 */
private data class RailAction(
    val label: String,
    val hint: String,
    val icon: ImageVector,
    val tint: Color,
    val primary: Boolean = false,
    val highlighted: Boolean = false,
    val active: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Sticky Swiss-army rail docked to the side of the screen. It floats above whatever page is
 * showing, survives scrolling, can be dragged up and down and flipped to the other edge.
 * The top slot always opens the Last Call Info page.
 */
@Composable
fun SwissArmyRail(
    onOpenLastCall: () -> Unit,
    onQuickArm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var expanded by rememberSaveable { mutableStateOf(false) }
    var dockRight by rememberSaveable { mutableStateOf(true) }
    var offsetY by rememberSaveable { mutableStateOf(0f) }
    var railHeightPx by remember { mutableFloatStateOf(0f) }

    val isFgsRunning by KeepAliveForegroundService.isRunning.collectAsStateWithLifecycle()
    val isWakeLockHeld by KeepAliveForegroundService.isWakeLockHeld.collectAsStateWithLifecycle()

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isBatteryIgnored by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { hasOverlayPermission = Settings.canDrawOverlays(context) }

    val batteryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { isBatteryIgnored = isIgnoringBatteryOptimizations(context) }

    LaunchedEffect(expanded) {
        if (expanded) {
            hasOverlayPermission = Settings.canDrawOverlays(context)
            isBatteryIgnored = isIgnoringBatteryOptimizations(context)
        }
    }

    val actions = listOf(
        RailAction(
            label = "Last Call Info",
            hint = "Number, duration, SIM & ROM profile",
            icon = Icons.Default.History,
            tint = Color(0xFF38BDF8),
            primary = true,
            highlighted = true,
            onClick = onOpenLastCall
        ),
        RailAction(
            label = "Arm everything",
            hint = "Grants what is missing, then starts the engine",
            icon = Icons.Default.RocketLaunch,
            tint = Color(0xFF34D399),
            primary = true,
            active = isFgsRunning && hasOverlayPermission && isBatteryIgnored,
            onClick = onQuickArm
        ),
        RailAction(
            label = "Direct popup",
            hint = "Service intent, bypasses BroadcastQueue",
            icon = Icons.Default.Bolt,
            tint = Color(0xFF7DD3FC),
            primary = true,
            onClick = { KeepAliveForegroundService.showOverlayDirect(context, "separate") }
        ),
        RailAction(
            label = "Simulate call end",
            hint = "Runs the OFFHOOK to IDLE trigger flow",
            icon = Icons.Default.PhoneInTalk,
            tint = Color(0xFFFBBF24),
            primary = true,
            onClick = { KeepAliveForegroundService.simulateCall(context) }
        ),
        RailAction(
            label = if (isFgsRunning) "Keep-alive ON" else "Keep-alive OFF",
            hint = "Foreground service + dynamic receiver",
            icon = Icons.Default.PowerSettingsNew,
            tint = if (isFgsRunning) Color(0xFF34D399) else Color(0xFF94A3B8),
            primary = true,
            active = isFgsRunning,
            onClick = {
                if (isFgsRunning) KeepAliveForegroundService.stop(context)
                else KeepAliveForegroundService.start(context)
            }
        ),
        RailAction(
            label = if (isWakeLockHeld) "Wake lock held" else "Wake lock off",
            hint = "PARTIAL_WAKE_LOCK anti-freeze",
            icon = Icons.Default.FlashOn,
            tint = if (isWakeLockHeld) Color(0xFFFBBF24) else Color(0xFF94A3B8),
            active = isWakeLockHeld,
            onClick = { KeepAliveForegroundService.toggleWakeLock(context, !isWakeLockHeld) }
        ),
        RailAction(
            label = "Dynamic broadcast",
            hint = "Runtime receiver inside the live service",
            icon = Icons.Default.Layers,
            tint = Color(0xFF6EE7B7),
            onClick = {
                val intent = Intent(KeepAliveForegroundService.ACTION_DYNAMIC_POPUP).apply {
                    setPackage(context.packageName)
                    putExtra(KeepAliveForegroundService.EXTRA_VARIANT, "separate")
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                }
                context.sendBroadcast(intent)
            }
        ),
        RailAction(
            label = "5s alarm pulse",
            hint = "setExactAndAllowWhileIdle wakeup test",
            icon = Icons.Default.Alarm,
            tint = Color(0xFFC4B5FD),
            onClick = {
                AlarmPingReceiver.scheduleExactPing(context, 5, showOverlay = true)
                Toast.makeText(
                    context,
                    "Alarm in 5s — press Home to test the background wakeup",
                    Toast.LENGTH_LONG
                ).show()
            }
        ),
        RailAction(
            label = if (hasOverlayPermission) "Overlay granted" else "Grant overlay",
            hint = "SYSTEM_ALERT_WINDOW for the floating card",
            icon = Icons.Default.Security,
            tint = if (hasOverlayPermission) Color(0xFF34D399) else Color(0xFFF87171),
            active = hasOverlayPermission,
            onClick = {
                overlayPermissionLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        ),
        RailAction(
            label = if (isBatteryIgnored) "Battery exempt" else "Battery optimized",
            hint = "Doze / power-saving exemption",
            icon = Icons.Default.BatteryChargingFull,
            tint = if (isBatteryIgnored) Color(0xFF34D399) else Color(0xFFF87171),
            active = isBatteryIgnored,
            onClick = {
                batteryPermissionLauncher.launch(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        ),
        RailAction(
            label = "Add shade tile",
            hint = "Toggle the engine from quick settings",
            icon = Icons.Default.Dashboard,
            tint = Color(0xFF7DD3FC),
            onClick = { QuickTiles.requestAddEngineTile(context) }
        ),
        RailAction(
            label = "Copy snapshot",
            hint = "Device, exemptions and recent events",
            icon = Icons.Default.ContentCopy,
            tint = Color(0xFF94A3B8),
            onClick = { copyDiagnosticSnapshot(context, isFgsRunning, isWakeLockHeld) }
        ),
        RailAction(
            label = "Clear event log",
            hint = "Empties the live IPC stream",
            icon = Icons.Default.Delete,
            tint = Color(0xFFF87171),
            onClick = {
                LogEventBus.clear()
                Toast.makeText(context, "Event log cleared", Toast.LENGTH_SHORT).show()
            }
        )
    )

    val visibleActions = if (expanded) actions else actions.filter { it.primary }
    val railWidth by animateDpAsState(
        targetValue = if (expanded) 226.dp else 56.dp,
        animationSpec = tween(durationMillis = 180),
        label = "railWidth"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val maxOffset = ((containerHeightPx - railHeightPx) / 2f).coerceAtLeast(0f)

        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                bottomStart = 18.dp,
                topEnd = 18.dp,
                bottomEnd = 18.dp
            ),
            color = Color(0xE60F172A),
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(if (dockRight) Alignment.CenterEnd else Alignment.CenterStart)
                .offset { IntOffset(0, offsetY.roundToInt()) }
                .padding(horizontal = 6.dp)
                .width(railWidth)
                .onSizeChanged { railHeightPx = it.height.toFloat() }
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 6.dp, horizontal = 6.dp)
                    .heightIn(max = 460.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RailHandle(
                    expanded = expanded,
                    dockRight = dockRight,
                    onToggleExpanded = { expanded = !expanded },
                    onFlipSide = { dockRight = !dockRight },
                    onDrag = { delta ->
                        offsetY = (offsetY + delta).coerceIn(-maxOffset, maxOffset)
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    visibleActions.forEachIndexed { index, action ->
                        RailButton(action = action, expanded = expanded)
                        if (index == 0) {
                            Spacer(
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFF1E293B))
                            )
                        }
                    }
                }

                if (!expanded) {
                    Text(
                        "•••",
                        color = Color(0xFF475569),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clickable { expanded = true }
                            .padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RailHandle(
    expanded: Boolean,
    dockRight: Boolean,
    onToggleExpanded: () -> Unit,
    onFlipSide: () -> Unit,
    onDrag: (Float) -> Unit
) {
    val toggle: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onToggleExpanded() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    expanded && dockRight -> Icons.Default.ChevronRight
                    expanded -> Icons.Default.ChevronLeft
                    dockRight -> Icons.Default.ChevronLeft
                    else -> Icons.Default.ChevronRight
                },
                contentDescription = if (expanded) "Collapse toolbar" else "Expand toolbar",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(18.dp)
            )
        }
    }

    val grip: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag toolbar up or down",
                tint = Color(0xFF475569),
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (expanded) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            toggle()
            grip()
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onFlipSide() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Move toolbar to the other side",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    } else {
        // Collapsed the rail is only wide enough for one control per line.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            grip()
            toggle()
        }
    }
}

@Composable
private fun RailButton(action: RailAction, expanded: Boolean) {
    val background = when {
        action.highlighted -> Color(0xFF0369A1)
        action.active -> Color(0xFF14342B)
        else -> Color(0xFF16213A)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable { action.onClick() }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.label,
            tint = if (action.highlighted) Color.White else action.tint,
            modifier = Modifier.size(20.dp)
        )
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    action.label,
                    color = if (action.highlighted) Color.White else Color(0xFFE2E8F0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    action.hint,
                    color = if (action.highlighted) Color(0xFFBAE6FD) else Color(0xFF7A8CA6),
                    fontSize = 9.5.sp,
                    maxLines = 2
                )
            }
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
    return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
}

private fun copyDiagnosticSnapshot(
    context: Context,
    isFgsRunning: Boolean,
    isWakeLockHeld: Boolean
) {
    val snapshot = buildString {
        appendLine("=== IPC Solution PoC snapshot ===")
        DeviceProfile.hardwareSummary().forEach { appendLine("${it.label}: ${it.value}") }
        DeviceProfile.restrictionSummary(context).forEach { appendLine("${it.label}: ${it.value}") }
        appendLine("Keep-alive service: ${if (isFgsRunning) "running" else "stopped"}")
        appendLine("Wake lock: ${if (isWakeLockHeld) "held" else "released"}")
        appendLine()
        appendLine("--- recent events ---")
        LogEventBus.logs.value.take(20).forEach { event ->
            appendLine("${event.formattedTime} [${event.source.displayName}] ${event.action} — ${event.details}")
        }
    }

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("IPC diagnostics", snapshot))
    Toast.makeText(context, "Diagnostic snapshot copied", Toast.LENGTH_SHORT).show()
    LogEventBus.log(
        source = EventSource.SYSTEM_DIAGNOSTIC,
        action = "Snapshot Copied",
        details = "Swiss-army rail copied a device + event snapshot to the clipboard"
    )
}
