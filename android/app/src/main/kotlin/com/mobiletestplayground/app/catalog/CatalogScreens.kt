package com.mobiletestplayground.app.catalog

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import com.mobiletestplayground.core.domain.FailureReason
import com.mobiletestplayground.core.domain.Outcome
import com.mobiletestplayground.core.domain.auth.AuthRepository
import com.mobiletestplayground.core.domain.catalog.CartRepository
import com.mobiletestplayground.core.domain.catalog.CheckoutResult
import com.mobiletestplayground.core.domain.catalog.CheckoutUseCase
import com.mobiletestplayground.core.domain.catalog.PriceFormatter
import com.mobiletestplayground.core.domain.catalog.ProductRepository
import com.mobiletestplayground.core.domain.model.CartLine
import com.mobiletestplayground.core.domain.model.Product
import com.mobiletestplayground.core.domain.model.Role
import com.mobiletestplayground.core.domain.model.Session
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.ErrorText
import com.mobiletestplayground.core.ui.components.LoadingIndicator
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ScreenColumn
import com.mobiletestplayground.core.ui.components.ValueRow
import com.mobiletestplayground.core.ui.components.textTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** User-facing message for a failed data operation (backend or offline). */
@StringRes
fun FailureReason.message(): Int = when (this) {
    FailureReason.UNAVAILABLE -> R.string.data_error_unavailable
    FailureReason.UNAUTHORIZED -> R.string.data_error_unauthorized
    FailureReason.NOT_FOUND -> R.string.product_not_found
    FailureReason.CONFLICT -> R.string.data_error_conflict
    FailureReason.UNKNOWN -> R.string.data_error_unknown
}

/** Loading → content or error, for any screen that reads from a repository. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
    data class Error(val reason: FailureReason) : Loadable<Nothing>
}

fun <T> Outcome<T>.toLoadable(): Loadable<T> = when (this) {
    is Outcome.Success -> Loadable.Ready(value)
    is Outcome.Failure -> Loadable.Error(reason)
}

private fun Outcome<*>.failureReason(): FailureReason? = (this as? Outcome.Failure)?.reason

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val products: ProductRepository,
    private val cart: CartRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow<Loadable<List<Product>>>(Loadable.Loading)
    val state: StateFlow<Loadable<List<Product>>> = mutableState.asStateFlow()
    private val mutableActionError = MutableStateFlow<FailureReason?>(null)

    /** The last add-to-cart failure (for example a sold-out product on the backend). */
    val actionError: StateFlow<FailureReason?> = mutableActionError.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutableState.value = Loadable.Loading
        viewModelScope.launch { mutableState.value = products.products().toLoadable() }
    }

    fun addToCart(product: Product) {
        viewModelScope.launch { mutableActionError.value = cart.add(product).failureReason() }
    }
}

@Composable
private fun LoadState(state: Loadable<*>, loadingTag: String, errorTag: String, onRetry: () -> Unit) {
    when (state) {
        Loadable.Loading -> LoadingIndicator(loadingTag, Modifier.padding(16.dp))
        is Loadable.Error -> Column(Modifier.padding(16.dp)) {
            ErrorText(stringResource(state.reason.message()), errorTag)
            OutlinedButton(onClick = onRetry, modifier = Modifier.testTag(TestTags.DATA_RETRY)) {
                Text(stringResource(R.string.action_retry))
            }
        }
        is Loadable.Ready<*> -> Unit
    }
}

@Composable
fun CatalogScreen(onProduct: (String) -> Unit, viewModel: CatalogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    PlaygroundScaffold(stringResource(R.string.nav_catalog), TestTags.CATALOG_SCREEN, onBack = null) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LoadState(state, TestTags.CATALOG_LOADING, TestTags.CATALOG_ERROR, viewModel::load)
            actionError?.let {
                ErrorText(stringResource(it.message()), TestTags.CATALOG_ACTION_ERROR, Modifier.padding(horizontal = 16.dp))
            }
            val products = (state as? Loadable.Ready)?.value ?: return@Column
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(TestTags.CATALOG_LIST),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(products, key = { it.id }) { product ->
                    ProductCard(product, onOpen = { onProduct(product.id) }, onAdd = { viewModel.addToCart(product) })
                }
            }
        }
    }
}

@Composable
private fun ProductCard(product: Product, onOpen: () -> Unit, onAdd: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.product(product.id)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                Text(product.category + " · " + PriceFormatter.format(product.priceCents))
            }
            OutlinedButton(onClick = onAdd, modifier = Modifier.testTag(TestTags.productAddToCart(product.id))) {
                Text(stringResource(R.string.catalog_add))
            }
        }
    }
}

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val products: ProductRepository,
    private val cart: CartRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    private val productId: String = handle["productId"] ?: ""
    private val mutableState = MutableStateFlow<Loadable<Product>>(Loadable.Loading)
    val state: StateFlow<Loadable<Product>> = mutableState.asStateFlow()
    private val mutableActionError = MutableStateFlow<FailureReason?>(null)
    val actionError: StateFlow<FailureReason?> = mutableActionError.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutableState.value = Loadable.Loading
        viewModelScope.launch { mutableState.value = products.product(productId).toLoadable() }
    }

    fun addToCart(product: Product) {
        viewModelScope.launch { mutableActionError.value = cart.add(product).failureReason() }
    }
}

