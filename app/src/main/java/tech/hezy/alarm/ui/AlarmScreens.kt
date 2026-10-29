package tech.hezy.alarm.ui

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.hezy.alarm.R
import tech.hezy.alarm.domain.Alarm
import tech.hezy.alarm.domain.AlarmTone
import tech.hezy.glassalarmslider.GlassButton
import tech.hezy.glassalarmslider.LocalGlassBackdrop
import tech.hezy.glassalarmslider.glassBackdropSource
import tech.hezy.glassalarmslider.rememberGlassBackdrop
import java.time.DayOfWeek
import java.util.Calendar
import kotlin.math.roundToInt

@Composable
fun AlarmHome(state: AlarmListState, dispatch: (AlarmIntent) -> Unit) {
    val palette = iosPalette(true)
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val backdrop = rememberGlassBackdrop()
    var sheet by remember { mutableFloatStateOf(0f) }
    val headerHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        IosMetrics.screenMargin + 64.dp
    val blurEnd = headerHeight + 24.dp
    val topTint = remember {
        Brush.verticalGradient(listOf(Color(0xB3080808), Color(0x66080808), Color.Transparent))
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - 0.05f * sheet
                    scaleX = scale
                    scaleY = scale
                    shape = RoundedCornerShape(IosMetrics.groupedCorner * sheet)
                    clip = true
                }
                .background(Color.Black),
        ) {
            LazyColumn(
                Modifier.fillMaxSize().glassBackdropSource(backdrop).progressiveTopBlur(blurEnd),
                contentPadding = PaddingValues(top = headerHeight + IosMetrics.hairline, bottom = 28.dp),
            ) {
                if (state.askNotification) {
                    item {
                        Text(
                            stringResource(R.string.allow_notifications),
                            color = palette.secondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(IosMetrics.screenMargin),
                        )
                    }
                }
                item {
                    Column(Modifier.padding(start = IosMetrics.screenMargin, end = IosMetrics.screenMargin, top = 56.dp)) {
                        Text(
                            stringResource(R.string.other_alarms),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                        Separator(palette)
                    }
                }
                items(state.alarms, key = { it.id }) { alarm ->
                    AlarmRow(
                        alarm, palette, is24, state.editing, state.armedDeleteId == alarm.id,
                        onOpen = { dispatch(AlarmIntent.Open(alarm.id)) },
                        onToggle = { dispatch(AlarmIntent.ToggleEnabled(alarm.id)) },
                        onArm = { dispatch(AlarmIntent.ArmDelete(alarm.id)) },
                        onDelete = { dispatch(AlarmIntent.Delete(alarm.id)) },
                    )
                }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
            Box(Modifier.fillMaxWidth().height(blurEnd).background(topTint))
            Box(Modifier.fillMaxWidth().height(headerHeight).pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            })
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = IosMetrics.screenMargin)) {
                CompositionLocalProvider(LocalGlassBackdrop provides backdrop) { AlarmToolbar(state, dispatch) }
            }
        }
        IosEditorSheet(state.editor, onProgress = { sheet = it }, onIntent = dispatch) { draft, editorDispatch ->
            IosEditorStack(draft.page, onBack = { editorDispatch(AlarmIntent.OpenPage(EditorPage.Form)) }) { page ->
                when (page) {
                    EditorPage.Form -> EditorForm(draft, palette, is24, editorDispatch)
                    EditorPage.Repeat -> RepeatPage(draft, palette, editorDispatch)
                    EditorPage.Label -> LabelPage(draft, palette, editorDispatch)
                    EditorPage.Sound -> SoundPage(draft, palette, editorDispatch)
                    EditorPage.Snooze -> SnoozePage(draft, palette, editorDispatch)
                }
            }
        }
    }
}

