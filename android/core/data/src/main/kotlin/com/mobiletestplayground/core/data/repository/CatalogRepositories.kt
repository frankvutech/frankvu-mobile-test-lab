package com.mobiletestplayground.core.data.repository

import com.mobiletestplayground.core.data.remote.AddToCartJson
import com.mobiletestplayground.core.data.remote.BackendClientProvider
import com.mobiletestplayground.core.data.remote.BackendErrors
import com.mobiletestplayground.core.data.remote.CartJson
import com.mobiletestplayground.core.domain.FailureReason
import com.mobiletestplayground.core.domain.Outcome
import com.mobiletestplayground.core.domain.catalog.CartRepository
import com.mobiletestplayground.core.domain.catalog.OrderNumberGenerator
import com.mobiletestplayground.core.domain.catalog.PlacedOrder
import com.mobiletestplayground.core.domain.catalog.ProductRepository
import com.mobiletestplayground.core.domain.model.CartLine
import com.mobiletestplayground.core.domain.model.Product
import com.mobiletestplayground.core.domain.seed.SeedData
import com.mobiletestplayground.core.domain.testmode.ResettableStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

// --- Offline/test mode -------------------------------------------------------------------------

class SeedProductRepository : ProductRepository {
    override suspend fun products(): Outcome<List<Product>> = Outcome.Success(SeedData.products)

    override suspend fun product(id: String): Outcome<Product> =
        SeedData.products.firstOrNull { it.id == id }?.let { Outcome.Success(it) } ?: Outcome.Failure(FailureReason.NOT_FOUND)
}

/** The offline cart lives in memory: it empties on process restart, which tests can rely on. */
class InMemoryCartRepository(private val orderNumbers: OrderNumberGenerator) :
    CartRepository,
    ResettableStore {
    private val state = MutableStateFlow<List<CartLine>>(emptyList())
    override val lines: StateFlow<List<CartLine>> = state.asStateFlow()

    override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun add(product: Product): Outcome<Unit> {
        state.update { lines ->
            val existing = lines.firstOrNull { it.product.id == product.id }
            if (existing == null) {
                lines + CartLine(product, 1)
            } else {
                lines.map { if (it.product.id == product.id) it.copy(quantity = it.quantity + 1) else it }
            }
        }
        return Outcome.Success(Unit)
    }

    override suspend fun remove(productId: String): Outcome<Unit> {
        state.update { lines -> lines.filterNot { it.product.id == productId } }
        return Outcome.Success(Unit)
    }

    override suspend fun clear(): Outcome<Unit> {
        state.value = emptyList()
        return Outcome.Success(Unit)
    }

    override suspend fun checkout(): Outcome<PlacedOrder> {
        val lines = state.value
        if (lines.isEmpty()) return Outcome.Failure(FailureReason.CONFLICT)
        val total = lines.sumOf { it.totalCents }
        state.value = emptyList()
        return Outcome.Success(PlacedOrder(orderNumbers.next(), total))
    }

    override suspend fun resetToSeed() {
        state.value = emptyList()
    }
}

class SequentialOrderNumberGenerator :
    OrderNumberGenerator,
    ResettableStore {
    private val counter = AtomicInteger(0)

    override fun next(): String = "ORDER-" + counter.incrementAndGet().toString().padStart(DIGITS, '0')

    override fun reset() = counter.set(0)

    override suspend fun resetToSeed() = reset()

    private companion object {
        const val DIGITS = 4
    }
}

// --- Backend mode ------------------------------------------------------------------------------

class RemoteProductRepository(private val client: BackendClientProvider) : ProductRepository {
    override suspend fun products(): Outcome<List<Product>> = BackendErrors.call { client.api().products().items.map { it.toDomain() } }

    override suspend fun product(id: String): Outcome<Product> = BackendErrors.call { client.api().product(id).toDomain() }
}

/** The server-side cart (`/api/v1/cart`). [lines] mirrors the last response. */
class RemoteCartRepository(
    private val client: BackendClientProvider,
    private val token: () -> String?,
) : CartRepository,
    ResettableStore {
    private val state = MutableStateFlow<List<CartLine>>(emptyList())
    override val lines: StateFlow<List<CartLine>> = state.asStateFlow()

    private fun bearer(): String? = token()?.let { "Bearer $it" }

    private suspend fun withCart(block: suspend (String) -> CartJson): Outcome<Unit> {
        val auth = bearer() ?: return Outcome.Failure(FailureReason.UNAUTHORIZED)
        return when (val outcome = BackendErrors.call { block(auth) }) {
            is Outcome.Success -> {
                state.value = outcome.value.items.map { CartLine(it.product.toDomain(), it.quantity) }
                Outcome.Success(Unit)
            }
            is Outcome.Failure -> outcome
        }
    }

    override suspend fun refresh() = withCart { client.api().cart(it) }

    override suspend fun add(product: Product) = withCart { client.api().addToCart(it, AddToCartJson(product.id)) }

    override suspend fun remove(productId: String) = withCart { client.api().removeFromCart(it, productId) }

    override suspend fun clear() = withCart { client.api().clearCart(it) }

    override suspend fun checkout(): Outcome<PlacedOrder> {
        val auth = bearer() ?: return Outcome.Failure(FailureReason.UNAUTHORIZED)
        return when (val outcome = BackendErrors.call { client.api().placeOrder(auth) }) {
            is Outcome.Success -> {
                state.value = emptyList()
                Outcome.Success(PlacedOrder(outcome.value.orderNumber, outcome.value.totalCents))
            }
            is Outcome.Failure -> outcome
        }
    }

    override suspend fun resetToSeed() {
        state.value = emptyList()
    }
}

// --- Mode switching ----------------------------------------------------------------------------

class SwitchingProductRepository(
    private val local: ProductRepository,
    private val remote: ProductRepository,
    private val useBackend: () -> Boolean,
) : ProductRepository {
    private val active get() = if (useBackend()) remote else local

    override suspend fun products() = active.products()

    override suspend fun product(id: String) = active.product(id)
}

class SwitchingCartRepository(
    private val local: CartRepository,
    private val remote: CartRepository,
    useBackendFlow: StateFlow<Boolean>,
    scope: CoroutineScope,
) : CartRepository {
    private val useBackend = useBackendFlow
    private val active get() = if (useBackend.value) remote else local

    override val lines: StateFlow<List<CartLine>> = combine(useBackendFlow, local.lines, remote.lines) { backend, offline, online ->
        if (backend) online else offline
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun refresh() = active.refresh()

    override suspend fun add(product: Product) = active.add(product)

    override suspend fun remove(productId: String) = active.remove(productId)

    override suspend fun clear() = active.clear()

    override suspend fun checkout() = active.checkout()
}
