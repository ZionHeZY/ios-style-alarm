package tech.hezy.glassalarmslider

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun GlassAlarmControls(
    modifier: Modifier = Modifier,
    onSnooze: () -> Unit,
    onStop: () -> Unit,
    settings: GlassAlarmSettings = remember { GlassAlarmSettings() },
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val stopCallback by rememberUpdatedState(onStop)
    val snoozeCallback by rememberUpdatedState(onSnooze)
    val snoozeLabel = stringResource(R.string.snooze)
    val sliderLabel = stringResource(R.string.slide_to_stop)
    val stopLabel = stringResource(R.string.stop_action)
    var progress by remember { mutableFloatStateOf(0f) }
    var pressed by remember { mutableStateOf(false) }
    var completing by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val motionEnabled = ValueAnimator.areAnimatorsEnabled()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 1.16f else 1f,
        animationSpec = if (motionEnabled) spring(
            dampingRatio = if (pressed) 0.7f else 0.8f,
            stiffness = if (pressed) 300f else 180f,
        ) else snap(),
        label = "glass press",
    )
    val shimmer by rememberInfiniteTransition(label = "prompt shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "prompt sweep",
    )
    val renderer = remember(context) { GlassAlarmRenderer(context) }
    DisposableEffect(renderer) {
        onDispose { renderer.release() }
    }

    val release = remember(scope) {
        { complete: Boolean ->
            settling?.cancel()
            pressed = false
            settling = scope.launch {
                completing = complete
                if (complete) {
                    animateProgress(progress, 1f, 0.85f, 400f) { progress = it }
                    delay(150)
                    stopCallback()
                    delay(600)
                }
                animateProgress(progress, 0f, 0.75f, 180f) { progress = it }
                completing = false
            }
        }
    }
    val adjust = remember(release) {
        { value: Float ->
            if (value.isFinite()) {
                settling?.cancel()
                completing = false
                progress = value.coerceIn(0f, 1f)
                if (progress >= 0.88f) release(true)
                true
            } else false
        }
    }

    BoxWithConstraints(modifier.height(240.dp)) {
        require(maxWidth.value.isFinite() && maxWidth >= 160.dp) {
            "GlassAlarmControls requires a bounded width of at least 160dp"
        }
        val geometry = renderer.configure(
            with(density) { maxWidth.toPx() }, density.density, density.fontScale,
            snoozeLabel, sliderLabel, motionEnabled,
        )
        val progressDescription = stringResource(R.string.stop_progress, (progress * 100).roundToInt())
        Canvas(Modifier.matchParentSize().graphicsLayer {
            clip = true
            renderEffect = renderer.effect(settings, progress, pressScale)
        }) {
            drawIntoCanvas { canvas ->
                renderer.draw(canvas.nativeCanvas, settings, progress, pressScale, shimmer)
            }
        }

        Canvas(Modifier.matchParentSize()) {
            val side = (97.5f / 3f) * geometry.unit * pressScale
            val x = geometry.thumbX(progress)
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(x - side / 2f, geometry.thumbY - side / 2f),
                size = Size(side, side),
                cornerRadius = CornerRadius(3f * geometry.unit * pressScale),
            )
        }

        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(104.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
            ) {
                settling?.cancel()
                pressed = false
                completing = false
                progress = 0f
                snoozeCallback()
            }.semantics { contentDescription = snoozeLabel })

        Box(Modifier.offset(y = 120.dp).fillMaxWidth().padding(horizontal = 16.dp).height(120.dp)
            .semantics {
                contentDescription = sliderLabel
                role = Role.Button
                stateDescription = progressDescription
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                onClick(label = stopLabel) { if (!completing) release(true); true }
                setProgress { adjust(it) }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || completing) false
                else when (event.key) {
                    Key.DirectionRight -> adjust(progress + 0.1f)
                    Key.DirectionLeft -> adjust(progress - 0.1f)
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> { release(true); true }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(geometry) {
                try {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        settling?.cancel()
                        completing = false
                        pressed = true
                        val initialProgress = progress
                        val startX = down.position.x
                        var released = false
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            progress = (initialProgress + (change.position.x - startX) / geometry.travel).coerceIn(0f, 1f)
                            released = change.changedToUpIgnoreConsumed()
                            change.consume()
                        } while (change.pressed)
                        release(released && progress >= 0.88f)
                    }
                } finally {
                    pressed = false
                    settling?.cancel()
                    completing = false
                    progress = 0f
                }
            })
    }
}

private suspend fun animateProgress(from: Float, to: Float, damping: Float, stiffness: Float, frame: (Float) -> Unit) {
    if (ValueAnimator.areAnimatorsEnabled()) {
        animate(from, to, animationSpec = spring(dampingRatio = damping, stiffness = stiffness)) { value, _ ->
            frame(value.coerceIn(0f, 1f))
        }
    } else frame(to)
}
