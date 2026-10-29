package tech.hezy.alarm.ui

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import tech.hezy.glassalarmslider.GlassButton
import tech.hezy.glassalarmslider.GlassToggle
import tech.hezy.glassalarmslider.LocalGlassBackground

object IosMetrics {
    val minTouch = 44.dp
    val screenMargin = 16.dp
    val rowPaddingH = 16.dp
    val labelGap = 12.dp
    val hairline = 0.5.dp
    val groupedCorner = 28.dp
    val groupedRowMin = 53.dp
    val body: TextUnit = 17.sp
}

data class IosPalette(
    val background: Color = Color.Black,
    val grouped: Color = Color(0xFF2C2C2E),
    val label: Color = Color.White,
    val secondary: Color = Color(0xFF98989D),
    val blue: Color = Color(0xFFF09748),
    val green: Color = Color(0xFF63CE69),
    val separator: Color = Color(0xFF2A2A2C),
    val switchOff: Color = Color(0xFF454548),
    val destructive: Color = Color(0xFFEB534D),
    val highlight: Color = Color(0xFF2C2C2E),
    val disabledTime: Color = Color(0xFF8D8D92),
)

fun iosPalette(@Suppress("UNUSED_PARAMETER") dark: Boolean) = IosPalette()

val IosFontFamily = FontFamily.SansSerif

@Composable
fun IosSwitch(checked: Boolean, onChecked: (Boolean) -> Unit, palette: IosPalette) {
    GlassToggle(checked, onChecked, checkedColor = palette.green, uncheckedColor = palette.switchOff)
}

