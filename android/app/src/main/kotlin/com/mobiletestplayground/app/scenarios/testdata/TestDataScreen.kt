package com.mobiletestplayground.app.scenarios.testdata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.domain.catalog.PriceFormatter
import com.mobiletestplayground.core.domain.list.DynamicItemsRepository
import com.mobiletestplayground.core.domain.list.GenerateTestDataUseCase
import com.mobiletestplayground.core.domain.seed.SeedData
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ScreenColumn
import com.mobiletestplayground.core.ui.components.SectionTitle
import com.mobiletestplayground.core.ui.components.ValueRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shows the seed data set and offers non-destructive data actions (available in every build,
 * unlike Test Mode). Credentials are listed without passwords; see docs/test-credentials.md.
 */
@HiltViewModel
class TestDataViewModel @Inject constructor(
    private val items: DynamicItemsRepository,
    private val generate: GenerateTestDataUseCase,
) : ViewModel() {
    val itemCount: StateFlow<Int> = items.items.map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    private val mutableStatus = MutableStateFlow<Int?>(null)
    val status: StateFlow<Int?> = mutableStatus.asStateFlow()

    fun reset() {
        viewModelScope.launch {
            items.reset()
            mutableStatus.value = R.string.test_mode_done_reset_data
        }
    }

    fun generate() {
        viewModelScope.launch {
            generate.invoke()
            mutableStatus.value = R.string.test_mode_done_generate_data
        }
    }
}

@Composable
fun TestDataScreen(onBack: () -> Unit, viewModel: TestDataViewModel = hiltViewModel()) {
    val itemCount by viewModel.itemCount.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    PlaygroundScaffold(stringResource(R.string.scenario_test_data), TestTags.TEST_DATA_SCREEN, onBack) { padding ->
        ScreenColumn(padding) {
            SectionTitle(stringResource(R.string.test_data_users))
            SeedData.accounts.forEach { account ->
                val status = if (account.locked) stringResource(R.string.admin_user_locked) else account.user.role.name
                ValueRow(account.user.email, status, TestTags.testDataUser(account.user.id))
            }
            SectionTitle(stringResource(R.string.test_data_products))
            SeedData.products.take(PRODUCT_PREVIEW).forEach { product ->
                ValueRow(product.name, PriceFormatter.format(product.priceCents), TestTags.testDataProduct(product.id))
            }
            Text(stringResource(R.string.test_data_more_products, SeedData.products.size), style = MaterialTheme.typography.bodySmall)
            SectionTitle(stringResource(R.string.lists_dynamic))
            ValueRow(stringResource(R.string.test_data_item_count), itemCount.toString(), TestTags.TEST_DATA_ITEM_COUNT)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::generate, modifier = Modifier.testTag(TestTags.TEST_DATA_GENERATE)) {
                    Text(stringResource(R.string.test_mode_generate_data))
                }
                OutlinedButton(onClick = viewModel::reset, modifier = Modifier.testTag(TestTags.TEST_DATA_RESET)) {
                    Text(stringResource(R.string.test_mode_reset_data))
                }
            }
            status?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag(TestTags.TEST_DATA_STATUS))
            }
        }
    }
}

private const val PRODUCT_PREVIEW = 5
