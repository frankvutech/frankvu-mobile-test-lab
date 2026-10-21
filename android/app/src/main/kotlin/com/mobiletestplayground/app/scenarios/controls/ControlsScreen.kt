package com.mobiletestplayground.app.scenarios.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.LabeledCheckbox
import com.mobiletestplayground.core.ui.components.LabeledSwitch
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ScreenColumn
import com.mobiletestplayground.core.ui.components.SectionTitle
import com.mobiletestplayground.core.ui.components.ValueRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Every control's value lives in SavedStateHandle, so it survives rotation and process death and
 * tests can verify that it does.
 */
@HiltViewModel
class ControlsViewModel @Inject constructor(private val handle: SavedStateHandle) : ViewModel() {
    val tapCount: StateFlow<Int> = handle.getStateFlow("tap", 0)
    val toggled: StateFlow<Boolean> = handle.getStateFlow("toggle", false)
    val checked: StateFlow<Boolean> = handle.getStateFlow("checked", false)
    val radio: StateFlow<String> = handle.getStateFlow("radio", SIZES.first())
    val slider: StateFlow<Int> = handle.getStateFlow("slider", 50)
    val stepper: StateFlow<Int> = handle.getStateFlow("stepper", 0)
    val switch: StateFlow<Boolean> = handle.getStateFlow("switch", false)
    val progress: StateFlow<Int> = handle.getStateFlow("progress", 0)

    fun tap() = set("tap", tapCount.value + 1)
    fun setToggled(value: Boolean) = set("toggle", value)
    fun setChecked(value: Boolean) = set("checked", value)
    fun setRadio(value: String) = set("radio", value)
    fun setSlider(value: Int) = set("slider", value)
    fun step(delta: Int) = set("stepper", (stepper.value + delta).coerceIn(STEPPER_MIN, STEPPER_MAX))
    fun setSwitch(value: Boolean) = set("switch", value)
    fun advanceProgress() = set("progress", (progress.value + PROGRESS_STEP).coerceAtMost(PROGRESS_MAX))
    fun resetProgress() = set("progress", 0)

    private fun <T> set(key: String, value: T) {
        handle[key] = value
    }

    companion object {
        val SIZES = listOf("small", "medium", "large")
        const val STEPPER_MIN = 0
        const val STEPPER_MAX = 10
        const val PROGRESS_STEP = 25
        const val PROGRESS_MAX = 100
    }
}

