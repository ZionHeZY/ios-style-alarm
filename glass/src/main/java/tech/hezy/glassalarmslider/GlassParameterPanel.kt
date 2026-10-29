package tech.hezy.glassalarmslider

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.roundToInt

private data class ParameterAxis(
    val parameter: GlassParameter,
    val name: String,
    val format: String = "%.2f",
    val scale: Float = 1f,
    val unit: String = "",
) {
    fun formatted(value: Float) = String.format(Locale.ROOT, format, value * scale) + unit
    fun normalized(value: Float) = ((value - parameter.min) / (parameter.max - parameter.min)).coerceIn(0f, 1f)
    fun value(position: Float) = parameter.min + position.coerceIn(0f, 1f) * (parameter.max - parameter.min)
}

private data class ParameterPair(val title: String, val x: ParameterAxis, val y: ParameterAxis)

private val sliderPairs = listOf(
    ParameterPair("折射 · 厚度与折射率", ParameterAxis(GlassParameter.THICKNESS, "折射厚度", unit = "pt"), ParameterAxis(GlassParameter.IOR, "折射率")),
    ParameterPair("折射 · 色散与带宽", ParameterAxis(GlassParameter.CHROMATIC_ABERRATION, "色散", "%.3f"), ParameterAxis(GlassParameter.CURVATURE, "折射带宽")),
    ParameterPair("折射 · 位移与放大", ParameterAxis(GlassParameter.LENS_DEPTH, "位移上限", unit = "pt"), ParameterAxis(GlassParameter.MAGNIFICATION, "折射放大", unit = "×")),
    ParameterPair("边缘 · 剖面与宽度", ParameterAxis(GlassParameter.EDGE_POWER, "边缘剖面"), ParameterAxis(GlassParameter.RIM_WIDTH, "细边宽度", unit = "pt")),
    ParameterPair("反射 · 强度与距离", ParameterAxis(GlassParameter.RIM_INTENSITY, "反射强度"), ParameterAxis(GlassParameter.LIGHT_RADIUS, "反射距离", unit = "×")),
    ParameterPair("反射 · 正视率与淡化", ParameterAxis(GlassParameter.FRESNEL_F0, "正视反射率"), ParameterAxis(GlassParameter.AMBIENT_ELEVATION, "两侧淡化")),
    ParameterPair("散射 · 范围与宽度", ParameterAxis(GlassParameter.AMBIENT_RANGE, "边缘范围", unit = "×"), ParameterAxis(GlassParameter.AMBIENT_HEIGHT, "散射宽度", unit = "pt")),
    ParameterPair("光照 · 方向与高光", ParameterAxis(GlassParameter.LIGHT_ANGLE, "方向偏移", "%.0f", 180f / PI.toFloat(), "°"), ParameterAxis(GlassParameter.LIGHT_INTENSITY, "镜面高光")),
    ParameterPair("光照 · 光晕与盘心", ParameterAxis(GlassParameter.AMBIENT_STRENGTH, "环境光晕"), ParameterAxis(GlassParameter.BODY_LIFT, "盘心提亮")),
    ParameterPair("按钮颜色 · 红与绿", ParameterAxis(GlassParameter.ORANGE_R, "按钮红"), ParameterAxis(GlassParameter.ORANGE_G, "按钮绿")),
    ParameterPair("按钮颜色 · 蓝与变暗", ParameterAxis(GlassParameter.ORANGE_B, "按钮蓝"), ParameterAxis(GlassParameter.ORANGE_DIMMING, "按钮变暗")),
    ParameterPair("光源颜色 · 红与绿", ParameterAxis(GlassParameter.LIGHT_R, "光源红"), ParameterAxis(GlassParameter.LIGHT_G, "光源绿")),
    ParameterPair("光源颜色 · 蓝与高光", ParameterAxis(GlassParameter.LIGHT_B, "光源蓝"), ParameterAxis(GlassParameter.LIGHT_INTENSITY, "镜面高光")),
    ParameterPair("玻璃染色 · 红与绿", ParameterAxis(GlassParameter.GLASS_R, "玻璃红"), ParameterAxis(GlassParameter.GLASS_G, "玻璃绿")),
    ParameterPair("玻璃染色 · 蓝与浓度", ParameterAxis(GlassParameter.GLASS_B, "玻璃蓝"), ParameterAxis(GlassParameter.GLASS_A, "染色浓度")),
    ParameterPair("捕获 · 范围与分辨率", ParameterAxis(GlassParameter.SIZE_COEFFICIENT, "捕获范围", unit = "×"), ParameterAxis(GlassParameter.SCALE_COEFFICIENT, "捕获精度", scale = 100f, unit = "%")),
    ParameterPair("捕获 · 磨砂与染色", ParameterAxis(GlassParameter.BLUR_RADIUS, "磨砂半径", unit = "pt"), ParameterAxis(GlassParameter.GLASS_A, "染色浓度")),
)