@Composable
fun ProductDetailScreen(onBack: () -> Unit, viewModel: ProductDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val product = (state as? Loadable.Ready)?.value
    PlaygroundScaffold(product?.name ?: stringResource(R.string.nav_catalog), TestTags.PRODUCT_DETAIL_SCREEN, onBack) { padding ->
        ScreenColumn(padding) {
            if ((state as? Loadable.Error)?.reason == FailureReason.NOT_FOUND) {
                Text(stringResource(R.string.product_not_found), modifier = Modifier.testTag(TestTags.PRODUCT_NOT_FOUND))
                return@ScreenColumn
            }
            LoadState(state, TestTags.PRODUCT_LOADING, TestTags.PRODUCT_ERROR, viewModel::load)
            if (product == null) return@ScreenColumn
            Text(product.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag(TestTags.PRODUCT_NAME))
            ValueRow(stringResource(R.string.product_price), PriceFormatter.format(product.priceCents), TestTags.PRODUCT_PRICE)
            ValueRow(stringResource(R.string.product_category), product.category, TestTags.PRODUCT_CATEGORY)
            Button(
                onClick = { viewModel.addToCart(product) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.PRODUCT_ADD_TO_CART),
            ) { Text(stringResource(R.string.catalog_add_to_cart)) }
            actionError?.let { ErrorText(stringResource(it.message()), TestTags.CATALOG_ACTION_ERROR) }
        }
    }
}

@HiltViewModel
class CartViewModel @Inject constructor(
    private val cart: CartRepository,
    private val checkout: CheckoutUseCase,
) : ViewModel() {
    val lines: StateFlow<List<CartLine>> = cart.lines
    private val mutableOrder = MutableStateFlow<CheckoutResult.Placed?>(null)
    val order: StateFlow<CheckoutResult.Placed?> = mutableOrder.asStateFlow()
    private val mutableError = MutableStateFlow<FailureReason?>(null)
    val error: StateFlow<FailureReason?> = mutableError.asStateFlow()

    init {
        // In backend mode the cart lives on the server: fetch it whenever the screen opens.
        viewModelScope.launch { mutableError.value = cart.refresh().failureReason() }
    }

    fun remove(productId: String) {
        viewModelScope.launch { mutableError.value = cart.remove(productId).failureReason() }
    }

    fun checkout() {
        viewModelScope.launch {
            when (val result = checkout.invoke()) {
                is CheckoutResult.Placed -> {
                    mutableOrder.value = result
                    mutableError.value = null
                }
                is CheckoutResult.Failed -> mutableError.value = result.reason
                CheckoutResult.EmptyCart -> Unit
            }
        }
    }
}

@Composable
fun CartScreen(viewModel: CartViewModel = hiltViewModel()) {
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val order by viewModel.order.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    PlaygroundScaffold(stringResource(R.string.nav_cart), TestTags.CART_SCREEN, onBack = null) { padding ->
        ScreenColumn(padding) {
            order?.let { OrderConfirmation(it) }
            error?.let { ErrorText(stringResource(it.message()), TestTags.CART_ERROR) }
            if (lines.isEmpty()) {
                Text(stringResource(R.string.cart_empty), modifier = Modifier.testTag(TestTags.CART_EMPTY))
                return@ScreenColumn
            }
            lines.forEach { line ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.cartItem(line.product.id)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(line.product.name, modifier = Modifier.weight(1f).testTag(TestTags.title(TestTags.cartItem(line.product.id))))
                    Text("× " + line.quantity, modifier = Modifier.testTag(TestTags.cartItemQuantity(line.product.id)))
                    IconButton(
                        onClick = { viewModel.remove(line.product.id) },
                        modifier = Modifier.testTag(TestTags.cartRemove(line.product.id)),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cart_remove, line.product.name))
                    }
                }
            }
            HorizontalDivider()
            ValueRow(stringResource(R.string.cart_total), PriceFormatter.format(lines.sumOf { it.totalCents }), TestTags.CART_TOTAL)
            Button(
                onClick = viewModel::checkout,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.CART_CHECKOUT_BUTTON),
            ) { Text(stringResource(R.string.cart_checkout)) }
        }
    }
}

@Composable
private fun OrderConfirmation(placed: CheckoutResult.Placed) {
    Card(
        Modifier
            .fillMaxWidth()
            .testTag(TestTags.ORDER_CONFIRMATION),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.cart_order_placed), style = MaterialTheme.typography.titleMedium)
            Text(placed.orderNumber, modifier = Modifier.testTag(TestTags.ORDER_NUMBER))
            Text(
                stringResource(R.string.cart_total) + ": " + PriceFormatter.format(placed.totalCents),
                modifier = Modifier.testTag(textTag(R.string.cart_total)),
            )
        }
    }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(authRepository: AuthRepository) : ViewModel() {
    val session: StateFlow<Session?> = authRepository.session
}

@Composable
fun ProfileScreen(
    onAdmin: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
    onSignIn: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    PlaygroundScaffold(stringResource(R.string.nav_profile), TestTags.PROFILE_SCREEN, onBack = null) { padding ->
        ScreenColumn(padding) {
            val user = session?.user
            if (user == null) {
                Text(stringResource(R.string.profile_guest), modifier = Modifier.testTag(TestTags.PROFILE_NAME))
                Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.login_button)) }
            } else {
                ValueRow(stringResource(R.string.profile_name), user.displayName, TestTags.PROFILE_NAME)
                ValueRow(stringResource(R.string.login_email), user.email, TestTags.PROFILE_EMAIL)
                ValueRow(stringResource(R.string.profile_role), user.role.name, TestTags.PROFILE_ROLE)
                if (user.role == Role.ADMIN) {
                    OutlinedButton(
                        onClick = onAdmin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.PROFILE_ADMIN),
                    ) { Text(stringResource(R.string.admin_title)) }
                }
            }
            OutlinedButton(
                onClick = onSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.PROFILE_SETTINGS),
            ) { Text(stringResource(R.string.settings_title)) }
            if (user != null) {
                Button(
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.PROFILE_LOGOUT),
                ) { Text(stringResource(R.string.profile_logout)) }
            }
        }
    }
}
