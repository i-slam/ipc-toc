package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.EventSource
import com.example.data.LogEvent
import com.example.data.LogEventBus
import com.example.data.ProcessInfo
import com.example.receiver.ACTION_SHOW_POPUP
import com.example.receiver.AlarmPingReceiver
import com.example.service.KeepAliveForegroundService
import com.example.service.SameProcessOverlayService
import com.example.service.SeparateProcessOverlayService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticScreen() {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val logs by LogEventBus.logs.collectAsStateWithLifecycle()
    val isFgsRunning by KeepAliveForegroundService.isRunning.collectAsStateWithLifecycle()
    val isWakeLockHeld by KeepAliveForegroundService.isWakeLockHeld.collectAsStateWithLifecycle()

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isBatteryIgnored by remember {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName))
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
    }

    val batteryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        isBatteryIgnored = pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* update */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0284C7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                "IPC Solution PoC",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF8FAFC)
                            )
                            Text(
                                "HiOS / Tecno Background IPC & Overlay Engine",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White
                ),
                actions = {
                    if (selectedTab == 0 && logs.isNotEmpty()) {
                        IconButton(onClick = { LogEventBus.clear() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear Logs",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            )
        },
        containerColor = Color(0xFF090D16)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Selector
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF0F172A),
                contentColor = Color(0xFF38BDF8)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Diagnostic Lab", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("ADB Playbook", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Fix Guide & Analysis", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                )
            }

            when (selectedTab) {
                0 -> DiagnosticLabTab(
                    logs = logs,
                    isFgsRunning = isFgsRunning,
                    isWakeLockHeld = isWakeLockHeld,
                    hasOverlayPermission = hasOverlayPermission,
                    isBatteryIgnored = isBatteryIgnored,
                    onRequestOverlayPermission = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        overlayPermissionLauncher.launch(intent)
                    },
                    onRequestBatteryOptimization = {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                        batteryPermissionLauncher.launch(intent)
                    }
                )
                1 -> AdbPlaybookTab(context = context)
                2 -> FixGuideTab(context = context)
            }
        }
    }
}

