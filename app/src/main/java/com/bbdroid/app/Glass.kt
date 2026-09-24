package com.bbdroid.app

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 玻璃面板边缘：上白高光 + 下深描边。仅在玻璃皮肤开启时生效，普通主题下为 no-op。
 * 用于承载内容的卡面（解析卡、结果卡、探测卡等），玻璃内不嵌套玻璃。
 */
@Composable
fun glassPanel(): Modifier {
    val enabled = LocalGlassEnabled.current
    val intensity = LocalGlassIntensity.current
    if (!enabled) return Modifier
    return Modifier.drawBehind {
        val top = (0.5f + intensity * 0.2f).coerceIn(0f, 0.7f)
        val bottom = (0.06f + intensity * 0.08f).coerceIn(0f, 0.2f)
        // 顶部镜面高光
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = top), Color.Transparent),
                startY = 0f,
                endY = size.height * 0.32f,
            )
        )
        // 底部深描边
        drawLine(
            color = Color(0xFF2A1A2E).copy(alpha = bottom),
            start = Offset(0f, size.height - 1f),
            end = Offset(size.width, size.height - 1f),
            strokeWidth = 1.2f,
        )
        // 顶部细高光边
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = top), Color.Transparent),
                startX = size.width * 0.12f,
                endX = size.width * 0.88f,
            ),
            start = Offset(size.width * 0.12f, 1f),
            end = Offset(size.width * 0.88f, 1f),
            strokeWidth = 1.2f,
        )
    }
}

/** 玻璃卡面通用修饰：玻璃开启时叠加半透明描边，普通主题下仅返回原修饰。 */
@Composable
fun glassSurface(): Modifier {
    val enabled = LocalGlassEnabled.current
    if (!enabled) return Modifier
    return Modifier.border(1.dp, Color.White.copy(alpha = 0.35f), DialogShape)
}
