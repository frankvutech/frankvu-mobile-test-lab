package com.mobiletestplayground.app.scenarios.errors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.domain.network.NetworkPlaygroundRepository
import com.mobiletestplayground.core.domain.network.NetworkResult
import com.mobiletestplayground.core.domain.network.NetworkScenario
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.ErrorText
import com.mobiletestplayground.core.ui.components.LoadingIndicator
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ScreenColumn
import com.mobiletestplayground.core.ui.components.SectionTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The error state shown, as a stable code also used in the `errors_state` text. */
enum class ErrorState { NONE, LOADING, NETWORK, UNEXPECTED, EMPTY, RECOVERABLE, RECOVERED }

@HiltViewModel
class ErrorHandlingViewModel @Inject constructor(
    private val network: NetworkPlaygroundRepository,
    private val handle: SavedStateHandle,
) : ViewModel() {
    val state: StateFlow<String> = handle.getStateFlow(KEY_STATE, ErrorState.NONE.name)
    val inlineValue: StateFlow<String> = handle.getStateFlow(KEY_INLINE, "")

    private fun set(state: ErrorState) {
        handle[KEY_STATE] = state.name
    }

    /** A real request against the simulated 500 endpoint, mapped by the same code as the Network scenario. */
    fun triggerNetwork() {
        set(ErrorState.LOADING)
        viewModelScope.launch {
            val result = network.fetchProducts(NetworkScenario.SERVER_ERROR)
            set(if (result is NetworkResult.Failure) ErrorState.NETWORK else ErrorState.NONE)
        }
    }

    /** An exception thrown by "business logic" and caught at the ViewModel boundary, never crashing the app. */
    fun triggerUnexpected() {
        val outcome = runCatching { error("Simulated unexpected failure") }
        if (outcome.isFailure) set(ErrorState.UNEXPECTED)
    }

    fun triggerEmpty() = set(ErrorState.EMPTY)

    /** Fails on the first attempt and succeeds on retry, for practising retry flows. */
    fun triggerRecoverable() = set(ErrorState.RECOVERABLE)

    fun retry() {
        when (ErrorState.valueOf(state.value)) {
            ErrorState.NETWORK -> triggerNetwork()
            ErrorState.RECOVERABLE -> set(ErrorState.RECOVERED)
            else -> set(ErrorState.NONE)
        }
    }

    fun dismiss() = set(ErrorState.NONE)

    fun onInlineChange(value: String) {
        handle[KEY_INLINE] = value
    }

    private companion object {
        const val KEY_STATE = "state"
        const val KEY_INLINE = "inline"
    }
}

/** Inline validation: digits only, at most 5. Returns the error message resource or null. */
fun inlineError(value: String): Int? = when {
    value.isEmpty() -> null
    value.any { !it.isDigit() } -> R.string.errors_inline_digits
    value.length > INLINE_MAX -> R.string.errors_inline_length
    else -> null
}

private const val INLINE_MAX = 5

@Composable
fun ErrorHandlingScreen(onBack: () -> Unit, viewModel: ErrorHandlingViewModel = hiltViewModel()) {
    val stateName by viewModel.state.collectAsStateWithLifecycle()
    val inline by viewModel.inlineValue.collectAsStateWithLifecycle()
    val state = ErrorState.valueOf(stateName)

    PlaygroundScaffold(stringResource(R.string.scenario_errors), TestTags.ERRORS_SCREEN, onBack) { padding ->
        ScreenColumn(padding) {
            Text(stringResource(R.string.errors_intro))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::triggerNetwork,
                    modifier = Modifier.weight(1f).testTag(TestTags.ERRORS_TRIGGER_NETWORK),
                ) {
                    Text(stringResource(R.string.errors_network))
                }
                OutlinedButton(
                    onClick = viewModel::triggerUnexpected,
                    modifier = Modifier.weight(1f).testTag(TestTags.ERRORS_TRIGGER_UNEXPECTED),
                ) {
                    Text(stringResource(R.string.errors_unexpected))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::triggerEmpty, modifier = Modifier.weight(1f).testTag(TestTags.ERRORS_TRIGGER_EMPTY)) {
                    Text(stringResource(R.string.errors_empty))
                }
                OutlinedButton(
                    onClick = viewModel::triggerRecoverable,
                    modifier = Modifier.weight(1f).testTag(TestTags.ERRORS_TRIGGER_RECOVERABLE),
                ) {
                    Text(stringResource(R.string.errors_recoverable))
                }
            }
            Text(state.name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag(TestTags.ERRORS_STATE))
            ErrorPanel(state, onRetry = viewModel::retry, onDismiss = viewModel::dismiss)

            SectionTitle(stringResource(R.string.errors_inline_title))
            val error = inlineError(inline)
            OutlinedTextField(
                value = inline,
                onValueChange = viewModel::onInlineChange,
                label = { Text(stringResource(R.string.errors_inline_label)) },
                isError = error != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(TestTags.ERRORS_INLINE_INPUT),
            )
            error?.let { ErrorText(stringResource(it), TestTags.ERRORS_INLINE_ERROR) }
        }
    }
}

@Composable
private fun ErrorPanel(state: ErrorState, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val message = when (state) {
        ErrorState.NONE -> return
        ErrorState.LOADING -> {
            LoadingIndicator(TestTags.ERRORS_LOADING)
            return
        }
        ErrorState.NETWORK -> R.string.errors_message_network
        ErrorState.UNEXPECTED -> R.string.errors_message_unexpected
        ErrorState.EMPTY -> R.string.errors_message_empty
        ErrorState.RECOVERABLE -> R.string.errors_message_recoverable
        ErrorState.RECOVERED -> R.string.errors_message_recovered
    }
    val isError = state != ErrorState.RECOVERED && state != ErrorState.EMPTY
    Card(
        colors = if (isError) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (isError) {
                ErrorText(stringResource(message), TestTags.ERRORS_MESSAGE)
            } else {
                Text(stringResource(message), modifier = Modifier.testTag(TestTags.ERRORS_MESSAGE))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state == ErrorState.NETWORK || state == ErrorState.RECOVERABLE) {
                    Button(onClick = onRetry, modifier = Modifier.testTag(TestTags.ERRORS_RETRY)) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.testTag(TestTags.ERRORS_DISMISS)) {
                    Text(stringResource(R.string.action_dismiss))
                }
            }
        }
    }
}
