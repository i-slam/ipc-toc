package com.example.bubble

import android.Manifest
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
import androidx.compose.foundation.layout.fillMaxSize
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

/**
 * The only screen this app has. It exists to get the overlay permission and switch the bubble on -
 * everything else happens in the floating button itself.
 */
class BubbleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                BubbleSetupScreen()
            }
        }
    }
}

@Composable
private fun BubbleSetupScreen() {
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
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF090D16)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0369A1)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }

            Text(
                "Floating Button",
                color = Color(0xFFF8FAFC),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "A bubble that sits on the screen edge above every app. Drag it anywhere, tap it " +
                        "for the last call, the dialer, or the full diagnostics app.",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp
            )

            StatusRow("Display over other apps", hasOverlay)
            StatusRow("Bubble on screen", isShowing)

            Button(
                onClick = {
                    if (isShowing) {
                        BubbleOverlayService.hide(context)
                        return@Button
                    }
                    // Ask for the call log first so the bubble has something to show, then the
                    // overlay grant, which is the one it cannot work without.
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
                "You can close this screen - the button stays. Hide it from the button itself or " +
                        "from its notification.",
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
            .background(Color(0xFF131C2E))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
