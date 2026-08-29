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

        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                AppRoot(
                    initialRoute = if (openLastCall) AppRoute.LAST_CALL else AppRoute.DIAGNOSTICS
                )
            }
        }
    }

    companion object {
        /** Set by the keep-alive notification action to land straight on the Last Call page. */
        const val EXTRA_OPEN_LAST_CALL = "open_last_call"
    }
}
