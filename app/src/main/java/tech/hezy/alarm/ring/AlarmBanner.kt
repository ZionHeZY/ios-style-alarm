package tech.hezy.alarm.ring

import android.content.Context
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tech.hezy.alarm.R
import tech.hezy.alarm.ui.IosFontFamily
import kotlin.math.roundToInt

private val CapsuleShape = RoundedCornerShape(45.dp)
private val CapsuleBorder = Color(0xFF222224)
private val TimeOrange = Color(0xFF884A11)
private val LabelOrange = Color(0xFFFF9841)
private val SnoozeCircleBg = Color(0xFF593311)
private val StopCircleBg = Color(0xFF333333)

fun formatAlarmBannerTime(context: Context, hour: Int, minute: Int): String =
    if (DateFormat.is24HourFormat(context)) "%02d:%02d".format(hour, minute)
    else "%d:%02d".format(if (hour % 12 == 0) 12 else hour % 12, minute)

@Composable
fun AlarmBannerPopup(
    time: String,
    label: String,
    snoozeVisible: Boolean,
    visible: Boolean,
    onSnooze: () -> Unit,
    onStop: () -> Unit,
    onOpenFullScreen: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var rendered by remember { mutableStateOf(false) }
    val slideY = remember { Animatable(-260f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val labelStrokePx = with(LocalDensity.current) { 0.85.dp.toPx() }

    LaunchedEffect(visible) {
        if (visible) {
            dragOffset = 0f
            rendered = true
            slideY.animateTo(0f, spring(dampingRatio = 0.80f, stiffness = 400f))
        } else if (rendered) {
            slideY.animateTo(-280f, tween(durationMillis = 160, easing = FastOutSlowInEasing))
            rendered = false
        }
    }

    if (!rendered) return

    Box(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .offset { IntOffset(0, (slideY.value + dragOffset).roundToInt()) },
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 406.dp)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .height(90.dp)
                .shadow(elevation = 12.dp, shape = CapsuleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CapsuleShape)
                .background(Color.Black)
                .border(1.5.dp, CapsuleBorder, CapsuleShape)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffset < -24.dp.toPx()) {
                                scope.launch {
                                    slideY.animateTo(-280f, tween(150))
                                    onStop()
                                }
                            } else {
                                dragOffset = 0f
                            }
                        },
                        onDragCancel = { dragOffset = 0f },
                    ) { _, dragAmount ->
                        dragOffset = (dragOffset + dragAmount).coerceAtMost(12f)
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenFullScreen,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(25.dp))
            RingingAlarmIcon(Modifier.size(43.dp, 47.dp))
            Spacer(Modifier.width(16.dp))
            Box(
                modifier = Modifier.weight(1f).height(52.dp).offset(y = 6.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                Column {
                    Text(
                        text = time,
                        color = TimeOrange,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        style = TextStyle(
                            fontFamily = IosFontFamily,
                            fontFeatureSettings = "tnum",
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                    )
                    Spacer(Modifier.height(2.dp))
                    Box {
                        Text(
                            text = label,
                            color = LabelOrange,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                fontFamily = IosFontFamily,
                                drawStyle = Stroke(width = labelStrokePx, join = StrokeJoin.Round),
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                            ),
                        )
                        Text(
                            text = label,
                            color = LabelOrange,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                fontFamily = IosFontFamily,
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            if (snoozeVisible) {
                BannerCircleButton(
                    bg = SnoozeCircleBg,
                    iconRes = R.drawable.ic_zzz,
                    contentDescription = stringResource(R.string.snooze_row),
                    onClick = onSnooze,
                )
                Spacer(Modifier.width(8.dp))
            }
            BannerCircleButton(
                bg = StopCircleBg,
                iconRes = R.drawable.ic_banner_close,
                contentDescription = stringResource(R.string.cancel),
                onClick = onStop,
            )
            Spacer(Modifier.width(18.dp))
        }
    }
}

@Composable
private fun RingingAlarmIcon(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "alarm_bells")
    val bellAngle by transition.animateFloat(
        initialValue = -5.5f,
        targetValue = 5.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 68, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bell_oscillation",
    )
    Box(modifier) {
        Image(
            painter = painterResource(R.drawable.ic_alarm_bells),
            contentDescription = null,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationZ = bellAngle
                    transformOrigin = TransformOrigin(0.5f, 0.54f)
                }
                .blur(1.8.dp, BlurredEdgeTreatment.Unbounded),
        )
        Image(
            painter = painterResource(R.drawable.ic_alarm_body),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun BannerCircleButton(
    bg: Color,
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "btn_scale")
    Box(
        modifier = Modifier
            .size(50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(50.dp),
        )
    }
}