private val labelStyle = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
private val glassBrush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.7f)))

@Composable
fun GlassParameterPanel(settings: GlassAlarmSettings, modifier: Modifier = Modifier) {
    var selectedPair by rememberSaveable { mutableStateOf(0) }
    var showsPairs by rememberSaveable { mutableStateOf(false) }
    val pair = sliderPairs[selectedPair]
    Column(modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50)).padding(3.dp),
            ) {
                Box(Modifier.size(56.dp, 44.dp).background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(50))
                    .semantics { contentDescription = "滑块"; selected = true }, contentAlignment = Alignment.Center) {
                    BasicText("滑块", style = labelStyle.copy(fontSize = 14.sp))
                }
            }
            Spacer(Modifier.weight(1f))
            PanelIcon(
                Modifier.semantics { contentDescription = "滑块参数组合"; stateDescription = pair.title }
                    .clickable(role = Role.Button) { showsPairs = !showsPairs },
                if (showsPairs) "up" else "down",
            )
            PanelIcon(Modifier.semantics { contentDescription = "重置滑块参数" }
                .clickable(role = Role.Button) { settings.reset() }, "reset")
        }
        if (showsPairs) {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedPair)
            LazyColumn(Modifier.fillMaxWidth().height(168.dp), state = listState) {
                itemsIndexed(sliderPairs) { index, option ->
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).semantics { selected = index == selectedPair }
                            .clickable(role = Role.Button) { selectedPair = index; showsPairs = false },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(option.title, Modifier.weight(1f), labelStyle.copy(fontSize = 13.sp))
                        if (index == selectedPair) PanelIcon(Modifier.size(24.dp), "check")
                    }
                }
            }
        } else {
            key(selectedPair) {
                Row(Modifier.fillMaxWidth().height(168.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ParameterPad(settings, pair)
                    Column(Modifier.weight(1f)) {
                        PrecisionSlider(settings, pair.x, "X")
                        PrecisionSlider(settings, pair.y, "Y")
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelIcon(modifier: Modifier, icon: String) {
    Canvas(modifier.then(if (icon == "check") Modifier else Modifier.size(44.dp))) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = 6.dp.toPx()
        val stroke = 1.5.dp.toPx()
        if (icon == "reset") {
            drawArc(Color.White, 215f, 285f, false, center - Offset(radius, radius), Size(radius * 2, radius * 2), style = Stroke(stroke))
            val path = Path().apply {
                moveTo(center.x - radius, center.y - radius - 1.dp.toPx())
                lineTo(center.x - radius, center.y)
                lineTo(center.x, center.y)
            }
            drawPath(path, Color.White, style = Stroke(stroke))
        } else {
            val path = Path().apply {
                if (icon == "check") {
                    moveTo(center.x - radius, center.y)
                    lineTo(center.x - radius / 3f, center.y + radius / 2f)
                    lineTo(center.x + radius, center.y - radius / 2f)
                } else {
                    val direction = if (icon == "up") -1f else 1f
                    moveTo(center.x - radius, center.y - direction * radius / 3f)
                    lineTo(center.x, center.y + direction * radius / 2f)
                    lineTo(center.x + radius, center.y - direction * radius / 3f)
                }
            }
            drawPath(path, Color.White, style = Stroke(stroke))
        }
    }
}

@Composable
private fun ParameterPad(settings: GlassAlarmSettings, pair: ParameterPair) {
    val x = settings[pair.x.parameter]
    val y = settings[pair.y.parameter]
    var pressed by remember { mutableStateOf(false) }
    val pressure by animateFloatAsState(if (pressed) 1f else 0f, tween(180), label = "pad pressure")
    val shape = RoundedCornerShape(18.dp)
    Canvas(
        Modifier.size(168.dp).background(glassBrush, shape)
            .border(0.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.05f))), shape)
            .semantics {
                contentDescription = "二维调节：X ${pair.x.name}，Y ${pair.y.name}"
                stateDescription = "${pair.x.formatted(x)}，${pair.y.formatted(y)}；使用右侧的玻璃滑块分别精调两个方向"
            }
            .parameterDrag({ pressed = it }) { position, width, height ->
                settings[pair.x.parameter] = pair.x.value((position.x - width * 0.1f) / (width * 0.8f))
                settings[pair.y.parameter] = pair.y.value(1f - (position.y - height * 0.1f) / (height * 0.8f))
            },
    ) {
        val inset = size.width * 0.1f
        val travel = size.width * 0.8f
        val step = travel / 10f
        val point = Offset(inset + pair.x.normalized(x) * travel, inset + (1f - pair.y.normalized(y)) * travel)
        val column = ((point.x - inset) / step).roundToInt()
        val row = ((point.y - inset) / step).roundToInt()
        for (r in 0..10) for (c in 0..10) {
            val dot = Offset(inset + c * step, inset + r * step)
            val proximity = (1f - hypot(dot.x - point.x, dot.y - point.y) / (size.width * 0.55f)).coerceAtLeast(0f)
            val radius = size.width * 0.0125f * (1f + 2f * proximity * pressure)
            val idleOpacity = if (r == row || c == column) 0.85f else 0.4f
            val opacity = (idleOpacity + (0.4f + 0.5f * proximity - idleOpacity) * pressure) * (1f - r / 10f * 0.72f)
            drawCircle(Color.White.copy(alpha = opacity), radius, dot)
        }
        val idlePoint = Offset(inset + column * step, inset + row * step)
        val marker = idlePoint + (point - idlePoint) * pressure
        drawCircle(Color.White, size.width * (0.086f + (0.25f - 0.086f) * pressure) / 2f, marker)
    }
}

@Composable
private fun PrecisionSlider(settings: GlassAlarmSettings, axis: ParameterAxis, dimension: String) {
    val value = settings[axis.parameter]
    val currentValue by rememberUpdatedState(value)
    var pressed by remember { mutableStateOf(false) }
    val expansion by animateFloatAsState(if (pressed) 1.12f else 1f, tween(160), label = "precision pressure")
    fun update(next: Float) { settings[axis.parameter] = next }
    Column(Modifier.fillMaxWidth().height(84.dp), verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("$dimension · ${axis.name}", Modifier.weight(1f), labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(axis.formatted(value), style = labelStyle.copy(fontFamily = FontFamily.Monospace), maxLines = 1)
        }
        Canvas(
            Modifier.fillMaxWidth().height(44.dp)
                .semantics {
                    contentDescription = axis.name
                    stateDescription = axis.formatted(value)
                    progressBarRangeInfo = ProgressBarRangeInfo(value, axis.parameter.min..axis.parameter.max)
                    setProgress { next -> if (next.isFinite()) { update(next); true } else false }
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) false else {
                        val step = (axis.parameter.max - axis.parameter.min) / 100f
                        when (event.key) {
                            Key.DirectionLeft, Key.DirectionDown -> { update(currentValue - step); true }
                            Key.DirectionRight, Key.DirectionUp -> { update(currentValue + step); true }
                            Key.MoveHome -> { update(axis.parameter.min); true }
                            Key.MoveEnd -> { update(axis.parameter.max); true }
                            else -> false
                        }
                    }
                }
                .focusable()
                .parameterDrag({ pressed = it }) { position, width, _ ->
                    val inset = 18.dp.toPx()
                    update(axis.value((position.x - inset) / (width - inset * 2f).coerceAtLeast(1f)))
                },
        ) {
            val inset = 18.dp.toPx()
            val travel = (size.width - inset * 2f).coerceAtLeast(1f)
            val centerY = size.height / 2f
            val position = inset + travel * axis.normalized(value)
            val thickness = 4.dp.toPx()
            drawRoundRect(Color.White.copy(alpha = 0.16f), Offset(inset, centerY - thickness / 2f), Size(travel, thickness), CornerRadius(thickness / 2f))
            drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(inset, centerY - thickness / 2f), Size(travel * axis.normalized(value), thickness), CornerRadius(thickness / 2f))
            val thumb = Size(36.dp.toPx() * expansion, 24.dp.toPx() * expansion)
            val origin = Offset(position - thumb.width / 2f, centerY - thumb.height / 2f)
            drawRoundRect(glassBrush, origin, thumb, CornerRadius(thumb.height / 2f))
            drawRoundRect(Color.White.copy(alpha = 0.32f), origin, thumb, CornerRadius(thumb.height / 2f), style = Stroke(0.5.dp.toPx()))
        }
    }
}

@Composable
private fun Modifier.parameterDrag(
    onPressed: (Boolean) -> Unit,
    onPosition: androidx.compose.ui.unit.Density.(Offset, Float, Float) -> Unit,
): Modifier {
    val currentOnPressed by rememberUpdatedState(onPressed)
    val currentOnPosition by rememberUpdatedState(onPosition)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            currentOnPressed(true)
            try {
                currentOnPosition(down.position, size.width.toFloat(), size.height.toFloat())
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.pressed) currentOnPosition(change.position, size.width.toFloat(), size.height.toFloat())
                    change.consume()
                } while (change.pressed)
            } finally {
                currentOnPressed(false)
            }
        }
    }
}
