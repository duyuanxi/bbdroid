package com.bbdroid.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * M3 Expressive 形状：卡片 20dp、对话框/底部抽屉 28dp、按钮胶囊形（CircleShape）。
 * 按钮默认即为胶囊形（M3 CornerFull），无需额外设置。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

// 对话框 / 底部抽屉的圆角（28dp）
val DialogShape = RoundedCornerShape(28.dp)
