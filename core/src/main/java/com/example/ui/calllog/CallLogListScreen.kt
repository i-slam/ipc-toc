package com.example.ui.calllog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.telephony.CallDirection
import com.example.telephony.CallLogReader
import com.example.telephony.WhatsAppLauncher

private val Background = Color(0xFF090D16)
private val Card = Color(0xFF131C2E)
private val Hairline = Color(0xFF1E293B)
private val Bright = Color(0xFFF8FAFC)
private val Body = Color(0xFFE2E8F0)
private val Muted = Color(0xFF94A3B8)
private val Faint = Color(0xFF64748B)
private val Accent = Color(0xFF38BDF8)
private val WhatsAppGreen = Color(0xFF25D366)
private val WhatsAppInk = Color(0xFF11351F)

/** Room for the Swiss-army rail is the caller's business; this is the plain-screen default. */
val DefaultListPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)

/** Openers offered on the overflow menu, because a missed call usually wants the same two replies. */
private val QUICK_REPLIES = listOf(
    "Sorry I missed your call",
    "Can you call me back?"
)

/** Stable handle for the tab strip, so a test can switch tabs without matching on label text. */
fun tabTestTag(tab: CallLogTab): String = "call-log-tab-${tab.name}"

/** What a row can do, gathered so the list does not need four lambdas threaded through it. */
class CallLogActions(
    val onWhatsApp: (CallRow, String) -> Unit,
    val onDial: (CallRow) -> Unit,
    val onCopy: (CallRow) -> Unit,
    val onShare: (CallRow) -> Unit
)

/** The real actions, wired to intents and the clipboard. */
@Composable
fun rememberCallLogActions(): CallLogActions {
    val context = LocalContext.current
    return remember(context) {
        CallLogActions(
            onWhatsApp = { row, message -> openWhatsApp(context, row, message) },
            onDial = { row -> dialNumber(context, row) },
            onCopy = { row -> copyDetails(context, row) },
            onShare = { row -> shareCall(context, row) }
        )
    }
}

/**
 * Every recent call, filtered by tab, each row one tap from a WhatsApp chat.
 *
 * [header] is rendered as the first item of the same list rather than above it - the standalone
 * app puts its setup card there, and stacking two scrolling containers would fight each other.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogListScreen(
    modifier: Modifier = Modifier,
    title: String = "Call log",
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    contentPadding: PaddingValues = DefaultListPadding,
    header: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current

    var refreshKey by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var rows by remember { mutableStateOf<List<CallRow>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(CallLogReader.hasCallLogPermission(context)) }
    var whatsAppInstalled by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(CallLogTab.ALL) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = CallLogReader.hasCallLogPermission(context)
        refreshKey++
    }

    LaunchedEffect(refreshKey) {
        isLoading = true
        hasPermission = CallLogReader.hasCallLogPermission(context)
        whatsAppInstalled = WhatsAppLauncher.isInstalled(context)
        rows = if (hasPermission) CallLogLoader.load(context) else emptyList()
        isLoading = false
    }

    ObserveCallLog { refreshKey++ }

    val actions = rememberCallLogActions()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Body
                            )
                        }
                    }
                },
                title = {
                    Column {
                        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Bright)
                        if (subtitle != null) {
                            Text(subtitle, fontSize = 11.sp, color = Muted)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Background
    ) { innerPadding ->
        CallLogContent(
            rows = rows,
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it },
            isLoading = isLoading,
            hasPermission = hasPermission,
            whatsAppInstalled = whatsAppInstalled,
            onGrantPermission = { permissionLauncher.launch(CallLogReader.requiredPermissions) },
            actions = actions,
            contentPadding = contentPadding,
            header = header,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

/**
 * The list itself, with no provider access of its own, so the states worth checking - empty,
 * locked, WhatsApp missing, a number WhatsApp cannot take - can be composed directly in a test.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogContent(
    rows: List<CallRow>,
    selectedTab: CallLogTab,
    onSelectTab: (CallLogTab) -> Unit,
    isLoading: Boolean,
    hasPermission: Boolean,
    whatsAppInstalled: Boolean,
    onGrantPermission: () -> Unit,
    actions: CallLogActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = DefaultListPadding,
    header: (@Composable () -> Unit)? = null
) {
    val counts = remember(rows) { CallLogFilter.counts(rows) }
    val visible = remember(rows, selectedTab) { CallLogFilter.filter(selectedTab, rows) }

    Column(modifier = modifier) {
        PrimaryTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color(0xFF0F172A),
            contentColor = Accent
        ) {
            CallLogTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier.testTag(tabTestTag(tab)),
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(tab.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                (counts[tab] ?: 0).toString(),
                                fontSize = 10.sp,
                                color = if (tab == CallLogTab.MISSED && (counts[tab] ?: 0) > 0) {
                                    Color(CallDirection.MISSED.badgeColor)
                                } else {
                                    Faint
                                }
                            )
                        }
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (header != null) {
                item(key = "header") { header() }
            }

            if (!hasPermission) {
                item(key = "gate") { CallLogPermissionGate(onGrant = onGrantPermission) }
            } else if (!whatsAppInstalled) {
                // One banner beats thirty buttons that all lead to a browser.
                item(key = "no-whatsapp") {
                    NoticeCard("WhatsApp is not installed, so the green buttons will fall back to a browser link.")
                }
            }

            when {
                isLoading -> item(key = "loading") {
                    EmptyState(Icons.Default.Refresh, "Reading call log…")
                }

                visible.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        icon = Icons.Default.PhoneDisabled,
                        title = when {
                            !hasPermission -> "Call log locked"
                            rows.isEmpty() -> "No calls in the log yet"
                            else -> "Nothing under ${selectedTab.label}"
                        }
                    )
                }

                else -> items(visible, key = { it.record.id }) { row ->
                    CallLogRow(row = row, actions = actions)
                }
            }
        }
    }
}

@Composable
private fun CallLogRow(row: CallRow, actions: CallLogActions) {
    val record = row.record
    val accent = Color(record.direction.badgeColor)
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Card,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Hairline, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = directionIcon(record.direction),
                    contentDescription = record.direction.label,
                    tint = accent,
                    modifier = Modifier.size(17.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.displayName,
                    color = Body,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    "${record.direction.label} · ${record.formattedDuration} · ${record.relativeTime()}",
                    color = Muted,
                    fontSize = 10.5.sp,
                    maxLines = 1
                )
            }

            WhatsAppButton(row = row, onClick = { actions.onWhatsApp(row, "") })

            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More actions for ${record.displayName}",
                        tint = Faint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Call back") },
                        leadingIcon = { Icon(Icons.Default.Dialpad, null) },
                        onClick = { menuOpen = false; actions.onDial(row) }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy details") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = { menuOpen = false; actions.onCopy(row) }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = { menuOpen = false; actions.onShare(row) }
                    )
                    if (row.canWhatsApp) {
                        HorizontalDivider(color = Hairline)
                        QUICK_REPLIES.forEach { reply ->
                            DropdownMenuItem(
                                text = { Text("WhatsApp: \"$reply\"") },
                                leadingIcon = { Icon(Icons.Default.Chat, null, tint = WhatsAppGreen) },
                                onClick = { menuOpen = false; actions.onWhatsApp(row, reply) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Disabled rather than hidden when the number cannot be resolved: the button staying put in every
 * row is what makes the list scannable, and a greyed one says "not this number" where a missing
 * one would just look like a rendering bug.
 */
