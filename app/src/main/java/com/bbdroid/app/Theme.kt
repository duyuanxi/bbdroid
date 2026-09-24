package com.bbdroid.app

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ---------- 主题模式 ----------
enum class ThemeMode { SYSTEM, LIGHT, DARK }

// 玻璃皮肤配置（可选皮肤，默认关闭）
data class GlassConfig(val enabled: Boolean = false, val intensity: Float = 0.5f)

// 玻璃皮肤是否启用 / 折光强度（由主题提供，组件据此切换材质）
val LocalGlassEnabled = staticCompositionLocalOf { false }
val LocalGlassIntensity = staticCompositionLocalOf { 0.5f }

// M3 标准缓动（MotionScheme.standard() 的平滑、不回弹语义）
val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// ---------- 玻璃皮肤：基于当前配色派生半透明表面 ----------
private fun glassScheme(base: ColorScheme, intensity: Float): ColorScheme {
    val a = (0.28f + intensity * 0.18f).coerceIn(0f, 0.5f) // 面板填充 α ≤ 0.5
    val hi = (a * 1.25f).coerceIn(0f, 0.5f)
    val low = (a * 0.6f).coerceIn(0f, 0.5f)
    return base.copy(
        surface = Color.White.copy(alpha = a),
        surfaceContainerLow = Color.White.copy(alpha = low),
        surfaceContainer = Color.White.copy(alpha = a),
        surfaceContainerHigh = Color.White.copy(alpha = hi),
        surfaceContainerHighest = Color.White.copy(alpha = hi),
        surfaceVariant = Color.White.copy(alpha = hi),
        outline = Color.White.copy(alpha = (a + 0.28f).coerceIn(0f, 0.7f)),
        outlineVariant = Color.White.copy(alpha = hi),
    )
}

// 玻璃皮肤背后的彩色渐变底（为模糊/半透明面板提供可折射的底色，保证"模糊背景有颜色"）
@Composable
fun GlassBackdrop(intensity: Float = 0.5f, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF43285F),
                        Color(0xFF7A2E6B),
                        Color(0xFFB23A67),
                        Color(0xFFE0556E),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(1400f, 2400f),
                )
            )
    ) {
        // 顶部亮带：为玻璃面板提供镜面高光来源
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.08f + intensity * 0.06f), Color.Transparent),
                        startY = 0f,
                        endY = 900f,
                    )
                )
        )
    }
}

@Composable
fun BbdroidTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    glass: GlassConfig = GlassConfig(),
    content: @Composable () -> Unit,
) {
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val base = if (darkTheme) DarkColors else LightColors
    val scheme = if (glass.enabled) glassScheme(base, glass.intensity) else base

    CompositionLocalProvider(
        LocalGlassEnabled provides glass.enabled,
        LocalGlassIntensity provides glass.intensity,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
