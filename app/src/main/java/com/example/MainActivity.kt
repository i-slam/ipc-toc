package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.AppRoot
import com.example.ui.AppRoute
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val openLastCall = intent?.getBooleanExtra(EXTRA_OPEN_LAST_CALL, false) == true
        val startQuickArm = intent?.getBooleanExtra(EXTRA_START_QUICK_ARM, false) == true

        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                AppRoot(
                    initialRoute = if (openLastCall) AppRoute.LAST_CALL else AppRoute.DIAGNOSTICS,
                    autoArm = startQuickArm
                )
            }
        }
    }

    companion object {
        /** Set by the notification action, the tile and the launcher shortcut. */
        const val EXTRA_OPEN_LAST_CALL = "open_last_call"

        /** Set by the arm shortcut when the permission chain still needs dialogs. */
        const val EXTRA_START_QUICK_ARM = "start_quick_arm"
    }
}