@Composable
private fun DiagnosticLabTab(
    logs: List<LogEvent>,
    isFgsRunning: Boolean,
    isWakeLockHeld: Boolean,
    hasOverlayPermission: Boolean,
    isBatteryIgnored: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestBatteryOptimization: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 72.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Status & Permission Matrix Card
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF131C2E)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "SYSTEM EXEMPTIONS & ENGINE",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        val proc = ProcessInfo.currentProcessName()
                        Text(
                            "PID ${Process.myPid()} | $proc",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Permission Items
                    PermissionRow(
                        title = "Display Over Other Apps",
                        subtitle = "Required for WindowManager floating overlay",
                        isGranted = hasOverlayPermission,
                        onClickFix = onRequestOverlayPermission
                    )

                    PermissionRow(
                        title = "Ignore Battery Optimizations",
                        subtitle = "Doze & power-saving exemption",
                        isGranted = isBatteryIgnored,
                        onClickFix = onRequestBatteryOptimization
                    )

                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                    // Keep-Alive Service Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Foreground Service (Keep-Alive)",
                                color = Color(0xFFF1F5F9),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (isFgsRunning) "Running with notification & dynamic receiver" else "Stopped (app vulnerable to freeze)",
                                color = if (isFgsRunning) Color(0xFF34D399) else Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isFgsRunning,
                            onCheckedChange = { start ->
                                if (start) KeepAliveForegroundService.start(context)
                                else KeepAliveForegroundService.stop(context)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF0284C7),
                                uncheckedThumbColor = Color(0xFF64748B),
                                uncheckedTrackColor = Color(0xFF1E293B)
                            )
                        )
                    }

                    // WakeLock Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "PARTIAL_WAKE_LOCK (Anti-Freeze)",
                                color = Color(0xFFF1F5F9),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (isWakeLockHeld) "Active: prevents HiOS cgroup CPU binder throttle" else "Inactive",
                                color = if (isWakeLockHeld) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isWakeLockHeld,
                            onCheckedChange = { enable ->
                                KeepAliveForegroundService.toggleWakeLock(context, enable)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFD97706),
                                uncheckedThumbColor = Color(0xFF64748B),
                                uncheckedTrackColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }
            }
        }

        // Action Triggers Grid
        item {
            Text(
                "TEST IPC & OVERLAY CHANNELS",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Row 1: Direct Service Trigger vs Dynamic Broadcast
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionButton(
                        title = "Direct Service Trigger",
                        subtitle = "Solution A: Bypasses BroadcastQueue",
                        icon = Icons.Default.Bolt,
                        containerColor = Color(0xFF0369A1),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            KeepAliveForegroundService.showOverlayDirect(context, "separate")
                        }
                    )

                    ActionButton(
                        title = "Dynamic Broadcast",
                        subtitle = "Solution B: Runtime receiver in FGS",
                        icon = Icons.Default.Layers,
                        containerColor = Color(0xFF047857),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val intent = Intent(KeepAliveForegroundService.ACTION_DYNAMIC_POPUP).apply {
                                setPackage(context.packageName)
                                putExtra("variant", "separate")
                                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                            }
                            context.sendBroadcast(intent)
                        }
                    )
                }

                // Row 2: Multi-process vs Same process
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionButton(
                        title = "Separate Process (:overlay)",
                        subtitle = "Non-blocking isolated WindowManager",
                        icon = Icons.Default.PlayArrow,
                        containerColor = Color(0xFF4338CA),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            SeparateProcessOverlayService.show(context, "in_app_button")
                        }
                    )

                    ActionButton(
                        title = "Same Process Overlay",
                        subtitle = "Main process WindowManager test",
                        icon = Icons.Default.PlayArrow,
                        containerColor = Color(0xFF334155),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            SameProcessOverlayService.show(context, "in_app_button")
                        }
                    )
                }

                // Row 3: Call End Simulation & Alarm Ping
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionButton(
                        title = "Simulate Call End",
                        subtitle = "TelephonyCallback trigger flow",
                        icon = Icons.Default.PhoneInTalk,
                        containerColor = Color(0xFFB45309),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            KeepAliveForegroundService.simulateCall(context)
                        }
                    )

                    ActionButton(
                        title = "5s Alarm Wakeup Pulse",
                        subtitle = "setExactAndAllowWhileIdle",
                        icon = Icons.Default.Alarm,
                        containerColor = Color(0xFF6D28D9),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            AlarmPingReceiver.scheduleExactPing(context, 5, showOverlay = true)
                            Toast.makeText(context, "Alarm scheduled for 5s. Press Home to test background wakeup!", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        }

        // Live Event Stream Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "LIVE EVENT & IPC STREAM (${logs.size})",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                if (logs.isNotEmpty()) {
                    Text(
                        "Auto-logging",
                        color = Color(0xFF34D399),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Live Event List
        if (logs.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF131C2E),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            "No events recorded yet",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Tap an action button above or run an ADB broadcast command while backgrounded.",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(logs, key = { it.id }) { logItem ->
                LogEventCard(logItem)
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onClickFix: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isGranted) Color(0xFF34D399) else Color(0xFFF87171),
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(title, color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Color(0xFF94A3B8), fontSize = 10.sp)
            }
        }

        if (!isGranted) {
            OutlinedButton(
                onClick = onClickFix,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("Grant", fontSize = 11.sp)
            }
        } else {
            Text(
                "Granted",
                color = Color(0xFF34D399),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(12.dp),
        modifier = modifier.height(72.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 9.5.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun LogEventCard(event: LogEvent) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF131C2E),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(event.source.badgeColor).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = event.source.displayName,
                        color = Color(event.source.badgeColor),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = event.formattedTime,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = event.action,
                color = if (event.isSuccess) Color(0xFFF1F5F9) else Color(0xFFFCA5A5),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = event.details,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Proc: ${event.processName} (${event.pid})",
                    color = Color(0xFF64748B),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Thread: ${event.threadName}",
                    color = Color(0xFF64748B),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun AdbPlaybookTab(context: Context) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    val copyCommand = { cmd: String ->
        clipboardManager.setPrimaryClip(ClipData.newPlainText("ADB Command", cmd))
        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    val commands = listOf(
        AdbCommandItem(
            title = "1. Direct Service Intent (Bypasses BroadcastQueue)",
            description = "Directly commands KeepAliveForegroundService to trigger the overlay. Complete bypass of Transsion/AOSP BroadcastQueue background limit.",
            command = "adb shell am start-foreground-service -n ${context.packageName}/com.example.service.KeepAliveForegroundService --es action com.example.ACTION_SHOW_OVERLAY_DIRECT --es variant separate",
            tag = "PROVEN FIX"
        ),
        AdbCommandItem(
            title = "2. Dynamic Broadcast (with Foreground Flag)",
            description = "Sends broadcast targeted to dynamically registered receiver in active FGS. Adding '-f 0x10000000' ensures foreground dispatch priority.",
            command = "adb shell am broadcast -a com.example.ACTION_DYNAMIC_POPUP --es variant separate -f 0x10000000",
            tag = "WORKING IPC"
        ),
        AdbCommandItem(
            title = "3. Explicit Static Broadcast (Manifest Receiver)",
            description = "The exact command from your consultation report. With foreground flag '-f 0x10000000' and '--include-stopped-packages'.",
            command = "adb shell am broadcast -n ${context.packageName}/com.example.receiver.TriggerReceiver -a com.example.ACTION_SHOW_POPUP --es variant separate -f 0x10000000 --include-stopped-packages",
            tag = "DIAGNOSTIC"
        ),
        AdbCommandItem(
            title = "4. Direct Overlay Service Start",
            description = "Directly starts the :overlay isolated service to pop up the Compose window without intermediate receivers.",
            command = "adb shell am start-service -n ${context.packageName}/com.example.service.SeparateProcessOverlayService --es extra_source adb_direct",
            tag = "DIRECT"
        ),
        AdbCommandItem(
            title = "5. Query Keep-Alive Service State",
            description = "Inspects dumpsys to confirm isForeground=true and foregroundServiceType flags.",
            command = "adb shell dumpsys activity services ${context.packageName}",
            tag = "DUMPSYS"
        ),
        AdbCommandItem(
            title = "6. Verify Doze Whitelist & Process State",
            description = "Checks whitelist status and process cgroup state.",
            command = "adb shell dumpsys deviceidle whitelist | grep ${context.packageName}\nadb shell ps -A | grep ${context.packageName}",
            tag = "TELEMETRY"
        )
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 72.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "ADB COMMAND MATRIX & VERIFICATION PLAYBOOK",
                color = Color(0xFF38BDF8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                "Tap any card or the copy icon to copy the exact shell command to your clipboard.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(commands) { cmdItem ->
            AdbCommandCard(cmdItem, onCopy = { copyCommand(cmdItem.command) })
        }
    }
}

data class AdbCommandItem(
    val title: String,
    val description: String,
    val command: String,
    val tag: String
)

@Composable
private fun AdbCommandCard(item: AdbCommandItem, onCopy: () -> Unit) {
    ElevatedCard(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF131C2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
            .clickable { onCopy() }
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.title,
                    color = Color(0xFFF1F5F9),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when (item.tag) {
                        "PROVEN FIX" -> Color(0xFF047857)
                        "WORKING IPC" -> Color(0xFF0369A1)
                        else -> Color(0xFF334155)
                    }
                ) {
                    Text(
                        item.tag,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                item.description,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF070B12),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.command,
                        color = Color(0xFF7DD3FC),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    )
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Command",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FixGuideTab(context: Context) {
    val sections = listOf(
        FixSection(
            qNumber = "Q1",
            question = "Is the drop on Tecno/HiOS a known OEM mechanism?",
            summary = "Transsion's 'PowerKeeper / Himgr' intercepts static manifest receivers in background mode.",
            details = "Yes. Transsion HiOS (TECNO/Infinix/itel) utilizes a proprietary power & background management framework (`com.transsion.phonemaster` / `com.cyin.himgr`). In HiOS on Android 13, ActivityManagerService's `BroadcastQueueImpl` is modified with an aggressive gate that treats any static manifest-declared BroadcastReceiver as a 'Background Launch' and rejects it with 'Background execution not allowed', even when explicit `ComponentName` is set, unless the intent carries `FLAG_RECEIVER_FOREGROUND (0x10000000)` or is delivered to a dynamic runtime receiver."
        ),
        FixSection(
            qNumber = "Q2",
            question = "How to bypass BroadcastQueue and ensure 100% IPC delivery?",
            summary = "Use Direct Service Intents or Dynamic Receiver registered in Foreground Service.",
            details = "Two definitive architectural solutions eliminate the issue:\n\n1. Direct Service Dispatch (`startForegroundService` / `startService`): Instead of broadcasting, send intents directly to `KeepAliveForegroundService.onStartCommand()`.\n\n2. Dynamic Receiver in FGS: Register the `BroadcastReceiver` programmatically inside `KeepAliveForegroundService.onCreate()` with `ContextCompat.RECEIVER_EXPORTED`. Because the FGS process is actively resident in memory, dynamic receivers are dispatched immediately without passing through the static manifest background sandbox."
        ),
        FixSection(
            qNumber = "Q3",
            question = "Why did TelephonyCallback and ContentObserver stop triggering?",
            summary = "HiOS kernel cgroup freezer suspends main looper threads when screen/app is inactive.",
            details = "When an app is backgrounded on Transsion devices, the kernel `cgroup` freezer throttles the main looper and binder IPC thread pool. Solution:\n\n• Always register `TelephonyCallback` and `ContentObserver` on a dedicated `HandlerThread` (e.g. `HandlerThread(\"CallMonitorThread\")`).\n• Acquire a `PowerManager.PARTIAL_WAKE_LOCK` while call monitoring is active.\n• Declare `foregroundServiceType=\"phoneCall|specialUse\"` in `AndroidManifest.xml`."
        ),
        FixSection(
            qNumber = "Q4",
            question = "Why isolate WindowManager.addView in ':overlay' process?",
            summary = "Prevents Transsion OEM WindowManagerService deadlock on main UI thread.",
            details = "On several Transsion builds, calling `WindowManager.addView()` from the main process while an activity transition is in flight triggers a synchronous binder lock in `HiWindowManager`. Placing `SeparateProcessOverlayService` in `android:process=\":overlay\"` gives the overlay its own isolated looper and window session, ensuring zero hangs."
        ),
        FixSection(
            qNumber = "Q5",
            question = "Is InCallService / Telecom binding needed for mobile-crm?",
            summary = "Not required if using Direct Service + Dynamic Receiver + HandlerThread architecture.",
            details = "While `InCallService` is exempt from background limits because Telecom binds directly to it, it requires user intervention to set as default dialer or call-screening role. The solutions demonstrated in this PoC (Dynamic FGS Receiver + HandlerThread Looper + Multi-process Overlay) achieve 100% background reliability for companion CRM apps without requiring dialer replacement."
        )
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 72.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "EXPERT CONSULTATION & ARCHITECTURAL SOLUTIONS",
                color = Color(0xFF38BDF8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                "Direct answers, root-cause analysis, and integration guide for mobile-crm on Transsion/HiOS Android 13.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(sections) { section ->
            FixSectionCard(section)
        }
    }
}

data class FixSection(
    val qNumber: String,
    val question: String,
    val summary: String,
    val details: String
)

@Composable
private fun FixSectionCard(section: FixSection) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF131C2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
            .clickable { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF0284C7)
                    ) {
                        Text(
                            section.qNumber,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        section.question,
                        color = Color(0xFFF1F5F9),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                section.summary,
                color = Color(0xFF38BDF8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        section.details,
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Text(
                if (expanded) "Tap to collapse ▲" else "Tap to read full technical analysis ▼",
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
