package com.bbdroid.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ---------- 主题模式 ----------
enum class ThemeMode { NORMAL, GLASS }

object ThemePrefs {
    private const val KEY = "theme_mode"
    private const val KEY_GLASS = "glass_intensity"

    fun get(context: Context): ThemeMode {
        val v = context.getSharedPreferences("bbdroid", Context.MODE_PRIVATE).getString(KEY, "normal")
        return if (v == "glass") ThemeMode.GLASS else ThemeMode.NORMAL
    }

    fun set(context: Context, mode: ThemeMode) {
        context.getSharedPreferences("bbdroid", Context.MODE_PRIVATE)
            .edit().putString(KEY, if (mode == ThemeMode.GLASS) "glass" else "normal").commit()
    }

    // 折光效果强度 0..1
    fun getGlassIntensity(context: Context): Float =
        context.getSharedPreferences("bbdroid", Context.MODE_PRIVATE).getFloat(KEY_GLASS, 0.5f)

    fun setGlassIntensity(context: Context, value: Float) {
        context.getSharedPreferences("bbdroid", Context.MODE_PRIVATE)
            .edit().putFloat(KEY_GLASS, value.coerceIn(0f, 1f)).commit()
    }
}

// ---------- 普通主题（既有） ----------
private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF64748B),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFF1F5F9),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0F172A),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
    error = Color(0xFFF87171),
    onError = Color(0xFF0F172A),
)

// ---------- 液态玻璃主题（透明度随折光强度动态变化） ----------
// intensity 0 = 几乎透明（更清透），1 = 半透明磨砂（更明显折光）
private fun glassColors(intensity: Float): ColorScheme {
    val a = (0.14f + intensity * 0.26f).coerceIn(0f, 0.5f)            // 表面基准透明度
    val hi = (a * 1.5f).coerceIn(0f, 0.6f)                            // 高层容器透明度
    val br = (a * 1.2f).coerceIn(0f, 0.55f)
    return darkColorScheme(
        primary = Color(0xFF7AC8FF),
        onPrimary = Color(0xFF002A4A),
        primaryContainer = Color.White.copy(alpha = br),
        onPrimaryContainer = Color(0xFFE8F4FF),
        secondary = Color(0xFFB9C6FF),
        onSecondary = Color(0xFF1A2150),
        secondaryContainer = Color.White.copy(alpha = br),
        onSecondaryContainer = Color(0xFFE4E9FF),
        tertiary = Color(0xFFFFB3E0),
        onTertiary = Color(0xFF4E1038),
        tertiaryContainer = Color.White.copy(alpha = br),
        onTertiaryContainer = Color(0xFFFFE4F2),
        background = Color.Transparent,
        onBackground = Color(0xFFF4F7FF),
        surface = Color.White.copy(alpha = a),
        onSurface = Color(0xFFF4F7FF),
        surfaceVariant = Color.White.copy(alpha = hi),
        onSurfaceVariant = Color(0xFFE2E9FF),
        surfaceContainer = Color.White.copy(alpha = a),
        surfaceContainerHigh = Color.White.copy(alpha = (a * 1.5f).coerceIn(0f, 0.6f)),
        surfaceContainerHighest = Color.White.copy(alpha = (a * 1.8f).coerceIn(0f, 0.6f)),
        surfaceContainerLow = Color.White.copy(alpha = (a * 0.7f)),
        surfaceContainerLowest = Color.White.copy(alpha = (a * 0.4f)),
        outline = Color.White.copy(alpha = (a + 0.28f).coerceIn(0f, 0.7f)),
        outlineVariant = Color.White.copy(alpha = hi),
        inverseSurface = Color(0xFFF4F7FF),
        inverseOnSurface = Color(0xFF12173A),
        inversePrimary = Color(0xFF1F4C7A),
        error = Color(0xFFFF8A80),
        onError = Color(0xFF3A0000),
        scrim = Color(0x33000000),
    )
}

// 液态玻璃背后的渐变底色 + 折光光斑（强度越高折光越明显）
@Composable
fun GlassBackdrop(intensity: Float = 0.5f, modifier: Modifier = Modifier) {
    // 干净深色渐变背景（非液态玻璃，为玻璃按钮提供可折射的底色）
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1E2A78),
                        Color(0xFF4C2FA6),
                        Color(0xFF8B3FC9),
                        Color(0xFFD34FA0),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(2400f, 2400f),
                )
            )
    ) {
        // 极柔和的顶部亮带，给按钮一点层次（不是光斑折光）
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.06f + intensity * 0.05f), Color.Transparent),
                        startY = 0f,
                        endY = 700f,
                    )
                )
        )
    }
}

@Composable
fun BbdroidTheme(
    mode: ThemeMode = ThemeMode.NORMAL,
    glassIntensity: Float = 0.5f,
    content: @Composable () -> Unit,
) {
    val scheme = when (mode) {
        ThemeMode.GLASS -> glassColors(glassIntensity)
        ThemeMode.NORMAL -> if (isSystemInDarkTheme()) DarkColors else LightColors
    }
    CompositionLocalProvider(
        LocalGlassEnabled provides (mode == ThemeMode.GLASS),
        LocalGlassIntensity provides glassIntensity,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
