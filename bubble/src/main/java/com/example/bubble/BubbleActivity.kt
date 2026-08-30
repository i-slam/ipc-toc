package com.example.bubble

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.calllog.CallLogListScreen

/**
 * The only screen this app has: the recent calls, each one tap from a WhatsApp chat, with the
 * setup card that grants the overlay permission sitting at the top of the same list.
 *
 * Putting the card inside the list rather than above it keeps one scrolling container on screen -
 * two of them nested fight each other on a short phone.
 */
class BubbleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Deep link for launcher shortcuts and automation: show the button without a tap, as
        // long as the grant it needs is already in place.
        if (intent?.action == ACTION_SHOW && Settings.canDrawOverlays(this)) {
            BubbleOverlayService.show(this)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CallLogListScreen(
                    title = "Floating Button",
                    subtitle = "Recent calls · tap the green button to message",
                    header = { BubbleSetupCard() }
                )
            }
        }
    }

    companion object {
        const val ACTION_SHOW = "com.example.bubble.action.SHOW"

        /** Opens this screen from the bubble; the list is what the activity shows either way. */
        const val ACTION_CALL_LOG = "com.example.bubble.action.CALL_LOG"

        fun callLogIntent(context: Context): Intent =
            Intent(context, BubbleActivity::class.java).apply {
                action = ACTION_CALL_LOG
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
    }
}

/** Overlay grant, bubble on/off - everything this app needs before the button can exist. */
@Composable
private fun BubbleSetupCard() {
    val context = LocalContext.current
    val isShowing by BubbleOverlayService.isShowing.collectAsStateWithLifecycle()

    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlay = Settings.canDrawOverlays(context)
        if (hasOverlay) BubbleOverlayService.show(context)
    }

    val callLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasOverlay = Settings.canDrawOverlays(context)
        if (!hasOverlay) {
            overlayLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
        } else {
            BubbleOverlayService.show(context)
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF131C2E),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0369A1)),
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
                        "Floating button",
                        color = Color(0xFFF8FAFC),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Sits on the screen edge above every app",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }

            StatusRow("Display over other apps", hasOverlay)
            StatusRow("Bubble on screen", isShowing)

            Button(
                onClick = {
                    if (isShowing) {
                        BubbleOverlayService.hide(context)
                        return@Button
                    }
                    // Ask for the call log first so the list and the bubble have something to
                    // show, then the overlay grant, which is the one it cannot work without.
                    val perms = mutableListOf(Manifest.permission.READ_CALL_LOG)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms += Manifest.permission.POST_NOTIFICATIONS
                    }
                    callLogLauncher.launch(perms.toTypedArray())
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isShowing) Color(0xFF7F1D1D) else Color(0xFF047857)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isShowing) "Hide the floating button" else "Show the floating button",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "Close this screen and the button stays. Hide it from the button itself or from " +
                        "its notification.",
                color = Color(0xFF64748B),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF16213A))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFFCBD5E1), fontSize = 12.sp)
        Text(
            if (ok) "OK" else "not yet",
            color = if (ok) Color(0xFF34D399) else Color(0xFFF87171),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
