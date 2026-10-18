package com.mobiletestplayground.core.domain.catalog

import com.mobiletestplayground.core.domain.FailureReason
import com.mobiletestplayground.core.domain.Outcome
import com.mobiletestplayground.core.domain.model.CartLine
import com.mobiletestplayground.core.domain.model.Product
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

interface ProductRepository {
    suspend fun products(): Outcome<List<Product>>

    suspend fun product(id: String): Outcome<Product>
}

data class PlacedOrder(val orderNumber: String, val totalCents: Int)

/**
 * The signed-in user's cart. [lines] mirrors the last known state; every operation returns the
 * outcome and updates [lines] on success. In backend mode the cart lives on the server.
 */
interface CartRepository {
    val lines: StateFlow<List<CartLine>>

    suspend fun refresh(): Outcome<Unit>

    suspend fun add(product: Product): Outcome<Unit>

    suspend fun remove(productId: String): Outcome<Unit>

    suspend fun clear(): Outcome<Unit>

    /** Turns the cart into an order and empties it. */
    suspend fun checkout(): Outcome<PlacedOrder>
}

sealed interface CheckoutResult {
    data class Placed(val orderNumber: String, val totalCents: Int) : CheckoutResult
    data object EmptyCart : CheckoutResult
    data class Failed(val reason: FailureReason) : CheckoutResult
}

/**
 * Places an order from the cart. Order numbers are deterministic: ORDER-0001 is the first order
 * after a reset, both in offline mode and on the backend.
 */
class CheckoutUseCase @Inject constructor(
    private val cart: CartRepository,
) {
    suspend operator fun invoke(): CheckoutResult {
        if (cart.lines.value.isEmpty()) return CheckoutResult.EmptyCart
        return when (val outcome = cart.checkout()) {
            is Outcome.Success -> CheckoutResult.Placed(outcome.value.orderNumber, outcome.value.totalCents)
            is Outcome.Failure -> CheckoutResult.Failed(outcome.reason)
        }
    }
}

interface OrderNumberGenerator {
    fun next(): String

    fun reset()
}

/** Formats cents as a fixed, locale-independent price such as "12.49". */
object PriceFormatter {
    fun format(cents: Int): String = "%d.%02d".format(java.util.Locale.ROOT, cents / 100, cents % 100)
}
