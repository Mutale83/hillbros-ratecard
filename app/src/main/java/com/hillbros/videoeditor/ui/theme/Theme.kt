package com.hillbros.videoeditor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF4F8DFD)
private val AccentDim = Color(0xFF2A4A80)
private val Surface0 = Color(0xFF0E1116)
private val Surface1 = Color(0xFF161B22)
private val Surface2 = Color(0xFF212832)

private val EditorDarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentDim,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF7BE3C3),
    onSecondary = Color(0xFF06231C),
    background = Surface0,
    onBackground = Color(0xFFE6EAF2),
    surface = Surface1,
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Surface2,
    onSurfaceVariant = Color(0xFFA8B3C4),
    outline = Color(0xFF39424F),
    error = Color(0xFFFF6B6B),
)

private val EditorLightColors = lightColorScheme(
    primary = Accent,
    secondary = Color(0xFF11866B),
)

/**
 * The editor is a dark-first tool — a bright chrome around video preview makes
 * colour grading unreliable — so dark is the default unless the system is
 * explicitly in light mode.
 */
@Composable
fun HillBrosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) EditorDarkColors else EditorLightColors,
        content = content,
    )
}