@Composable
fun GlassIconButton(icon: String, label: String, onClick: () -> Unit,
                    modifier: Modifier = Modifier, orange: Boolean = false) {
    GlassButton(
        onClick = onClick,
        modifier = modifier.size(IosMetrics.minTouch).semantics { contentDescription = label },
        color = if (orange) Color(0xFFF09748) else Color(0xFF302E2C),
    ) {
        Canvas(Modifier.size(25.dp)) {
            val w = size.width
            val stroke = 3.5.dp.toPx()
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
                Color.White, Offset(w * x1, w * y1), Offset(w * x2, w * y2), stroke, StrokeCap.Round,
            )
            when (icon) {
                "plus" -> {
                    line(0.11f, 0.5f, 0.89f, 0.5f)
                    line(0.5f, 0.11f, 0.5f, 0.89f)
                }
                "close" -> {
                    line(0.19f, 0.19f, 0.81f, 0.81f)
                    line(0.81f, 0.19f, 0.19f, 0.81f)
                }
                "check" -> {
                    line(0.12f, 0.54f, 0.41f, 0.79f)
                    line(0.41f, 0.79f, 0.88f, 0.16f)
                }
                "back" -> {
                    line(0.65f, 0.12f, 0.28f, 0.5f)
                    line(0.28f, 0.5f, 0.65f, 0.88f)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelColumn(items: List<String>, selected: Int, onSelected: (Int) -> Unit,
                palette: IosPalette, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    key(items.size) {
        val itemHeight = 34.dp
        val initial = remember { 100 * items.size + selected }
        val state = rememberLazyListState(initialFirstVisibleItemIndex = initial)
        val view = LocalView.current
        val scope = rememberCoroutineScope()
        val callback by rememberUpdatedState(onSelected)
        val pageActive by rememberUpdatedState(LocalIosPageActive.current)
        val fling = rememberSnapFlingBehavior(state)
        val pendingSelections = remember { ArrayDeque<Int>() }
        var reported by remember { mutableIntStateOf(selected) }
        var centeredIndex by remember { mutableIntStateOf(initial) }
        var synchronizing by remember { mutableStateOf(false) }
        var positioned by remember { mutableStateOf(false) }

        LaunchedEffect(selected, pageActive) {
            val echoed = pendingSelections.lastIndexOf(selected)
            if (echoed >= 0) repeat(echoed + 1) { pendingSelections.removeFirst() }
            if (!positioned || !pageActive || (echoed < 0 && selected != reported)) {
                synchronizing = true
                pendingSelections.clear()
                reported = selected
                val target = if (!positioned) 100 * items.size + selected else
                    (centeredIndex + selected - centeredIndex.mod(items.size))
                        .coerceIn(0, items.size * 200 - 1)
                try {
                    state.scrollToItem(target)
                    centeredIndex = target
                    positioned = true
                } finally {
                    synchronizing = false
                }
            }
        }
        LaunchedEffect(state) {
            var gestureStarted = false
            snapshotFlow {
                val info = state.layoutInfo
                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                val index = info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index
                Triple(index, state.isScrollInProgress, synchronizing || !positioned || !pageActive)
            }.distinctUntilChanged().collect { (index, scrolling, syncing) ->
                if (index != null) {
                    centeredIndex = index
                    if (syncing) gestureStarted = false
                    else {
                        if (scrolling) gestureStarted = true
                        val value = index.mod(items.size)
                        if (gestureStarted && value != reported) {
                            reported = value
                            pendingSelections.addLast(value)
                            callback(value)
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            view.playSoundEffect(SoundEffectConstants.CLICK)
                        }
                        if (!scrolling) gestureStarted = false
                    }
                }
            }
        }
        Box(modifier.height(196.dp)) {
            LazyColumn(
                state = state,
                flingBehavior = fling,
                userScrollEnabled = pageActive,
                contentPadding = PaddingValues(vertical = 81.dp),
                modifier = Modifier.fillMaxSize().clearAndSetSemantics {
                    stateDescription = items[reported]
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        reported.toFloat(), 0f..items.lastIndex.toFloat(), (items.size - 2).coerceAtLeast(0),
                    )
                    setProgress { value ->
                        if (!pageActive) return@setProgress false
                        val next = value.roundToInt().coerceIn(items.indices)
                        scope.launch {
                            state.animateScrollToItem(
                                (centeredIndex + next - reported).coerceIn(0, items.size * 200 - 1),
                            )
                        }
                        true
                    }
                },
            ) {
                items(items.size * 200) { index ->
                    Box(
                        Modifier.fillMaxWidth().height(itemHeight).graphicsLayer {
                            val info = state.layoutInfo
                            val row = info.visibleItemsInfo.firstOrNull { it.index == index }
                            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            val distance = row?.let { (it.offset + it.size / 2f - center) / it.size } ?: 4f
                            val angle = (distance * 0.48f).coerceIn(-1.57f, 1.57f)
                            val facing = cos(angle).coerceAtLeast(0f)
                            alpha = facing * facing
                            rotationX = -angle * 180f / kotlin.math.PI.toFloat()
                            cameraDistance = 12f * density
                            translationY = itemHeight.toPx() * (sin(angle) / 0.48f - distance)
                            scaleX = 0.9f + 0.1f * facing
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            items[index.mod(items.size)],
                            color = palette.label,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Normal,
                            style = TextStyle(fontFeatureSettings = "tnum", fontFamily = IosFontFamily),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

internal class IosContinuousShape(private val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }.coerceIn(0f, size.minDimension / 2f)
        val w = size.width
        val h = size.height
        return Outline.Generic(Path().apply {
            moveTo(r, 0f)
            lineTo(w - r, 0f)
            cubicTo(w - r * 0.5f, 0f, w - r * 0.32f, 0f, w - r * 0.16f, r * 0.16f)
            cubicTo(w, r * 0.32f, w, r * 0.5f, w, r)
            lineTo(w, h - r)
            cubicTo(w, h - r * 0.5f, w, h - r * 0.32f, w - r * 0.16f, h - r * 0.16f)
            cubicTo(w - r * 0.32f, h, w - r * 0.5f, h, w - r, h)
            lineTo(r, h)
            cubicTo(r * 0.5f, h, r * 0.32f, h, r * 0.16f, h - r * 0.16f)
            cubicTo(0f, h - r * 0.32f, 0f, h - r * 0.5f, 0f, h - r)
            lineTo(0f, r)
            cubicTo(0f, r * 0.5f, 0f, r * 0.32f, r * 0.16f, r * 0.16f)
            cubicTo(r * 0.32f, 0f, r * 0.5f, 0f, r, 0f)
            close()
        })
    }
}

@Composable
fun GroupedCard(palette: IosPalette, content: @Composable () -> Unit) {
    val shape = remember { IosContinuousShape(IosMetrics.groupedCorner) }
    Column(Modifier.padding(horizontal = IosMetrics.screenMargin).clip(shape).background(palette.grouped)) {
        CompositionLocalProvider(LocalGlassBackground provides palette.grouped) { content() }
    }
}

@Composable
fun GroupedRow(title: String, value: String?, palette: IosPalette, onClick: (() -> Unit)?,
               trailing: @Composable (() -> Unit)? = null, showDivider: Boolean = true,
               showChevron: Boolean = true, valueColor: Color = palette.secondary) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = IosMetrics.groupedRowMin)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = IosMetrics.rowPaddingH), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = palette.label, fontSize = IosMetrics.body, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.width(IosMetrics.labelGap))
            if (value != null) {
                Text(value, color = valueColor, fontSize = IosMetrics.body, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f))
                if (showChevron) Canvas(Modifier.padding(start = 10.dp).size(8.dp, 12.dp)) {
                    val stroke = 2.dp.toPx()
                    drawLine(palette.secondary, Offset(0f, 0f), Offset(size.width, size.height / 2), stroke, StrokeCap.Round)
                    drawLine(palette.secondary, Offset(size.width, size.height / 2), Offset(0f, size.height), stroke, StrokeCap.Round)
                }
            } else Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        if (showDivider) Box(Modifier.padding(horizontal = IosMetrics.rowPaddingH).fillMaxWidth().height(IosMetrics.hairline).background(palette.separator))
    }
}
