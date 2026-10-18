package com.mobiletestplayground.app.scenarios.gestures

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.domain.misc.GestureType
import com.mobiletestplayground.core.domain.misc.SwipeClassifier
import com.mobiletestplayground.core.domain.misc.ZoomRules
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ScreenColumn
import com.mobiletestplayground.core.ui.components.SectionTitle
import com.mobiletestplayground.core.ui.components.ValueRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class GesturesViewModel @Inject constructor(private val handle: SavedStateHandle) : ViewModel() {
    val lastGesture: StateFlow<String?> = handle.getStateFlow("gesture", null)
    val zoom: StateFlow<Float> = handle.getStateFlow("zoom", ZoomRules.MIN)
    val panX: StateFlow<Int> = handle.getStateFlow("panX", 0)
    val panY: StateFlow<Int> = handle.getStateFlow("panY", 0)
    val dropped: StateFlow<Boolean> = handle.getStateFlow("dropped", false)

    fun record(type: GestureType) {
        handle["gesture"] = type.name
    }

    fun zoomBy(factor: Float) {
        handle["zoom"] = ZoomRules.clamp(zoom.value * factor)
        record(GestureType.PINCH)
    }

    fun setZoom(value: Float) {
        handle["zoom"] = ZoomRules.clamp(value)
        record(GestureType.PINCH)
    }

    fun panBy(dx: Int, dy: Int) {
        handle["panX"] = panX.value + dx
        handle["panY"] = panY.value + dy
        record(GestureType.PAN)
    }

    fun drop(insideZone: Boolean) {
        handle["dropped"] = insideZone
        record(GestureType.DRAG_AND_DROP)
    }

    fun reset() {
        listOf("gesture", "zoom", "panX", "panY", "dropped").forEach { handle.remove<Any>(it) }
    }
}

@Composable
fun GesturesScreen(onBack: () -> Unit, viewModel: GesturesViewModel = hiltViewModel()) {
    val gesture by viewModel.lastGesture.collectAsStateWithLifecycle()
    val zoom by viewModel.zoom.collectAsStateWithLifecycle()
    val panX by viewModel.panX.collectAsStateWithLifecycle()
    val panY by viewModel.panY.collectAsStateWithLifecycle()
    val dropped by viewModel.dropped.collectAsStateWithLifecycle()

    PlaygroundScaffold(stringResource(R.string.scenario_gestures), TestTags.GESTURES_SCREEN, onBack) { padding ->
        ScreenColumn(padding) {
            Text(
                text = gesture?.let { stringResource(R.string.gesture_detected, it) } ?: stringResource(R.string.gesture_none),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(TestTags.GESTURE_RESULT),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val tapLabel = stringResource(R.string.gesture_tap)
                GestureTarget(
                    tapLabel,
                    TestTags.GESTURE_TAP_TARGET,
                    Modifier
                        .pointerInput(Unit) { detectTapGestures(onTap = { viewModel.record(GestureType.TAP) }) }
                        .semantics { onClick(tapLabel) { viewModel.record(GestureType.TAP).let { true } } },
                )
                val doubleLabel = stringResource(R.string.gesture_double_tap)
                GestureTarget(
                    doubleLabel,
                    TestTags.GESTURE_DOUBLE_TAP_TARGET,
                    Modifier
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { viewModel.record(GestureType.DOUBLE_TAP) }) }
                        .semantics { onClick(doubleLabel) { viewModel.record(GestureType.DOUBLE_TAP).let { true } } },
                )
                val longLabel = stringResource(R.string.gesture_long_press)
                GestureTarget(
                    longLabel,
                    TestTags.GESTURE_LONG_PRESS_TARGET,
                    Modifier
                        .pointerInput(Unit) { detectTapGestures(onLongPress = { viewModel.record(GestureType.LONG_PRESS) }) }
                        .semantics { onLongClick(longLabel) { viewModel.record(GestureType.LONG_PRESS).let { true } } },
                )
            }

            SectionTitle(stringResource(R.string.gesture_swipe))
            SwipeArea(onSwipe = viewModel::record)

            SectionTitle(stringResource(R.string.gesture_drag_and_drop))
            DragAndDrop(dropped = dropped, onDrop = viewModel::drop)

            SectionTitle(stringResource(R.string.gesture_zoom))
            ValueRow(
                stringResource(R.string.gesture_zoom),
                stringResource(R.string.gesture_zoom_level, ZoomRules.label(zoom)),
                TestTags.GESTURE_ZOOM_LEVEL,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clipToBounds()
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, gestureZoom, _ -> if (gestureZoom != 1f) viewModel.zoomBy(gestureZoom) }
                    }
                    .testTag(TestTags.GESTURE_ZOOM_AREA),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.gesture_pinch_here),
                    modifier = Modifier.graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.setZoom(zoom + 1f) }, modifier = Modifier.testTag(TestTags.GESTURE_ZOOM_IN)) {
                    Text(stringResource(R.string.gesture_zoom_in))
                }
                OutlinedButton(onClick = { viewModel.setZoom(zoom - 1f) }, modifier = Modifier.testTag(TestTags.GESTURE_ZOOM_OUT)) {
                    Text(stringResource(R.string.gesture_zoom_out))
                }
            }

            SectionTitle(stringResource(R.string.gesture_pan))
            ValueRow(stringResource(R.string.gesture_pan), "$panX, $panY", TestTags.GESTURE_PAN_OFFSET)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clipToBounds()
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .pointerInput(Unit) {
                        detectDragGestures { change, amount ->
                            change.consume()
                            viewModel.panBy(amount.x.roundToInt(), amount.y.roundToInt())
                        }
                    }
                    .testTag(TestTags.GESTURE_PAN_AREA),
            ) {
                Box(
                    Modifier
                        .offset { IntOffset(panX, panY) }
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.secondary),
                )
            }
            OutlinedButton(onClick = viewModel::reset, modifier = Modifier.testTag(TestTags.GESTURE_RESET)) {
                Text(stringResource(R.string.action_reset))
            }
        }
    }
}

