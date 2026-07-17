package com.example.ezmakro.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * App color tokens. Dark-mode variants live in [darkScheme]; light variants in
 * [lightScheme]. Kept as plain Compose [Color] so they can be reused outside of
 * MaterialTheme (e.g. charts).
 */
val lightScheme =
    androidx.compose.material3.lightColorScheme(
        primary = Color(0xFF2E7D32),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFB8F0BB),
        secondary = Color(0xFF00695C),
        background = Color(0xFFFDFDFD),
        surface = Color(0xFFFFFFFF),
        error = Color(0xFFB3261E),
    )

val darkScheme =
    androidx.compose.material3.darkColorScheme(
        primary = Color(0xFF8BE38F),
        onPrimary = Color(0xFF003911),
        primaryContainer = Color(0xFF00531E),
        secondary = Color(0xFF4DB6AC),
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E),
        error = Color(0xFFF2B8B5),
    )