@Composable
fun ControlsScreen(onBack: () -> Unit, viewModel: ControlsViewModel = hiltViewModel()) {
    val tapCount by viewModel.tapCount.collectAsStateWithLifecycle()
    val toggled by viewModel.toggled.collectAsStateWithLifecycle()
    val checked by viewModel.checked.collectAsStateWithLifecycle()
    val radio by viewModel.radio.collectAsStateWithLifecycle()
    val slider by viewModel.slider.collectAsStateWithLifecycle()
    val stepper by viewModel.stepper.collectAsStateWithLifecycle()
    val switch by viewModel.switch.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val on = stringResource(R.string.value_on)
    val off = stringResource(R.string.value_off)

    PlaygroundScaffold(stringResource(R.string.scenario_controls), TestTags.CONTROLS_SCREEN, onBack) { padding ->
        ScreenColumn(padding) {
            SectionTitle(R.string.controls_buttons)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::tap, modifier = Modifier.testTag(TestTags.CONTROLS_PRIMARY_BUTTON)) {
                    Text(stringResource(R.string.controls_primary))
                }
                OutlinedButton(onClick = viewModel::tap, modifier = Modifier.testTag(TestTags.CONTROLS_SECONDARY_BUTTON)) {
                    Text(stringResource(R.string.controls_secondary))
                }
            }
            Button(onClick = viewModel::tap, enabled = false, modifier = Modifier.testTag(TestTags.CONTROLS_DISABLED_BUTTON)) {
                Text(stringResource(R.string.controls_disabled))
            }
            ValueRow(stringResource(R.string.controls_tap_count), tapCount.toString(), TestTags.CONTROLS_TAP_COUNT)

            SectionTitle(R.string.controls_toggles)
            val favoriteDescription = stringResource(R.string.controls_favorite)
            val toggleState = if (toggled) on else off
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconToggleButton(
                    checked = toggled,
                    onCheckedChange = viewModel::setToggled,
                    modifier = Modifier
                        .testTag(TestTags.CONTROLS_TOGGLE)
                        .semantics {
                            contentDescription = favoriteDescription
                            stateDescription = toggleState
                        },
                ) {
                    Icon(if (toggled) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null)
                }
                ValueRow(favoriteDescription, toggleState, TestTags.CONTROLS_TOGGLE_STATE)
            }
            LabeledCheckbox(stringResource(R.string.controls_checkbox), checked, viewModel::setChecked, TestTags.CONTROLS_CHECKBOX)
            ValueRow(stringResource(R.string.controls_checkbox), if (checked) on else off, TestTags.CONTROLS_CHECKBOX_STATE)
            LabeledSwitch(stringResource(R.string.controls_switch), switch, viewModel::setSwitch, TestTags.CONTROLS_SWITCH)
            ValueRow(stringResource(R.string.controls_switch), if (switch) on else off, TestTags.CONTROLS_SWITCH_STATE)

            SectionTitle(R.string.controls_size)
            Column(Modifier.selectableGroup()) {
                ControlsViewModel.SIZES.forEach { size ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = radio == size, role = Role.RadioButton, onClick = { viewModel.setRadio(size) })
                            .testTag(TestTags.controlsRadio(size)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = radio == size, onClick = null)
                        Text(stringResource(sizeLabel(size)))
                    }
                }
            }
            ValueRow(stringResource(R.string.controls_size), stringResource(sizeLabel(radio)), TestTags.CONTROLS_RADIO_STATE)

            SectionTitle(R.string.controls_slider)
            val sliderDescription = stringResource(R.string.controls_slider)
            Slider(
                value = slider.toFloat(),
                onValueChange = { viewModel.setSlider(it.toInt()) },
                valueRange = 0f..100f,
                steps = 9,
                modifier = Modifier
                    .testTag(TestTags.CONTROLS_SLIDER)
                    .semantics { contentDescription = sliderDescription },
            )
            ValueRow(sliderDescription, slider.toString(), TestTags.CONTROLS_SLIDER_VALUE)

            SectionTitle(R.string.controls_stepper)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilledIconButton(
                    onClick = { viewModel.step(-1) },
                    enabled = stepper > ControlsViewModel.STEPPER_MIN,
                    modifier = Modifier.testTag(TestTags.CONTROLS_STEPPER_DECREMENT),
                ) { Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.controls_decrement)) }
                Text(stepper.toString(), modifier = Modifier.testTag(TestTags.CONTROLS_STEPPER_VALUE))
                FilledIconButton(
                    onClick = { viewModel.step(1) },
                    enabled = stepper < ControlsViewModel.STEPPER_MAX,
                    modifier = Modifier.testTag(TestTags.CONTROLS_STEPPER_INCREMENT),
                ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.controls_increment)) }
            }

            SectionTitle(R.string.controls_progress)
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.CONTROLS_PROGRESS),
            )
            ValueRow(stringResource(R.string.controls_progress), "$progress%", TestTags.CONTROLS_PROGRESS_VALUE)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::advanceProgress, modifier = Modifier.testTag(TestTags.CONTROLS_PROGRESS_ADVANCE)) {
                    Text(stringResource(R.string.controls_progress_advance))
                }
                OutlinedButton(onClick = viewModel::resetProgress, modifier = Modifier.testTag(TestTags.CONTROLS_PROGRESS_RESET)) {
                    Text(stringResource(R.string.action_reset))
                }
            }
            // Indeterminate indicator for "wait until gone"-style practice; it is always visible here.
            CircularProgressIndicator(modifier = Modifier.testTag(TestTags.CONTROLS_SPINNER))
        }
    }
}

private fun sizeLabel(size: String) = when (size) {
    "small" -> R.string.size_small
    "medium" -> R.string.size_medium
    else -> R.string.size_large
}
