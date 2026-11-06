package com.bbdroid.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.*

private data class Backdrop(val size: Size = Size.Zero, val dark: Boolean = false)
private val LocalBackdrop = staticCompositionLocalOf { Backdrop() }

@Composable
fun GlassSceneHost(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    var dimensions by remember { mutableStateOf(IntSize.Zero) }
    val backdrop = Backdrop(Size(dimensions.width.toFloat(), dimensions.height.toFloat()), LocalGlassDark.current)
    val enabled = LocalGlassEnabled.current
    val solid = MaterialTheme.colorScheme.surface
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(modifier.onSizeChanged { dimensions = it }) {
            Canvas(Modifier.matchParentSize()) { if (enabled) drawBackdrop(backdrop) else drawRect(solid) }
            content()
        }
    }
}

private fun DrawScope.drawBackdrop(scene: Backdrop) {
    val w = scene.size.width.takeIf { it > 0 } ?: size.width
    val h = scene.size.height.takeIf { it > 0 } ?: size.height
    drawRect(if (scene.dark) Color(0xFF0E1824) else Color(0xFFF3F7FB), size = Size(w, h))
    if (w == 0f || h == 0f) return
    fun glow(color: Color, center: Offset, radius: Float) {
        drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius), radius, center)
    }
    glow(if (scene.dark) Color(0xFF1A3A58) else Color(0xFFCDDEF6), Offset(w, h * 0.12f), w * 0.92f)
    glow(if (scene.dark) Color(0xFF123B3D) else Color(0xFFD5EBE7), Offset(0f, h * 0.7f), w * 0.84f)
    glow(if (scene.dark) Color(0xFF25354D) else Color(0xFFE1E8F8), Offset(w, h), w * 0.85f)
    drawCircle(Color.White.copy(alpha = if (scene.dark) 0.025f else 0.3f), w * 0.78f, Offset(w * 1.18f, h * 0.42f))
    drawCircle(Color.White.copy(alpha = if (scene.dark) 0.018f else 0.2f), w * 0.62f, Offset(-w * 0.25f, h * 0.85f))
}

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val backdrop = LocalBackdrop.current
    val enabled = LocalGlassEnabled.current
    val intensity = LocalGlassIntensity.current
    val shape = RoundedCornerShape(26.dp)
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    var offset by remember { mutableStateOf(Offset.Zero) }
    val outline = Brush.linearGradient(listOf(
        Color.White.copy(alpha = if (backdrop.dark) 0.19f else 0.95f),
        Color.White.copy(alpha = if (backdrop.dark) 0.04f else 0.14f),
        Color.White.copy(alpha = if (backdrop.dark) 0.13f else 0.65f)))
    Box(modifier.shadow(if (enabled) 9.dp else 1.dp, shape,
        ambientColor = Color(0xFF1D4368).copy(alpha = 0.08f), spotColor = Color(0xFF1D4368).copy(alpha = 0.12f))
        .clip(shape).onGloballyPositioned { offset = it.positionInRoot() }
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .then(if (enabled) Modifier.border(1.dp, outline, shape) else Modifier)) {
        if (enabled) {
            Canvas(Modifier.matchParentSize().blur((12 + intensity * 28).dp, BlurredEdgeTreatment.Unbounded)) {
                translate(-offset.x, -offset.y) { drawBackdrop(backdrop) }
            }
            val fill = if (backdrop.dark) Color(0xFF152638) else Color.White
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                fill.copy(alpha = 0.48f + intensity * 0.23f), fill.copy(alpha = 0.25f + intensity * 0.28f)))))
        } else Box(Modifier.matchParentSize().background(base))
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun PageIntro(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Legacy call sites now receive the shared AppCard material instead of painting a second layer.
@Composable fun glassPanel(): Modifier = Modifier
@Composable fun glassSurface(): Modifier = Modifier