@Composable
private fun WhatsAppButton(row: CallRow, onClick: () -> Unit) {
    val enabled = row.canWhatsApp
    val description = if (enabled) {
        "WhatsApp ${row.record.displayName}"
    } else {
        "WhatsApp unavailable: ${row.record.displayNumber} has no country code"
    }

    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (enabled) WhatsAppInk else Color(0xFF161E2E))
            .alpha(if (enabled) 1f else 0.38f)
            .semantics { contentDescription = description }
    ) {
        Icon(
            imageVector = Icons.Default.Chat,
            contentDescription = null,
            tint = WhatsAppGreen,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun CallLogPermissionGate(onGrant: () -> Unit) {
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
                "The list stays empty until READ_CALL_LOG is granted at runtime.",
                color = Color(0xFFFDE68A),
                fontSize = 11.sp
            )
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Grant call log access", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NoticeCard(message: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF10233A),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            message,
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = Card, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(28.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(30.dp))
            Text(title, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Re-reads the list when a call ends behind the app, and only while the screen is resumed - a
 * bubble sitting over the dialer is the whole reason this list needs to be current.
 */
@Composable
private fun ObserveCallLog(onChanged: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChanged()
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    onChanged()
                    // Throws without the grant, and some ROMs refuse it even with one.
                    runCatching {
                        context.contentResolver.registerContentObserver(
                            CallLog.Calls.CONTENT_URI,
                            true,
                            observer
                        )
                    }
                }

                Lifecycle.Event.ON_PAUSE ->
                    runCatching { context.contentResolver.unregisterContentObserver(observer) }

                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }
}

private fun directionIcon(direction: CallDirection): ImageVector = when (direction) {
    CallDirection.OUTGOING -> Icons.Default.CallMade
    CallDirection.INCOMING -> Icons.Default.CallReceived
    CallDirection.MISSED, CallDirection.REJECTED, CallDirection.BLOCKED -> Icons.Default.CallMissed
    else -> Icons.Default.Phone
}

private fun openWhatsApp(context: Context, row: CallRow, message: String) {
    if (!row.canWhatsApp) {
        Toast.makeText(
            context,
            "${row.record.displayNumber} has no country code, so WhatsApp cannot open it",
            Toast.LENGTH_LONG
        ).show()
        return
    }
    WhatsAppLauncher.openChat(context, row.record.number, message)?.let {
        Toast.makeText(context, it, Toast.LENGTH_LONG).show()
    }
}

private fun dialNumber(context: Context, row: CallRow) {
    val number = row.record.number.takeIf { it.isNotBlank() }
    if (number == null) {
        Toast.makeText(context, "That call has no number to dial", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun copyDetails(context: Context, row: CallRow) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Call", row.record.toShareText()))
    Toast.makeText(context, "Call details copied", Toast.LENGTH_SHORT).show()
}

private fun shareCall(context: Context, row: CallRow) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Call — ${row.record.displayName}")
        putExtra(Intent.EXTRA_TEXT, row.record.toShareText())
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, "Share call").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
