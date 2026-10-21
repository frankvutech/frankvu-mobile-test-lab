package com.mobiletestplayground.app.scenarios.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.domain.catalog.PriceFormatter
import com.mobiletestplayground.core.domain.model.Product
import com.mobiletestplayground.core.domain.network.NetworkError
import com.mobiletestplayground.core.domain.network.NetworkPlaygroundRepository
import com.mobiletestplayground.core.domain.network.NetworkResult
import com.mobiletestplayground.core.domain.network.NetworkScenario
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.ErrorText
import com.mobiletestplayground.core.ui.components.LoadingIndicator
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ScreenColumn
import com.mobiletestplayground.core.ui.components.textTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NetworkUiState {
    data object Idle : NetworkUiState
    data class Loading(val scenario: NetworkScenario) : NetworkUiState
    data class Success(val products: List<Product>) : NetworkUiState
    data object Empty : NetworkUiState
    data class Error(val error: NetworkError) : NetworkUiState
}

@HiltViewModel
class NetworkViewModel @Inject constructor(
    private val repository: NetworkPlaygroundRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow<NetworkUiState>(NetworkUiState.Idle)
    val state: StateFlow<NetworkUiState> = mutableState.asStateFlow()
    private var lastScenario: NetworkScenario? = null
    private var job: Job? = null

    fun load(scenario: NetworkScenario) {
        lastScenario = scenario
        job?.cancel()
        mutableState.value = NetworkUiState.Loading(scenario)
        job = viewModelScope.launch {
            mutableState.value = when (val result = repository.fetchProducts(scenario)) {
                is NetworkResult.Success -> NetworkUiState.Success(result.data)
                NetworkResult.Empty -> NetworkUiState.Empty
                is NetworkResult.Failure -> NetworkUiState.Error(result.error)
            }
        }
    }

    fun retry() {
        lastScenario?.let(::load)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NetworkScreen(onBack: () -> Unit, viewModel: NetworkViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlaygroundScaffold(stringResource(R.string.scenario_network), TestTags.NETWORK_SCREEN, onBack) { padding ->
        ScreenColumn(padding) {
            Text(stringResource(R.string.network_intro), modifier = Modifier.testTag(textTag(R.string.network_intro)))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NetworkScenario.entries.forEach { scenario ->
                    OutlinedButton(
                        onClick = { viewModel.load(scenario) },
                        enabled = state !is NetworkUiState.Loading,
                        modifier = Modifier.testTag(TestTags.networkScenario(scenario.id)),
                    ) { Text(stringResource(scenario.label())) }
                }
            }
            when (val current = state) {
                NetworkUiState.Idle -> Text(stringResource(R.string.network_idle), modifier = Modifier.testTag(TestTags.NETWORK_IDLE))
                is NetworkUiState.Loading -> LoadingIndicator(TestTags.NETWORK_LOADING)
                NetworkUiState.Empty -> Text(stringResource(R.string.network_empty), modifier = Modifier.testTag(TestTags.NETWORK_EMPTY))
                is NetworkUiState.Success -> Card(Modifier.fillMaxWidth().testTag(TestTags.NETWORK_SUCCESS)) {
                    Text(
                        stringResource(R.string.network_success, current.products.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    current.products.forEach { product ->
                        ListItem(
                            headlineContent = { Text(product.name) },
                            supportingContent = { Text(PriceFormatter.format(product.priceCents)) },
                            modifier = Modifier.testTag(TestTags.networkResultItem(product.id)),
                        )
                    }
                }
                is NetworkUiState.Error -> Card(Modifier.fillMaxWidth().testTag(TestTags.NETWORK_ERROR)) {
                    ErrorText(stringResource(current.error.message(), current.error.code()), TestTags.NETWORK_ERROR_MESSAGE)
                    Button(onClick = viewModel::retry, modifier = Modifier.testTag(TestTags.NETWORK_RETRY)) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        }
    }
}

private fun NetworkScenario.label() = when (this) {
    NetworkScenario.SUCCESS -> R.string.network_scenario_success
    NetworkScenario.SLOW -> R.string.network_scenario_slow
    NetworkScenario.TIMEOUT -> R.string.network_scenario_timeout
    NetworkScenario.BAD_REQUEST -> R.string.network_scenario_400
    NetworkScenario.UNAUTHORIZED -> R.string.network_scenario_401
    NetworkScenario.FORBIDDEN -> R.string.network_scenario_403
    NetworkScenario.NOT_FOUND -> R.string.network_scenario_404
    NetworkScenario.SERVER_ERROR -> R.string.network_scenario_500
    NetworkScenario.EMPTY -> R.string.network_scenario_empty
    NetworkScenario.MALFORMED -> R.string.network_scenario_malformed
}

/** Every error message takes the status code as an argument; messages without one ignore it. */
fun NetworkError.message() = when (this) {
    NetworkError.Timeout -> R.string.network_error_timeout
    NetworkError.BadRequest -> R.string.network_error_400
    NetworkError.Unauthorized -> R.string.network_error_401
    NetworkError.Forbidden -> R.string.network_error_403
    NetworkError.NotFound -> R.string.network_error_404
    is NetworkError.Server -> R.string.network_error_server
    NetworkError.MalformedResponse -> R.string.network_error_malformed
    NetworkError.Connectivity -> R.string.network_error_connectivity
    is NetworkError.UnexpectedStatus -> R.string.network_error_unexpected
}

fun NetworkError.code(): Int = when (this) {
    is NetworkError.Server -> code
    is NetworkError.UnexpectedStatus -> code
    else -> 0
}
