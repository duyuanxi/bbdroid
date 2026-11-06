package com.bbdroid.app

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

// ---------- 主题模式 ----------
enum class ThemeMode { SYSTEM, LIGHT, DARK }

// 玻璃皮肤配置（可选皮肤，默认关闭）
data class GlassConfig(val enabled: Boolean = false, val intensity: Float = 0.5f)

// 玻璃皮肤是否启用 / 材质强度（由主题提供，组件据此切换材质）
val LocalDarkTheme = staticCompositionLocalOf { false }
val LocalGlassEnabled = staticCompositionLocalOf { false }
val LocalGlassIntensity = staticCompositionLocalOf { 0.5f }

// M3 标准缓动（MotionScheme.standard() 的平滑、不回弹语义）
val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

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
    val scheme = base

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalGlassEnabled provides glass.enabled,
        LocalGlassIntensity provides glass.intensity.takeIf { it.isFinite() }?.coerceIn(0f, 1f).let { it ?: 0.5f },
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
