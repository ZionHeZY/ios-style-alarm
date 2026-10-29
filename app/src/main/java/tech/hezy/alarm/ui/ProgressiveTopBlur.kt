package tech.hezy.alarm.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tech.hezy.alarm.R

@Composable
internal fun Modifier.progressiveTopBlur(fadeEnd: Dp): Modifier {
    val resources = LocalContext.current.resources
    val density = LocalDensity.current
    val source = remember(resources) {
        resources.openRawResource(R.raw.progressive_top_blur).bufferedReader().use { it.readText() }
    }
    val (horizontal, vertical) = remember(source, density.density, fadeEnd) {
        val endPx = with(density) { fadeEnd.toPx() }
        val sigmaPx = with(density) { 12.dp.toPx() }
        fun pass(x: Float, y: Float): RenderEffect {
            val shader = RuntimeShader(source).apply {
                setFloatUniform("fadeEnd", endPx)
                setFloatUniform("maxSigma", sigmaPx)
                setFloatUniform("axis", x, y)
            }
            return RenderEffect.createRuntimeShaderEffect(shader, "content")
        }
        pass(1f, 0f).asComposeRenderEffect() to pass(0f, 1f).asComposeRenderEffect()
    }
    return graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
        renderEffect = vertical
    }.graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
        renderEffect = horizontal
    }
}
