package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DeviceProfile
import com.example.data.EventSource
import com.example.data.LogEventBus
import com.example.service.KeepAliveForegroundService
import com.example.service.SeparateProcessOverlayService
import com.example.telephony.CallDirection
import com.example.telephony.CallLogReader
import com.example.telephony.CallRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full page reached from the top slot of the Swiss-army rail: everything known about the most
 * recent call, plus the state of the call-end detection pipeline on this (Tecno/HiOS) handset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LastCallInfoScreen(onBack: () -> Unit, onOpenCallLog: () -> Unit) {
    val context = LocalContext.current

    var refreshKey by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var records by remember { mutableStateOf<List<CallRecord>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(CallLogReader.hasCallLogPermission(context)) }

    val isFgsRunning by KeepAliveForegroundService.isRunning.collectAsStateWithLifecycle()
    val isWakeLockHeld by KeepAliveForegroundService.isWakeLockHeld.collectAsStateWithLifecycle()
    val quickArm = rememberQuickArm()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasPermission = granted[android.Manifest.permission.READ_CALL_LOG] == true ||
                CallLogReader.hasCallLogPermission(context)
        LogEventBus.log(
            source = EventSource.SYSTEM_DIAGNOSTIC,
            action = "Call Log Permission Result",
            details = granted.entries.joinToString { "${it.key.substringAfterLast('.')}=${it.value}" },
            isSuccess = hasPermission
        )
        refreshKey++
    }

    LaunchedEffect(refreshKey) {
        isLoading = true
        hasPermission = CallLogReader.hasCallLogPermission(context)
        records = if (hasPermission) {
            withContext(Dispatchers.IO) { CallLogReader.readRecent(context, limit = 15) }
        } else {
            emptyList()
        }
        isLoading = false
    }

    val lastCall = records.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFE2E8F0)
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            "Last Call Info",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF8FAFC)
                        )
                        Text(
                            "${DeviceProfile.osFlavor()} · ${android.os.Build.MODEL}",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color(0xFF38BDF8)
                        )
                    }
                    IconButton(
                        onClick = { lastCall?.let { shareCallRecord(context, it) } },
                        enabled = lastCall != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share last call",
                            tint = if (lastCall != null) Color(0xFF38BDF8) else Color(0xFF475569)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF090D16)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 76.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!hasPermission) {
                item {
                    PermissionGateCard(
                        onGrant = { permissionLauncher.launch(CallLogReader.requiredPermissions) }
                    )
                }
            }

            item {
                when {
                    isLoading -> InfoPlaceholderCard(
                        icon = Icons.Default.History,
                        title = "Reading call log…",
                        message = "Querying CallLog.Calls on a background dispatcher."
                    )

                    lastCall != null -> LastCallHeroCard(
                        record = lastCall,
                        onCopy = { copyCallRecord(context, lastCall) },
                        onReplayOverlay = {
                            SeparateProcessOverlayService.show(context, "last_call_info_page")
                            Toast.makeText(context, "Overlay replay dispatched", Toast.LENGTH_SHORT).show()
                        }
                    )

                    hasPermission -> InfoPlaceholderCard(
                        icon = Icons.Default.PhoneDisabled,
                        title = "No calls in the log yet",
                        message = "Place or receive a call, then pull this page again with the refresh button."
                    )

                    else -> InfoPlaceholderCard(
                        icon = Icons.Default.PhoneDisabled,
                        title = "Call log locked",
                        message = "Grant the call log permission above to read the last call on this device."
                    )
                }
            }

            item {
                PipelineStatusCard(
                    isFgsRunning = isFgsRunning,
                    isWakeLockHeld = isWakeLockHeld,
                    hasCallLogPermission = hasPermission,
                    hasPhoneStatePermission = CallLogReader.hasPhoneStatePermission(context),
                    onArm = quickArm
                )
            }

            item { DeviceProfileCard(context = context) }

            if (records.size > 1) {
                item {
                    Button(
                        onClick = onOpenCallLog,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                            contentDescription = null,
                            tint = Color(0xFF25D366),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "  See all calls, each with a WhatsApp button",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LastCallHeroCard(
    record: CallRecord,
    onCopy: () -> Unit,
    onReplayOverlay: () -> Unit
) {
    val accent = Color(record.direction.badgeColor)

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF131C2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = directionIcon(record.direction),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        record.displayName,
                        color = Color(0xFFF8FAFC),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        "${record.direction.label} · ${record.relativeTime()}",
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (record.isNew) {
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFB91C1C)) {
                        Text(
                            "UNREAD",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

            DetailRow("Number", record.displayNumber, monospace = true)
            DetailRow("Duration", record.formattedDuration)
            DetailRow("Started", record.formattedTimestamp)
            record.geocodedLocation?.takeIf { it.isNotBlank() }?.let { DetailRow("Location", it) }
            record.phoneAccountId?.takeIf { it.isNotBlank() }?.let { DetailRow("SIM / account", it, monospace = true) }
            record.viaNumber?.takeIf { it.isNotBlank() }?.let { DetailRow("Via number", it, monospace = true) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCopy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "  Copy details",
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(
                    onClick = onReplayOverlay,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "  Replay popup",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun PipelineStatusCard(
    isFgsRunning: Boolean,
    isWakeLockHeld: Boolean,
    hasCallLogPermission: Boolean,
    hasPhoneStatePermission: Boolean,
    onArm: () -> Unit
) {
    val armed = isFgsRunning && hasCallLogPermission && hasPhoneStatePermission

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF131C2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "CALL-END DETECTION PIPELINE",
                color = Color(0xFF38BDF8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            StatusLine("READ_CALL_LOG granted", hasCallLogPermission)
            StatusLine("READ_PHONE_STATE granted", hasPhoneStatePermission)
            StatusLine("Keep-alive foreground service", isFgsRunning)
            StatusLine("PARTIAL_WAKE_LOCK held", isWakeLockHeld)

            if (!armed) {
                Button(
                    onClick = onArm,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Arm everything (permissions + service + wake lock)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    "Armed: TelephonyCallback and the CallLog ContentObserver are running on a dedicated HandlerThread.",
                    color = Color(0xFF34D399),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun DeviceProfileCard(context: Context) {
    val hardware = remember { DeviceProfile.hardwareSummary() }
    val restrictions = remember(context) { DeviceProfile.restrictionSummary(context) }
    val isTranssion = remember { DeviceProfile.isTranssionDevice() }

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF131C2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "HANDSET & ROM PROFILE",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            if (isTranssion) {
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF7C2D12)) {
                    Text(
                        "Transsion ROM detected — PowerKeeper / himgr will gate static receivers in background",
                        color = Color(0xFFFED7AA),
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            hardware.forEach { DetailRow(it.label, it.value, monospace = true) }
            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
            restrictions.forEach { DetailRow(it.label, it.value) }
        }
    }
}

@Composable
private fun PermissionGateCard(onGrant: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1F1300),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF92400E), RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "CALL LOG ACCESS REQUIRED",
                color = Color(0xFFFBBF24),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Text(
                "READ_CALL_LOG and READ_PHONE_STATE are declared in the manifest but still need a runtime grant before the last call can be read.",
                color = Color(0xFFFDE68A),
                fontSize = 11.sp
            )
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Grant call permissions", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InfoPlaceholderCard(icon: ImageVector, title: String, message: String) {
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
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF475569),
                modifier = Modifier.size(34.dp)
            )
            Text(title, color = Color(0xFF94A3B8), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                message,
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFFCBD5E1), fontSize = 12.sp)
        Text(
            if (ok) "OK" else "MISSING",
            color = if (ok) Color(0xFF34D399) else Color(0xFFF87171),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, monospace: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            color = Color(0xFF94A3B8),
            fontSize = 11.5.sp,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            value,
            color = Color(0xFFE2E8F0),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

private fun directionIcon(direction: CallDirection): ImageVector = when (direction) {
    CallDirection.OUTGOING -> Icons.Default.CallMade
    CallDirection.INCOMING -> Icons.Default.CallReceived
    CallDirection.MISSED, CallDirection.REJECTED, CallDirection.BLOCKED -> Icons.Default.CallMissed
    else -> Icons.Default.Phone
}

private fun copyCallRecord(context: Context, record: CallRecord) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Last call", record.toShareText()))
    Toast.makeText(context, "Last call details copied", Toast.LENGTH_SHORT).show()
    LogEventBus.log(
        source = EventSource.CONTENT_OBSERVER,
        action = "Last Call Copied",
        details = "Copied details for ${record.displayName} (${record.direction.label})"
    )
}

private fun shareCallRecord(context: Context, record: CallRecord) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Last call — ${record.displayName}")
        putExtra(Intent.EXTRA_TEXT, record.toShareText())
    }
    context.startActivity(
        Intent.createChooser(intent, "Share last call").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