@Composable
private fun AlarmToolbar(state: AlarmListState, dispatch: (AlarmIntent) -> Unit) {
    Box(Modifier.fillMaxWidth().height(64.dp)) {
        Text(
            stringResource(R.string.alarm_title),
            color = Color.White,
            fontSize = IosMetrics.body,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = IosMetrics.screenMargin),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IosMetrics.labelGap),
        ) {
            if (state.editing) {
                GlassIconButton("check", stringResource(R.string.done), { dispatch(AlarmIntent.ToggleEdit) }, orange = true)
            } else {
                val edit = stringResource(R.string.edit)
                GlassButton(
                    onClick = { dispatch(AlarmIntent.ToggleEdit) },
                    modifier = Modifier.height(IosMetrics.minTouch),
                ) {
                    Text(edit, color = Color.White, fontSize = IosMetrics.body,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = IosMetrics.screenMargin))
                }
            }
            GlassIconButton("plus", stringResource(R.string.add), { dispatch(AlarmIntent.Open(null)) })
        }
    }
}

@Composable
private fun TrashButton(palette: IosPalette, onClick: () -> Unit) {
    val label = stringResource(R.string.delete_alarm)
    Box(
        Modifier.size(50.dp).clip(CircleShape).background(palette.destructive)
            .semantics { contentDescription = label }
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
            val stroke = 1.5.dp.toPx()
            val w = size.width
            val h = size.height
            val cap = androidx.compose.ui.graphics.StrokeCap.Round
            drawRoundRect(Color.White, Offset(w * .27f, h * .27f), Size(w * .46f, h * .62f),
                CornerRadius(w * .06f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawLine(Color.White, Offset(w * .18f, h * .25f), Offset(w * .82f, h * .25f), stroke, cap)
            drawRoundRect(Color.White, Offset(w * .39f, h * .1f), Size(w * .22f, h * .14f),
                CornerRadius(w * .04f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawLine(Color.White, Offset(w * .43f, h * .39f), Offset(w * .43f, h * .76f), stroke, cap)
            drawLine(Color.White, Offset(w * .57f, h * .39f), Offset(w * .57f, h * .76f), stroke, cap)
        }
    }
}

@Composable
private fun Chevron(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(8.dp, 14.dp)) {
        val stroke = 1.8.dp.toPx()
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height / 2), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color, androidx.compose.ui.geometry.Offset(size.width, size.height / 2), androidx.compose.ui.geometry.Offset(0f, size.height), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable
private fun Separator(palette: IosPalette, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(IosMetrics.hairline).background(palette.separator))
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    palette: IosPalette,
    is24: Boolean,
    editing: Boolean,
    armed: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onArm: () -> Unit,
    onDelete: () -> Unit,
) {
    val threshold = with(LocalDensity.current) { 70.dp.toPx() }
    var expanded by remember(alarm.id) { mutableStateOf(false) }
    var dragging by remember(alarm.id) { mutableStateOf(false) }
    var dragOffset by remember(alarm.id) { mutableFloatStateOf(0f) }
    LaunchedEffect(armed) { expanded = armed }
    val settled by animateFloatAsState(
        if (dragging) dragOffset else if (expanded) -threshold else 0f,
        if (dragging) snap() else spring(dampingRatio = 0.9f, stiffness = 650f),
        label = "delete reveal",
    )
    val offset = if (dragging) dragOffset else settled
    val color = if (alarm.enabled) palette.label else palette.disabledTime
    val deleteLabel = stringResource(R.string.delete_alarm)
    Box(Modifier.fillMaxWidth().padding(horizontal = IosMetrics.screenMargin).clipToBounds()) {
        if (offset < -1f) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                TrashButton(palette, onDelete)
            }
        }
        Column(
            Modifier.offset { IntOffset(offset.roundToInt(), 0) }.background(palette.background)
                .pointerInput(threshold) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragOffset = settled; dragging = true },
                        onHorizontalDrag = { change, drag ->
                            change.consume()
                            dragOffset = (dragOffset + drag).coerceIn(-threshold, 0f)
                        },
                        onDragEnd = { expanded = dragOffset < -threshold / 2; dragging = false },
                        onDragCancel = { dragging = false },
                    )
                }
                .clickable { if (expanded) expanded = false else onOpen() },
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 101.dp).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editing) {
                    Box(
                        Modifier.width(40.dp).height(44.dp)
                            .semantics { contentDescription = deleteLabel }
                            .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                if (armed) expanded = !expanded else onArm()
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
                            drawCircle(palette.destructive)
                            drawLine(Color.White, Offset(size.width * .3f, size.height / 2),
                                Offset(size.width * .7f, size.height / 2), 1.8.dp.toPx(),
                                androidx.compose.ui.graphics.StrokeCap.Round)
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        val hour = if (is24) alarm.hour else (alarm.hour % 12).let { if (it == 0) 12 else it }
                        val hours = if (is24) hour.toString().padStart(2, '0') else hour.toString()
                        Text(
                            "$hours:${alarm.minute.toString().padStart(2, '0')}",
                            color = color,
                            fontSize = 54.sp,
                            lineHeight = 60.sp,
                            fontFamily = IosFontFamily,
                            fontWeight = FontWeight(350),
                            letterSpacing = (-1.5).sp,
                            style = TextStyle(
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                textGeometricTransform = TextGeometricTransform(scaleX = 1.08f),
                            ),
                        )
                        if (!is24) {
                            Text(
                                stringResource(if (alarm.hour < 12) R.string.am else R.string.pm),
                                color = color,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(start = 3.dp, bottom = 6.dp),
                            )
                        }
                    }
                    Text(
                        subtitle(alarm),
                        color = color,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    )
                }
                if (editing) {
                    Chevron(palette.separator)
                } else {
                    val description = stringResource(R.string.alarm_title) + " ${alarm.hour}:${alarm.minute}"
                    Box(Modifier.semantics { contentDescription = description }) {
                        IosSwitch(alarm.enabled, { onToggle() }, palette)
                    }
                }
            }
            Separator(palette)
        }
    }
}

