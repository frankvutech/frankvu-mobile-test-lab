package com.mobiletestplayground.app.scenarios.dynamic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mobiletestplayground.app.R
import com.mobiletestplayground.app.UiTiming
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.LoadingIndicator
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ScreenColumn
import com.mobiletestplayground.core.ui.components.SectionTitle
import com.mobiletestplayground.core.ui.components.ValueRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LoadState { IDLE, LOADING, LOADED }

data class DynamicContentUiState(
    val load: LoadState = LoadState.IDLE,
    val toggleVisible: Boolean = true,
    val delayedEnabled: Boolean = false,
    val countdown: Int? = null,
    val textUpdated: Boolean = false,
)

/**
 * Content that appears, disappears, enables and changes over time, always on the same schedule
 * (see [UiTiming]), for practising explicit waits instead of sleeps.
 */
@HiltViewModel
class DynamicContentViewModel @Inject constructor(private val timing: UiTiming) : ViewModel() {
    private val mutableState = MutableStateFlow(DynamicContentUiState())
    val state: StateFlow<DynamicContentUiState> = mutableState.asStateFlow()
    private var countdownJob: Job? = null

    init {
        // The delayed button enables a fixed time after the screen opens.
        viewModelScope.launch {
            delay(timing.delayedEnableMillis)
            mutableState.update { it.copy(delayedEnabled = true) }
        }
    }

    fun load() {
        if (mutableState.value.load == LoadState.LOADING) return
        mutableState.update { it.copy(load = LoadState.LOADING) }
        viewModelScope.launch {
            delay(timing.dynamicLoadMillis)
            mutableState.update { it.copy(load = LoadState.LOADED) }
        }
    }

    fun toggle() = mutableState.update { it.copy(toggleVisible = !it.toggleVisible) }

    fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (value in COUNTDOWN_FROM downTo 0) {
                mutableState.update { it.copy(countdown = value) }
                if (value > 0) delay(TICK_MILLIS)
            }
        }
    }

    fun changeText() {
        viewModelScope.launch {
            delay(TICK_MILLIS)
            mutableState.update { it.copy(textUpdated = !it.textUpdated) }
        }
    }

    companion object {
        const val COUNTDOWN_FROM = 5
        const val TICK_MILLIS = 1_000L
    }
}

@Composable
fun DynamicContentScreen(onBack: () -> Unit, viewModel: DynamicContentViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlaygroundScaffold(stringResource(R.string.scenario_dynamic), TestTags.DYNAMIC_CONTENT_SCREEN, onBack) { padding ->
        ScreenColumn(padding) {
            SectionTitle(R.string.dynamic_load_title)
            Button(onClick = viewModel::load, modifier = Modifier.testTag(TestTags.DYNAMIC_CONTENT_LOAD)) {
                Text(stringResource(R.string.dynamic_load))
            }
            when (state.load) {
                LoadState.IDLE -> Unit
                LoadState.LOADING -> LoadingIndicator(TestTags.DYNAMIC_CONTENT_LOADING)
                LoadState.LOADED -> Card(Modifier.fillMaxWidth().testTag(TestTags.DYNAMIC_CONTENT_LOADED)) {
                    Text(stringResource(R.string.dynamic_loaded), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }

            SectionTitle(R.string.dynamic_toggle_title)
            OutlinedButton(onClick = viewModel::toggle, modifier = Modifier.testTag(TestTags.DYNAMIC_CONTENT_TOGGLE)) {
                Text(stringResource(if (state.toggleVisible) R.string.dynamic_hide else R.string.dynamic_show))
            }
            AnimatedVisibility(state.toggleVisible) {
                Text(stringResource(R.string.dynamic_toggle_target), modifier = Modifier.testTag(TestTags.DYNAMIC_CONTENT_TOGGLE_TARGET))
            }

            SectionTitle(R.string.dynamic_delayed_title)
            Button(onClick = {}, enabled = state.delayedEnabled, modifier = Modifier.testTag(TestTags.DYNAMIC_CONTENT_DELAYED_BUTTON)) {
                Text(stringResource(R.string.dynamic_delayed_button))
            }
            ValueRow(
                stringResource(R.string.dynamic_delayed_title),
                stringResource(if (state.delayedEnabled) R.string.dynamic_enabled else R.string.dynamic_disabled),
                TestTags.DYNAMIC_CONTENT_DELAYED_STATUS,
            )

            SectionTitle(R.string.dynamic_countdown_title)
            OutlinedButton(onClick = viewModel::startCountdown, modifier = Modifier.testTag(TestTags.DYNAMIC_CONTENT_COUNTDOWN_START)) {
                Text(stringResource(R.string.dynamic_countdown_start))
            }
            val countdown = state.countdown
            ValueRow(
                stringResource(R.string.dynamic_countdown_title),
                when (countdown) {
                    null -> stringResource(R.string.value_not_started)
                    0 -> stringResource(R.string.dynamic_countdown_done)
                    else -> countdown.toString()
                },
                TestTags.DYNAMIC_CONTENT_COUNTDOWN_VALUE,
            )

            SectionTitle(R.string.dynamic_text_title)
            OutlinedButton(onClick = viewModel::changeText, modifier = Modifier.testTag(TestTags.DYNAMIC_CONTENT_CHANGE_TEXT)) {
                Text(stringResource(R.string.dynamic_change_text))
            }
            Text(
                stringResource(if (state.textUpdated) R.string.dynamic_text_updated else R.string.dynamic_text_original),
                modifier = Modifier.testTag(TestTags.DYNAMIC_CONTENT_TEXT),
            )
        }
    }
}
