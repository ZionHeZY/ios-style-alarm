package tech.hezy.glassalarmslider

import android.graphics.RenderNode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.ceil

@Stable
class GlassBackdropState internal constructor() {
    internal val node = RenderNode("glass backdrop").apply { setUseCompositingLayer(true, null) }
    internal var coordinates: LayoutCoordinates? = null
    internal var revision by mutableIntStateOf(0)
    internal var nativeRecording: android.graphics.Canvas? = null
    internal var composeRecording: Canvas? = null
    private var frame = 0

    internal fun recorded() {
        frame++
        revision = frame
    }

    internal fun release() {
        node.discardDisplayList()
        coordinates = null
        nativeRecording = null
        composeRecording = null
    }
}

val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdropState?> { null }
val LocalGlassBackground = staticCompositionLocalOf { Color.Black }

@Composable
fun rememberGlassBackdrop(): GlassBackdropState {
    val backdrop = remember { GlassBackdropState() }
    DisposableEffect(backdrop) { onDispose { backdrop.release() } }
    return backdrop
}

fun Modifier.glassBackdropSource(backdrop: GlassBackdropState, background: Color = Color.Black): Modifier =
    onGloballyPositioned { backdrop.coordinates = it }.drawWithContent {
        val target = drawContext.canvas
        val native = target.nativeCanvas
        require(native.isHardwareAccelerated) { "Glass backdrop requires a hardware canvas" }
        val width = ceil(size.width).toInt()
        val height = ceil(size.height).toInt()
        if (width > 0 && height > 0) {
            backdrop.node.setPosition(0, 0, width, height)
            val recording = backdrop.node.beginRecording(width, height)
            if (backdrop.nativeRecording !== recording) {
                backdrop.nativeRecording = recording
                backdrop.composeRecording = Canvas(recording)
            }
            try {
                recording.drawColor(background.toArgb())
                drawContext.canvas = requireNotNull(backdrop.composeRecording)
                drawContent()
            } finally {
                drawContext.canvas = target
                backdrop.node.endRecording()
            }
            native.drawRenderNode(backdrop.node)
            backdrop.recorded()
        }
    }