@Composable
private fun GestureTarget(label: String, tag: String, modifier: Modifier = Modifier) {
    Box(
        Modifier
            .size(96.dp)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .then(modifier)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SwipeArea(onSwipe: (GestureType) -> Unit) {
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    var total by remember { mutableStateOf(Offset.Zero) }
    val actions = listOf(
        GestureType.SWIPE_LEFT to stringResource(R.string.gesture_swipe_left),
        GestureType.SWIPE_RIGHT to stringResource(R.string.gesture_swipe_right),
        GestureType.SWIPE_UP to stringResource(R.string.gesture_swipe_up),
        GestureType.SWIPE_DOWN to stringResource(R.string.gesture_swipe_down),
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { total = Offset.Zero },
                    onDragEnd = { SwipeClassifier.classify(total.x, total.y, threshold)?.let(onSwipe) },
                ) { change, amount ->
                    change.consume()
                    total += amount
                }
            }
            // TalkBack users cannot swipe on content; custom actions expose the same results.
            .semantics { customActions = actions.map { (type, label) -> CustomAccessibilityAction(label) { onSwipe(type).let { true } } } }
            .testTag(TestTags.GESTURE_SWIPE_AREA),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.gesture_swipe_here))
    }
}

@Composable
private fun DragAndDrop(dropped: Boolean, onDrop: (Boolean) -> Unit) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var itemBounds by remember { mutableStateOf(Rect.Zero) }
    var zoneBounds by remember { mutableStateOf(Rect.Zero) }
    Text(
        stringResource(if (dropped) R.string.gesture_dropped else R.string.gesture_not_dropped),
        modifier = Modifier.testTag(TestTags.GESTURE_DROP_STATUS),
    )
    Row(
        Modifier.fillMaxWidth().height(120.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .size(72.dp)
                .background(MaterialTheme.colorScheme.tertiary)
                .onGloballyPositioned { itemBounds = it.boundsInRoot() }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            onDrop(zoneBounds.contains(itemBounds.center))
                            offset = Offset.Zero
                        },
                    ) { change, amount ->
                        change.consume()
                        offset += amount
                    }
                }
                .testTag(TestTags.GESTURE_DRAG_ITEM),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.gesture_drag_me), color = MaterialTheme.colorScheme.onTertiary) }
        Box(
            Modifier
                .size(110.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary)
                .onGloballyPositioned { zoneBounds = it.boundsInRoot() }
                .testTag(TestTags.GESTURE_DROP_ZONE),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.gesture_drop_here)) }
    }
}