@Composable
private fun EditorBar(title: String, dispatch: (AlarmIntent) -> Unit, form: Boolean = false) {
    Box(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = IosMetrics.screenMargin)) {
        GlassIconButton(
            if (form) "close" else "back",
            stringResource(if (form) R.string.cancel else R.string.back),
            { dispatch(if (form) AlarmIntent.CloseEditor else AlarmIntent.OpenPage(EditorPage.Form)) },
            Modifier.align(Alignment.CenterStart),
        )
        Text(
            title,
            color = Color.White,
            fontSize = IosMetrics.body,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )
        if (form) {
            GlassIconButton(
                "check",
                stringResource(R.string.save),
                { dispatch(AlarmIntent.Save) },
                Modifier.align(Alignment.CenterEnd),
                orange = true,
            )
        }
    }
}

@Composable
private fun EditorForm(draft: EditorDraft, palette: IosPalette, is24: Boolean, dispatch: (AlarmIntent) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        EditorBar(stringResource(if (draft.isNew) R.string.add_alarm else R.string.edit_alarm), dispatch, form = true)
        Spacer(Modifier.height(8.dp))
        TimeWheels(draft, palette, is24, dispatch)
        Spacer(Modifier.height(32.dp))
        GroupedCard(palette) {
            GroupedRow(
                stringResource(R.string.repeat),
                repeatSummary(draft.days),
                palette,
                { dispatch(AlarmIntent.OpenPage(EditorPage.Repeat)) },
                showDivider = false,
                showChevron = false,
                valueColor = Color.White,
                trailing = { RepeatIndicator() },
            )
        }
        Text(
            repeatHelp(draft.days),
            color = palette.secondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp),
        )
        Spacer(Modifier.height(26.dp))
        GroupedCard(palette) {
            GroupedRow(
                stringResource(R.string.label),
                draft.label.ifBlank { stringResource(R.string.alarm_default_label) },
                palette,
                { dispatch(AlarmIntent.OpenPage(EditorPage.Label)) },
                showChevron = false,
            )
            GroupedRow(
                stringResource(R.string.sound),
                toneName(draft.tone),
                palette,
                { dispatch(AlarmIntent.OpenPage(EditorPage.Sound)) },
            )
            GroupedRow(
                stringResource(R.string.snooze_row),
                null,
                palette,
                null,
                trailing = { IosSwitch(draft.snooze, { dispatch(AlarmIntent.ToggleSnooze(it)) }, palette) },
                showDivider = draft.snooze,
            )
            if (draft.snooze) {
                GroupedRow(
                    stringResource(R.string.snooze_duration),
                    stringResource(R.string.minutes_count, draft.snoozeMinutes),
                    palette,
                    { dispatch(AlarmIntent.OpenPage(EditorPage.Snooze)) },
                    showDivider = false,
                    showChevron = false,
                    valueColor = palette.blue,
                )
            }
        }
        if (!draft.isNew) {
            Spacer(Modifier.height(36.dp))
            GroupedCard(palette) {
                Box(
                    Modifier.fillMaxWidth().height(IosMetrics.groupedRowMin).clickable { dispatch(AlarmIntent.Delete(draft.id)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.delete_alarm), color = palette.destructive, fontSize = IosMetrics.body)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun RepeatIndicator() {
    androidx.compose.foundation.Canvas(Modifier.padding(start = 10.dp).size(10.dp, 16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()
        drawLine(
            Color.White,
            androidx.compose.ui.geometry.Offset(0f, h * 0.3f),
            androidx.compose.ui.geometry.Offset(w / 2, 0f),
            stroke,
            androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            Color.White,
            androidx.compose.ui.geometry.Offset(w / 2, 0f),
            androidx.compose.ui.geometry.Offset(w, h * 0.3f),
            stroke,
            androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            Color.White,
            androidx.compose.ui.geometry.Offset(0f, h * 0.7f),
            androidx.compose.ui.geometry.Offset(w / 2, h),
            stroke,
            androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            Color.White,
            androidx.compose.ui.geometry.Offset(w / 2, h),
            androidx.compose.ui.geometry.Offset(w, h * 0.7f),
            stroke,
            androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

@Composable
private fun TimeWheels(draft: EditorDraft, palette: IosPalette, is24: Boolean, dispatch: (AlarmIntent) -> Unit) {
    val hourItems = remember(is24) {
        if (is24) (0..23).map { it.toString().padStart(2, '0') } else (1..12).map(Int::toString)
    }
    val minuteItems = remember { (0..59).map { it.toString().padStart(2, '0') } }
    Box(Modifier.fillMaxWidth().height(196.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.padding(horizontal = IosMetrics.screenMargin).fillMaxWidth().height(34.dp)
                .clip(CircleShape).background(palette.highlight),
        )
        Row(Modifier.width(if (is24) 154.dp else 234.dp), horizontalArrangement = Arrangement.Center) {
            WheelColumn(
                hourItems,
                if (is24) draft.hour else (draft.hour + 11) % 12,
                { index -> dispatch(AlarmIntent.ChangeTime(if (is24) index else to24(index, draft.hour >= 12), draft.minute)) },
                palette,
                Modifier.weight(1f),
            )
            WheelColumn(
                minuteItems,
                draft.minute,
                { dispatch(AlarmIntent.ChangeTime(draft.hour, it)) },
                palette,
                Modifier.weight(1f),
            )
            if (!is24) {
                WheelColumn(
                    listOf(stringResource(R.string.am), stringResource(R.string.pm)),
                    if (draft.hour >= 12) 1 else 0,
                    { dispatch(AlarmIntent.ChangeTime(to24((draft.hour + 11) % 12, it == 1), draft.minute)) },
                    palette,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RepeatPage(draft: EditorDraft, palette: IosPalette, dispatch: (AlarmIntent) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        EditorBar(stringResource(R.string.repeat), dispatch)
        val names = longNames()
        val days = weekDays()
        GroupedCard(palette) {
            days.forEachIndexed { index, day ->
                GroupedRow(
                    names.getValue(day),
                    if (day in draft.days) "✓" else "",
                    palette,
                    { dispatch(AlarmIntent.ToggleDay(day)) },
                    showDivider = index != days.lastIndex,
                    showChevron = false,
                    valueColor = palette.blue,
                )
            }
        }
    }
}

@Composable
private fun LabelPage(draft: EditorDraft, palette: IosPalette, dispatch: (AlarmIntent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        EditorBar(stringResource(R.string.label), dispatch)
        GroupedCard(palette) {
            BasicTextField(
                draft.label,
                { dispatch(AlarmIntent.ChangeLabel(it)) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = IosMetrics.body, fontFamily = IosFontFamily),
                cursorBrush = SolidColor(palette.blue),
                modifier = Modifier.fillMaxWidth().padding(IosMetrics.screenMargin),
            )
        }
    }
}

@Composable
private fun SoundPage(draft: EditorDraft, palette: IosPalette, dispatch: (AlarmIntent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        EditorBar(stringResource(R.string.sound), dispatch)
        GroupedCard(palette) {
            AlarmTone.entries.forEachIndexed { index, tone ->
                GroupedRow(
                    toneName(tone),
                    if (tone == draft.tone) "✓" else "",
                    palette,
                    { dispatch(AlarmIntent.SelectTone(tone)) },
                    showDivider = index != AlarmTone.entries.lastIndex,
                    showChevron = false,
                    valueColor = palette.blue,
                )
            }
        }
    }
}

@Composable
private fun SnoozePage(draft: EditorDraft, palette: IosPalette, dispatch: (AlarmIntent) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        EditorBar(stringResource(R.string.snooze_duration), dispatch)
        GroupedCard(palette) {
            (1..15).forEach { minutes ->
                GroupedRow(
                    stringResource(R.string.minutes_count, minutes),
                    if (draft.snoozeMinutes == minutes) "✓" else "",
                    palette,
                    { dispatch(AlarmIntent.ChangeSnoozeMinutes(minutes)) },
                    showDivider = minutes != 15,
                    showChevron = false,
                    valueColor = palette.blue,
                )
            }
        }
    }
}

@Composable
private fun subtitle(alarm: Alarm): String {
    val default = stringResource(R.string.alarm_default_label)
    val label = alarm.label.takeIf { it.isNotBlank() && it != default }
    val repeat = if (alarm.days.isEmpty()) null else repeatSummary(alarm.days)
    return listOfNotNull(label, repeat).joinToString(" · ").ifBlank { default }
}

@Composable
private fun repeatHelp(days: Set<DayOfWeek>): String = stringResource(
    if (days.isEmpty()) R.string.repeat_help_once else R.string.repeat_help_days,
)

@Composable
private fun repeatSummary(days: Set<DayOfWeek>): String {
    val shorts = shortNames()
    val longs = longNames()
    return when {
        days.isEmpty() -> stringResource(R.string.never)
        days.size == 7 -> stringResource(R.string.every_day)
        days == WEEKDAYS -> stringResource(R.string.weekdays)
        days == WEEKENDS -> stringResource(R.string.weekends)
        days.size == 1 -> longs.getValue(days.first())
        else -> weekDays().filter { it in days }.joinToString(" ") { shorts.getValue(it) }
    }
}

@Composable
private fun longNames() = mapOf(
    DayOfWeek.SUNDAY to stringResource(R.string.day_sun),
    DayOfWeek.MONDAY to stringResource(R.string.day_mon),
    DayOfWeek.TUESDAY to stringResource(R.string.day_tue),
    DayOfWeek.WEDNESDAY to stringResource(R.string.day_wed),
    DayOfWeek.THURSDAY to stringResource(R.string.day_thu),
    DayOfWeek.FRIDAY to stringResource(R.string.day_fri),
    DayOfWeek.SATURDAY to stringResource(R.string.day_sat),
)

@Composable
private fun shortNames() = mapOf(
    DayOfWeek.SUNDAY to stringResource(R.string.short_sun),
    DayOfWeek.MONDAY to stringResource(R.string.short_mon),
    DayOfWeek.TUESDAY to stringResource(R.string.short_tue),
    DayOfWeek.WEDNESDAY to stringResource(R.string.short_wed),
    DayOfWeek.THURSDAY to stringResource(R.string.short_thu),
    DayOfWeek.FRIDAY to stringResource(R.string.short_fri),
    DayOfWeek.SATURDAY to stringResource(R.string.short_sat),
)

@Composable
private fun toneName(tone: AlarmTone) = stringResource(
    when (tone) {
        AlarmTone.Rise -> R.string.tone_rise
        AlarmTone.Pulse -> R.string.tone_pulse
        AlarmTone.Glass -> R.string.tone_glass
        AlarmTone.Harbor -> R.string.tone_harbor
        AlarmTone.Clarity -> R.string.tone_clarity
    },
)

private fun weekDays(): List<DayOfWeek> {
    val first = Calendar.getInstance().firstDayOfWeek
    val start = if (first == Calendar.SUNDAY) DayOfWeek.SUNDAY else DayOfWeek.of(first - 1)
    val days = DayOfWeek.entries
    return days.drop(start.ordinal) + days.take(start.ordinal)
}

private fun to24(index: Int, pm: Boolean) = (index + 1) % 12 + if (pm) 12 else 0
private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val WEEKENDS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
