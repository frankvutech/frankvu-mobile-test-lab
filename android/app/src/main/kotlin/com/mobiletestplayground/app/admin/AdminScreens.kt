package com.mobiletestplayground.app.admin

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.domain.Outcome
import com.mobiletestplayground.core.domain.catalog.PriceFormatter
import com.mobiletestplayground.core.domain.catalog.ProductRepository
import com.mobiletestplayground.core.domain.model.Product
import com.mobiletestplayground.core.domain.seed.SeedData
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ScreenColumn
import com.mobiletestplayground.core.ui.components.SectionTitle
import com.mobiletestplayground.core.ui.components.ValueRow
import com.mobiletestplayground.core.ui.components.textTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Admin screens only render after the navigation guard granted access (role ADMIN). */
@Composable
fun AdminDashboardScreen(onBack: () -> Unit, onProducts: () -> Unit, onUsers: () -> Unit) {
    PlaygroundScaffold(stringResource(R.string.admin_title), TestTags.ADMIN_DASHBOARD, onBack) { padding ->
        ScreenColumn(padding) {
            Text(stringResource(R.string.admin_intro), modifier = Modifier.testTag(textTag(R.string.admin_intro)))
            Button(onClick = onProducts, modifier = Modifier.fillMaxWidth().testTag(TestTags.ADMIN_OPEN_PRODUCTS)) {
                Text(stringResource(R.string.admin_products))
            }
            Button(onClick = onUsers, modifier = Modifier.fillMaxWidth().testTag(TestTags.ADMIN_OPEN_USERS)) {
                Text(stringResource(R.string.admin_users))
            }
        }
    }
}

@HiltViewModel
class AdminViewModel @Inject constructor(private val repository: ProductRepository) : ViewModel() {
    private val mutableProducts = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = mutableProducts.asStateFlow()
    val accounts: List<SeedData.SeedAccount> = SeedData.accounts

    init {
        viewModelScope.launch {
            (repository.products() as? Outcome.Success)?.let { mutableProducts.value = it.value }
        }
    }
}

@Composable
fun AdminProductsScreen(onBack: () -> Unit, viewModel: AdminViewModel = hiltViewModel()) {
    PlaygroundScaffold(stringResource(R.string.admin_products), TestTags.ADMIN_PRODUCTS, onBack) { padding ->
        val products by viewModel.products.collectAsStateWithLifecycle()
        ScreenColumn(padding) {
            products.forEach { product ->
                ValueRow(product.name, PriceFormatter.format(product.priceCents), TestTags.adminProduct(product.id))
            }
        }
    }
}

@Composable
fun AdminUsersScreen(onBack: () -> Unit, viewModel: AdminViewModel = hiltViewModel()) {
    PlaygroundScaffold(stringResource(R.string.admin_users), TestTags.ADMIN_USERS, onBack) { padding ->
        ScreenColumn(padding) {
            viewModel.accounts.forEach { account ->
                val status = if (account.locked) stringResource(R.string.admin_user_locked) else account.user.role.name
                ValueRow(account.user.email, status, TestTags.adminUser(account.user.id))
            }
        }
    }
}

@Composable
fun AccessDeniedScreen(onBack: () -> Unit) {
    PlaygroundScaffold(stringResource(R.string.access_denied_title), TestTags.ACCESS_DENIED_SCREEN, onBack) { padding ->
        ScreenColumn(padding) {
            Icon(Icons.Filled.Block, contentDescription = null)
            SectionTitle(R.string.access_denied_title)
            Text(
                stringResource(R.string.access_denied_message),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(TestTags.ACCESS_DENIED_MESSAGE),
            )
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().testTag(TestTags.ACCESS_DENIED_BACK)) {
                Text(stringResource(R.string.action_go_back))
            }
        }
    }
}
