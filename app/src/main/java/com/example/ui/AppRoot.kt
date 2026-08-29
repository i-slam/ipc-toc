package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

enum class AppRoute { DIAGNOSTICS, LAST_CALL }

/**
 * Hosts the app pages and keeps the Swiss-army rail pinned above all of them, so every tool stays
 * one tap away no matter which page is open.
 */
@Composable
fun AppRoot(initialRoute: AppRoute = AppRoute.DIAGNOSTICS) {
    var route by rememberSaveable { mutableStateOf(initialRoute) }

    BackHandler(enabled = route != AppRoute.DIAGNOSTICS) { route = AppRoute.DIAGNOSTICS }

    Box(modifier = Modifier.fillMaxSize()) {
        when (route) {
            AppRoute.DIAGNOSTICS -> DiagnosticScreen()
            AppRoute.LAST_CALL -> LastCallInfoScreen(onBack = { route = AppRoute.DIAGNOSTICS })
        }

        SwissArmyRail(onOpenLastCall = { route = AppRoute.LAST_CALL })
    }
}
