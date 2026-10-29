package tech.hezy.alarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import tech.hezy.glassalarmslider.LocalGlassBackground

internal val LocalIosPageActive = compositionLocalOf { true }

private val EditorSurface = Color(0xFF1C1C1E)
private val EditorEase = CubicBezierEasing(0.22f, 0.8f, 0.25f, 1f)

@Composable
internal fun IosEditorSheet(
    draft: EditorDraft?,
    onProgress: (Float) -> Unit,
    onIntent: (AlarmIntent) -> Unit,
    content: @Composable (EditorDraft, (AlarmIntent) -> Unit) -> Unit,
) {
    var retained by remember { mutableStateOf(draft) }
    val shown = draft ?: retained
    val session = shown?.id
    var closing by remember(session) { mutableStateOf(false) }
    var notifyClose by remember(session) { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var animation by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val latestDraft by rememberUpdatedState(draft)
    val dispatch by rememberUpdatedState(onIntent)
    val reportProgress by rememberUpdatedState(onProgress)
    val focus = LocalFocusManager.current
    SideEffect { if (draft != null) retained = draft }
    LaunchedEffect(Unit) { snapshotFlow { progress }.collect { reportProgress(it) } }
    DisposableEffect(Unit) { onDispose { reportProgress(0f) } }

    LaunchedEffect(session, draft == null, closing) {
        animation?.cancel()
        if (shown == null) {
            progress = 0f
            reportProgress(0f)
            return@LaunchedEffect
        }
        val exiting = draft == null || closing
        if (exiting) focus.clearFocus(force = true)
        animation = launch {
            if (exiting) {
                animate(progress, 0f, animationSpec = tween(280, easing = EditorEase)) { value, _ ->
                    progress = value
                }
                progress = 0f
                reportProgress(0f)
                if (notifyClose && latestDraft?.id == session) dispatch(AlarmIntent.CloseEditor)
                retained = null
            } else {
                animate(progress, 1f, animationSpec = spring(dampingRatio = 1f, stiffness = 420f)) { value, _ ->
                    progress = value.coerceIn(0f, 1f)
                }
                progress = 1f
            }
        }
        animation?.join()
    }

    if (shown == null) return
    val send: (AlarmIntent) -> Unit = { intent ->
        if (latestDraft != null && !closing) {
            when (intent) {
                AlarmIntent.CloseEditor -> {
                    notifyClose = true
                    closing = true
                }
                AlarmIntent.Save -> {
                    closing = true
                    dispatch(intent)
                }
                is AlarmIntent.Delete -> {
                    if (intent.id == shown.id) closing = true
                    dispatch(intent)
                }
                else -> dispatch(intent)
            }
        }
    }
    BackHandler {
        if (shown.page == EditorPage.Form) send(AlarmIntent.CloseEditor)
        else send(AlarmIntent.OpenPage(EditorPage.Form))
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f * progress)).blockEditorInput())
        Box(
            Modifier.fillMaxSize().statusBarsPadding().padding(top = 10.dp)
                .pointerInput(session, draft == null, closing) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (down.position.y > 76.dp.toPx() || latestDraft == null || closing || progress < 0.99f) {
                            return@awaitEachGesture
                        }
                        val velocity = VelocityTracker()
                        velocity.addPosition(down.uptimeMillis, down.position)
                        val start = progress
                        var claimed = false
                        var finished = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            velocity.addPosition(change.uptimeMillis, change.position)
                            val dy = change.position.y - down.position.y
                            val dx = change.position.x - down.position.x
                            if (!claimed) {
                                if (!change.pressed || change.isConsumed ||
                                    abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy)) break
                                if (dy > viewConfiguration.touchSlop) {
                                    animation?.cancel()
                                    claimed = true
                                }
                            }
                            if (claimed) {
                                change.consume()
                                progress = (start - (dy - viewConfiguration.touchSlop) /
                                    size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                            }
                            if (!change.pressed) {
                                finished = true
                                break
                            }
                        }
                        if (!claimed) return@awaitEachGesture
                        if (latestDraft != null && !closing) {
                            val speed = velocity.calculateVelocity().y
                            if (finished && (progress < 0.8f || (progress < 0.98f && speed > 900.dp.toPx()))) {
                                notifyClose = true
                                closing = true
                            } else {
                                animation?.cancel()
                                animation = scope.launch {
                                    animate(progress, 1f, animationSpec = spring(dampingRatio = 1f, stiffness = 500f)) { value, _ ->
                                        progress = value.coerceIn(0f, 1f)
                                    }
                                    progress = 1f
                                }
                            }
                        }
                    }
                }
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(EditorSurface)
                .imePadding()
        ) {
            key(shown.id) {
                val interactive = draft != null && !closing
                CompositionLocalProvider(
                    LocalIosPageActive provides interactive,
                    LocalGlassBackground provides EditorSurface,
                ) {
                    Box(Modifier.fillMaxSize().editorInteraction(interactive)) {
                        content(shown, send)
                    }
                }
            }
        }
    }
}

