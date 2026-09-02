package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette from the Floating CRM concept, kept as one object so a screen never invents a
 * colour. Names follow the concept's own CSS variables so the two stay comparable.
 */
object Crm {
    val Ink = Color(0xFF11141B)
    val Surface = Color(0xFF1A2029)
    val Surface2 = Color(0xFF232B37)
    val Surface3 = Color(0xFF2C3542)
    val Line = Color(0xFF333D4C)

    val Text = Color(0xFFE9ECF2)
    val TextMuted = Color(0xFF8B93A5)

    val Accent = Color(0xFFFF8A3D)
    val Accent2 = Color(0xFFFFB273)

    /** Text and icons that sit *on* the accent - the concept uses a dark brown, not white. */
    val AccentInk = Color(0xFF3A1E00)

    val WhatsApp = Color(0xFF25D366)
    val WhatsAppInk = Color(0xFF083318)
    val WhatsAppText = Color(0xFF8CF0B4)
    val WhatsAppLine = Color(0xFF145530)

    val Danger = Color(0xFFFF5C6C)

    /** Between muted text and the hairline - for timestamps and other third-rank detail. */
    val TextFaint = Color(0xFF6B7387)

    /** The warning ground the permission gate sits on: the accent, dropped almost to black. */
    val WarnSurface = Color(0xFF2A1705)

    /** Overlay surfaces sit over other apps, so they carry their own opacity. */
    val OverlaySurface = Color(0xF2232B37)
}
