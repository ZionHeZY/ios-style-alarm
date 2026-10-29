package tech.hezy.glassalarmslider

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import kotlin.math.ceil

@Composable
internal fun GlassButtonSurface(modifier: Modifier, color: Color, pressed: Float) {
    val context = LocalContext.current
    val renderer = remember(context) { GlassControlRenderer(context) }
    val backdrop = LocalGlassBackdrop.current
    val background = LocalGlassBackground.current
    DisposableEffect(renderer) { onDispose { renderer.release() } }
    Canvas(modifier.onGloballyPositioned { renderer.coordinates = it }) {
        backdrop?.revision
        drawIntoCanvas {
            renderer.draw(it.nativeCanvas, size.width, size.height, density,
                color, Color.Transparent, background, 0f, pressed, backdrop, toggle = false)
        }
    }
}

@Composable
internal fun GlassToggleSurface(
    modifier: Modifier,
    progress: Float,
    pressed: Float,
    checkedColor: Color,
    uncheckedColor: Color,
    scaleX: Float,
    scaleY: Float,
) {
    val context = LocalContext.current
    val renderer = remember(context) { GlassControlRenderer(context) }
    val background = LocalGlassBackground.current
    DisposableEffect(renderer) { onDispose { renderer.release() } }
    Canvas(modifier) {
        drawIntoCanvas {
            renderer.draw(it.nativeCanvas, size.width, size.height, density,
                lerp(uncheckedColor, checkedColor, progress), Color.White, background,
                progress, pressed, null, toggle = true, scaleX = scaleX, scaleY = scaleY)
        }
    }
}

private class GlassControlRenderer(context: Context) {
    private val shader = RuntimeShader(shaderSource(context))
    private val scene = RenderNode("glass control source").apply { setUseCompositingLayer(true, null) }
    private val lens = RenderNode("glass control lens")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = RectF()
    internal var coordinates: LayoutCoordinates? = null
    private val previous = FloatArray(12) { Float.NaN }
    private var previousTint = Color.Unspecified

    fun draw(
        canvas: android.graphics.Canvas,
        width: Float,
        height: Float,
        unit: Float,
        base: Color,
        thumb: Color,
        background: Color,
        progress: Float,
        pressed: Float,
        backdrop: GlassBackdropState?,
        toggle: Boolean,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
    ) {
        require(canvas.isHardwareAccelerated) { "Glass controls require a hardware canvas" }
        if (width <= 0f || height <= 0f) return
        val pad = ceil(16f * unit)
        val w = ceil(width + pad * 2).toInt()
        val h = ceil(height + pad * 2).toInt()
        scene.setPosition(0, 0, w, h)
        lens.setPosition(0, 0, w, h)
        val halfW = if (toggle) 20f * scaleX * unit else width / 2
        val halfH = if (toggle) 12f * scaleY * unit else height / 2
        val centerX = if (toggle) 22f * unit + progress * (width - 44f * unit) else width / 2
        val centerY = height / 2
        val recording = scene.beginRecording(w, h)
        try {
            recording.drawColor(background.toArgb())
            recording.translate(pad, pad)
            val sourceCoordinates = backdrop?.coordinates
            val destinationCoordinates = coordinates
            if (backdrop != null && backdrop.node.hasDisplayList() && sourceCoordinates?.isAttached == true && destinationCoordinates?.isAttached == true) {
                val offset = sourceCoordinates.localPositionOf(destinationCoordinates, Offset.Zero)
                val save = recording.save()
                recording.translate(-offset.x, -offset.y)
                recording.drawRenderNode(backdrop.node)
                recording.restoreToCount(save)
            }
            if (toggle) {
                val sourceScaleX = 2f / 3f + pressed / 12f
                val sourceScaleY = 0.001f + pressed * 0.749f
                track.set(
                    centerX * (1f - sourceScaleX), centerY - 14f * unit * sourceScaleY,
                    centerX + (width - centerX) * sourceScaleX, centerY + 14f * unit * sourceScaleY,
                )
                paint.color = base.toArgb()
                recording.drawRoundRect(track, 14f * unit * sourceScaleY, 14f * unit * sourceScaleY, paint)
            }
        } finally {
            scene.endRecording()
        }
        val tint = if (toggle) thumb else base
        val alpha = if (toggle) 1f - pressed else 0.9f
        val lift = if (toggle) 0f else 0.03f
        var dirty = false
        if (previous[0] != w.toFloat() || previous[1] != h.toFloat()) {
            shader.setFloatUniform("resolution", w.toFloat(), h.toFloat())
            previous[0] = w.toFloat(); previous[1] = h.toFloat(); dirty = true
        }
        if (previous[2] != centerX || previous[3] != centerY || previous[4] != halfW || previous[5] != halfH || previous[6] != pad) {
            shader.setFloatUniform("lensBounds", centerX + pad, centerY + pad, halfW, halfH)
            previous[2] = centerX; previous[3] = centerY; previous[4] = halfW
            previous[5] = halfH; previous[6] = pad; dirty = true
        }
        if (previous[7] != unit) {
            shader.setFloatUniform("density", unit)
            previous[7] = unit; dirty = true
        }
        if (previous[8] != pressed) {
            shader.setFloatUniform("press", pressed)
            previous[8] = pressed; dirty = true
        }
        if (previousTint != tint || previous[9] != alpha) {
            shader.setFloatUniform("tint", tint.red, tint.green, tint.blue, alpha)
            previousTint = tint; previous[9] = alpha; dirty = true
        }
        if (previous[10] != lift) {
            shader.setFloatUniform("bodyLift", lift)
            previous[10] = lift; dirty = true
        }
        val toggleMode = if (toggle) 1f else 0f
        if (previous[11] != toggleMode) {
            shader.setFloatUniform("toggle", toggleMode)
            previous[11] = toggleMode; dirty = true
        }
        if (dirty) lens.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "scene"))
        val output = lens.beginRecording(w, h)
        try { output.drawRenderNode(scene) } finally { lens.endRecording() }
        val save = canvas.save()
        try {
            if (toggle) {
                paint.color = base.toArgb()
                track.set(0f, centerY - 14f * unit, width, centerY + 14f * unit)
                canvas.drawRoundRect(track, 14f * unit, 14f * unit, paint)
            }
            canvas.translate(-pad, -pad)
            canvas.drawRenderNode(lens)
        } finally { canvas.restoreToCount(save) }
    }

    fun release() {
        scene.discardDisplayList()
        lens.discardDisplayList()
        lens.setRenderEffect(null)
        coordinates = null
    }

    companion object {
        private var source: String? = null
        @Synchronized private fun shaderSource(context: Context): String = source ?: context.resources
            .openRawResource(R.raw.glass_control).bufferedReader().use { it.readText() }.also { source = it }
    }
}
