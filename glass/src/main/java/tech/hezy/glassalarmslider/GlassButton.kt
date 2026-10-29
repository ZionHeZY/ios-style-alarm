package tech.hezy.glassalarmslider

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF302E2C),
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val deformation by animateFloatAsState(
        if (pressed && enabled) 1f else 0f,
        if (ValueAnimator.areAnimatorsEnabled()) spring(dampingRatio = 0.8f, stiffness = 500f) else snap(),
        label = "glass button press",
    )
    Box(
        modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp).graphicsLayer {
            scaleX = 1f + deformation * 0.07f
            scaleY = 1f + deformation * 0.04f
            alpha = if (enabled) 1f else 0.45f
        }.clickable(enabled = enabled, interactionSource = interaction, indication = null,
            role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlassButtonSurface(Modifier.matchParentSize(), color, deformation)
        content()
    }
}
