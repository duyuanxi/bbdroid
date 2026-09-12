package com.bbdroid.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

// 全局玻璃配置（由主题提供）
val LocalGlassEnabled = staticCompositionLocalOf { false }
val LocalGlassIntensity = staticCompositionLocalOf { 0.5f }

/**
 * 液态玻璃按钮：半透明玻璃面板 + 顶部折光边 + 交互感知高光（高光跟随手指位置，按下缩放反馈）。
 * 非玻璃主题下自动退化为普通 Button / OutlinedButton。
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (!LocalGlassEnabled.current) {
        if (filled) Button(onClick = onClick, enabled = enabled, modifier = modifier, content = content)
        else OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, content = content)
        return
    }

    val intensity = LocalGlassIntensity.current
    val shape = RoundedCornerShape(14.dp)
    val bgAlpha = (0.12f + intensity * 0.22f).coerceIn(0f, 0.4f)
    val bgColor = Color.White.copy(alpha = if (filled) bgAlpha else bgAlpha * 0.6f)
    val contentColor = Color(0xFFF4F7FF)

    var pressed by remember { mutableStateOf(false) }
    var pressPos by remember { mutableStateOf(Offset.Unspecified) }
    val scale by animateFloatAsState(if (pressed) 0.955f else 1f, label = "glassScale")
    val highlightColor = Color.White.copy(alpha = (0.28f + intensity * 0.38f).coerceIn(0f, 0.7f))
    val borderBrush = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.05f))
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .shadow(if (pressed) 12.dp else 6.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (enabled) bgColor else bgColor.copy(alpha = bgColor.alpha * 0.45f), shape)
            .border(1.5.dp, borderBrush, shape)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = { offset ->
                        pressed = true
                        pressPos = offset
                        // 等待松手；期间按住手指，高光即停留在触点
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() },
                )
            }
    ) {
        // 折光高光（跟随手指）+ 顶部折光边
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    if (pressed && pressPos != Offset.Unspecified) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(highlightColor, Color.Transparent),
                                center = pressPos,
                                radius = size.minDimension * 0.8f
                            ),
                            center = pressPos,
                            radius = size.minDimension * 0.8f,
                        )
                    }
                    // 顶部折光边（液态玻璃特征）
                    drawLine(
                        brush = Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.6f), Color.Transparent),
                            startY = 0f, endY = 26f
                        ),
                        start = Offset(0f, 1.5f),
                        end = Offset(size.width, 1.5f),
                        strokeWidth = 1.5f
                    )
                }
        )
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) { content() }
        }
    }
}
