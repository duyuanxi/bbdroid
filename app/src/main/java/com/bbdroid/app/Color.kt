package com.bbdroid.app

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val AccentBlue = Color(0xFF007AFF)
val LightColors = lightColorScheme(
    primary = Color(0xFF0066D6), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0EDFF), onPrimaryContainer = Color(0xFF123966),
    secondary = Color(0xFF53687D), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5EDF5), onSecondaryContainer = Color(0xFF243B52),
    tertiary = Color(0xFF267A7B), onTertiary = Color.White,
    surface = Color(0xFFF4F7FB), surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainer = Color(0xFFEDF2F8), surfaceContainerHigh = Color(0xFFE8EEF5),
    surfaceContainerHighest = Color(0xFFE1E9F2),
    onSurface = Color(0xFF182B3E), onSurfaceVariant = Color(0xFF5B6C80),
    outline = Color(0xFF8596A9), outlineVariant = Color(0xFFD1DCE8),
    error = Color(0xFFB3261E), onError = Color.White,
)
val DarkColors = darkColorScheme(
    primary = Color(0xFF7AB5FF), onPrimary = Color(0xFF08274B),
    primaryContainer = Color(0xFF173C62), onPrimaryContainer = Color(0xFFDBEBFF),
    secondary = Color(0xFFB3C7DD), onSecondary = Color(0xFF1B3047),
    secondaryContainer = Color(0xFF293E54), onSecondaryContainer = Color(0xFFE0EEFF),
    tertiary = Color(0xFF8DD5D3), onTertiary = Color(0xFF103C3C),
    surface = Color(0xFF0E1824), surfaceContainerLow = Color(0xFF132231),
    surfaceContainer = Color(0xFF182A3B), surfaceContainerHigh = Color(0xFF203347),
    surfaceContainerHighest = Color(0xFF293E52),
    onSurface = Color(0xFFEAF3FF), onSurfaceVariant = Color(0xFFB4C4D7),
    outline = Color(0xFF8497AD), outlineVariant = Color(0xFF35495E),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
)
