package com.bbdroid.app

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

private class GlassSceneState(val source: GraphicsLayer, val blurred: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
}
private val LocalGlassScene = staticCompositionLocalOf<GlassSceneState?> { null }
val LocalPageBottomInset = staticCompositionLocalOf { 0.dp }
val PanelShape = RoundedCornerShape(26.dp)

/** A neutral canvas. The navigation's glass gets its color from the page beneath it. */
@Composable
fun GlassScene(content: @Composable () -> Unit) {
    val source = rememberGraphicsLayer()
    val blurred = rememberGraphicsLayer()
    val scene = remember(source, blurred) { GlassSceneState(source, blurred) }
    CompositionLocalProvider(LocalGlassScene provides scene) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
    }
}

/** Capture the page separately from floating controls, preventing recursive backdrop sampling. */
@Composable
fun captureGlassContent(): Modifier {
    val scene = LocalGlassScene.current
    if (!LocalGlassEnabled.current || scene == null || Build.VERSION.SDK_INT < 31) return Modifier
    return Modifier.onGloballyPositioned { scene.origin = it.positionInRoot() }
        .drawWithContent {
            scene.source.record { this@drawWithContent.drawContent() }
            scene.blurred.renderEffect = BlurEffect(18.dp.toPx(), 18.dp.toPx())
            scene.blurred.record { drawLayer(scene.source) }
            drawLayer(scene.source)
        }
}

/** Background blur and a restrained lens effect, with sharp foreground controls. */
@Composable
fun glassMaterial(shape: Shape = PanelShape): Modifier {
    if (!LocalGlassEnabled.current) return Modifier
    val scene = LocalGlassScene.current
    val dark = LocalDarkTheme.current
    val strength = LocalGlassIntensity.current
    var position by remember { mutableStateOf(Offset.Zero) }
    val tint = if (dark) Color(0xFF202024) else Color(0xFFF9F9FC)
    val nativeBlur = Build.VERSION.SDK_INT >= 31
    return Modifier.shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.09f), spotColor = Color.Black.copy(alpha = 0.09f))
        .clip(shape)
        .onGloballyPositioned { position = it.positionInRoot() }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.08f else 0.22f), Color.Transparent, Color.White.copy(alpha = 0.04f)))
            val rim = Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) 0.36f else 0.95f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = if (dark) 0.15f else 0.65f)))
            onDrawWithContent {
                if (nativeBlur) scene?.let {
                    val offset = position - it.origin
                    scale(1f + strength * 0.018f, pivot = center) {
                        translate(-offset.x, -offset.y) { drawLayer(it.blurred) }
                    }
                }
                drawOutline(outline, tint.copy(alpha = if (!nativeBlur) 0.97f else if (dark) 0.78f - strength * 0.12f else 0.73f - strength * 0.18f))
                drawOutline(outline, sheen)
                drawContent()
                drawOutline(outline, rim, style = Stroke(1.dp.toPx()))
            }
        }
}

/** Content stays on quiet grouped surfaces; glass is reserved for floating navigation. */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        content = content,
    )
}