@Composable
internal fun IosEditorStack(
    page: EditorPage,
    onBack: () -> Unit,
    content: @Composable (EditorPage) -> Unit,
) {
    var retainedChild by remember { mutableStateOf(page.takeUnless { it == EditorPage.Form }) }
    val child = page.takeUnless { it == EditorPage.Form } ?: retainedChild
    var progress by remember { mutableFloatStateOf(if (child == null) 0f else 1f) }
    var dragging by remember { mutableStateOf(false) }
    var animation by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val back by rememberUpdatedState(onBack)
    val currentPage by rememberUpdatedState(page)
    val sheetActive = LocalIosPageActive.current
    val active by rememberUpdatedState(sheetActive)
    val focus = LocalFocusManager.current
    SideEffect { if (page != EditorPage.Form) retainedChild = page }
    LaunchedEffect(page) {
        animation?.cancel()
        dragging = false
        focus.clearFocus(force = true)
        animation = launch {
            val target = if (page == EditorPage.Form) 0f else 1f
            animate(progress, target, animationSpec = tween(400, easing = EditorEase)) { value, _ ->
                progress = value
            }
            progress = target
            if (page == EditorPage.Form) retainedChild = null
        }
        animation?.join()
    }
    Box(
        Modifier.fillMaxSize().clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!active || currentPage == EditorPage.Form || progress < 0.99f || down.position.x > 28.dp.toPx()) {
                        return@awaitEachGesture
                    }
                    val velocity = VelocityTracker()
                    velocity.addPosition(down.uptimeMillis, down.position)
                    val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                        animation?.cancel()
                        dragging = true
                        focus.clearFocus(force = true)
                        progress = (progress - over / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        velocity.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                    } ?: return@awaitEachGesture
                    val finished = horizontalDrag(drag.id) { change ->
                        velocity.addPosition(change.uptimeMillis, change.position)
                        val delta = change.position.x - change.previousPosition.x
                        progress = (progress - delta / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        change.consume()
                    }
                    val speed = velocity.calculateVelocity().x
                    val pop = finished && (progress < 0.6f || (progress < 0.98f && speed > 900.dp.toPx()))
                    animation?.cancel()
                    animation = scope.launch {
                        val target = if (pop) 0f else 1f
                        animate(progress, target, animationSpec = tween(240, easing = EditorEase)) { value, _ ->
                            progress = value
                        }
                        progress = target
                        if (pop && currentPage != EditorPage.Form) back()
                        dragging = false
                    }
                }
            },
    ) {
        val formActive = sheetActive && page == EditorPage.Form && progress == 0f && !dragging
        Box(
            Modifier.fillMaxSize().graphicsLayer { translationX = -size.width * 0.25f * progress }
                .editorInteraction(formActive),
        ) {
            CompositionLocalProvider(LocalIosPageActive provides formActive) { content(EditorPage.Form) }
            if (progress > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.24f * progress)))
        }
        if (child != null) {
            val childActive = sheetActive && page == child && progress == 1f && !dragging
            key(child) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer { translationX = size.width * (1f - progress) }
                        .background(EditorSurface).editorInteraction(childActive),
                ) {
                    CompositionLocalProvider(LocalIosPageActive provides childActive) { content(child) }
                }
            }
        }
    }
}

private fun Modifier.editorInteraction(active: Boolean): Modifier =
    (if (active) this else focusProperties { canFocus = false }.clearAndSetSemantics { })
        .editorPointerInput(active)

private fun Modifier.blockEditorInput(): Modifier = editorPointerInput(false)

private fun Modifier.editorPointerInput(active: Boolean): Modifier = pointerInput(active) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!active) {
            down.consume()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}
