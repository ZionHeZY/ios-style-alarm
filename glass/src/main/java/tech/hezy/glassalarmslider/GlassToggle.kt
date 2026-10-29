package tech.hezy.glassalarmslider

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun GlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    checkedColor: Color = Color(0xFF63CE69),
    uncheckedColor: Color = Color(0xFF454548),
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val callback by rememberUpdatedState(onCheckedChange)
    val currentChecked by rememberUpdatedState(checked)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val direction = if (rtl) -1f else 1f
    val density = LocalDensity.current
    val travel = with(density) { 20.dp.toPx() }
    val flingThreshold = with(density) { 700.dp.toPx() }
    var dragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val motion = ValueAnimator.areAnimatorsEnabled()
    val progress by animateFloatAsState(
        if (dragging) dragProgress else if (checked) 1f else 0f,
        if (dragging || !motion) snap() else spring(dampingRatio = 1f, stiffness = 1000f),
        label = "glass toggle position",
    )
    val active = enabled && (pressed || dragging || abs(progress - if (checked) 1f else 0f) > 0.01f)
    val deformation by animateFloatAsState(
        if (active) 1f else 0f,
        if (motion) spring(dampingRatio = 1f, stiffness = 1000f) else snap(),
        label = "glass toggle press",
    )
    val scaleX by animateFloatAsState(
        if (active) 1.5f else 1f,
        if (motion) spring(dampingRatio = 0.6f, stiffness = 250f) else snap(),
        label = "glass toggle width",
    )
    val scaleY by animateFloatAsState(
        if (active) 1.5f else 1f,
        if (motion) spring(dampingRatio = 0.7f, stiffness = 250f) else snap(),
        label = "glass toggle height",
    )
    Box(
        modifier.size(64.dp, 48.dp).toggleable(
            checked, enabled = enabled, role = Role.Switch,
            interactionSource = interaction, indication = null, onValueChange = { callback(it) },
        ).pointerInput(checked, enabled, rtl, travel, flingThreshold) {
            if (!enabled) return@pointerInput
            val velocity = VelocityTracker()
            try {
                detectHorizontalDragGestures(
                    onDragStart = {
                        velocity.resetTracking()
                        dragProgress = progress
                        dragging = true
                    },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        velocity.addPosition(change.uptimeMillis, change.position)
                        dragProgress = (dragProgress + delta * direction / travel).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        val speed = velocity.calculateVelocity().x * direction
                        val next = if (abs(speed) > flingThreshold) speed > 0f else dragProgress >= 0.5f
                        dragging = false
                        if (next != currentChecked) callback(next)
                    },
                    onDragCancel = { dragging = false },
                )
            } finally {
                dragging = false
            }
        },
    ) {
        GlassToggleSurface(
            Modifier.matchParentSize().graphicsLayer {
                this.scaleX = direction
                alpha = if (enabled) 1f else 0.45f
            },
            progress.coerceIn(0f, 1f), deformation.coerceIn(0f, 1f),
            checkedColor, uncheckedColor, scaleX, scaleY,
        )
    }
}
