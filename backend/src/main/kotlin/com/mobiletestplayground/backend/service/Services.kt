package com.mobiletestplayground.backend.service

import com.mobiletestplayground.backend.domain.ApiException
import com.mobiletestplayground.backend.domain.CartDto
import com.mobiletestplayground.backend.domain.CartItemDto
import com.mobiletestplayground.backend.domain.OrderDto
import com.mobiletestplayground.backend.domain.OrderItemDto
import com.mobiletestplayground.backend.domain.OrderStatus
import com.mobiletestplayground.backend.domain.Page
import com.mobiletestplayground.backend.domain.ProductDto
import com.mobiletestplayground.backend.domain.Role
import com.mobiletestplayground.backend.domain.Rules
import com.mobiletestplayground.backend.domain.UserDto
import com.mobiletestplayground.backend.domain.UserRecord
import com.mobiletestplayground.backend.domain.Validator
import com.mobiletestplayground.backend.domain.WishlistDto
import com.mobiletestplayground.backend.domain.toDto
import com.mobiletestplayground.backend.persistence.CartRepository
import com.mobiletestplayground.backend.persistence.OrderRepository
import com.mobiletestplayground.backend.persistence.PlaygroundDatabase
import com.mobiletestplayground.backend.persistence.ProductQuery
import com.mobiletestplayground.backend.persistence.ProductRepository
import com.mobiletestplayground.backend.persistence.ProductSort
import com.mobiletestplayground.backend.persistence.REGISTERED_ROLE
import com.mobiletestplayground.backend.persistence.TokenRepository
import com.mobiletestplayground.backend.persistence.UserRepository
import com.mobiletestplayground.backend.persistence.WishlistRepository
import com.mobiletestplayground.backend.security.IssuedToken
import com.mobiletestplayground.backend.security.PasswordHasher
import com.mobiletestplayground.backend.security.TokenService
import kotlinx.serialization.Serializable

/** The authenticated caller, resolved from a valid, unrevoked bearer token. */
data class Caller(val userId: String, val role: Role, val tokenId: String, val expiresAt: Long) {
    fun requireAdmin() {
        if (role != Role.ADMIN) throw ApiException.Forbidden("FORBIDDEN", "This endpoint requires the ADMIN role.")
    }
}

@Serializable
data class LoginRequest(val email: String? = null, val password: String? = null)

@Serializable
data class RegisterRequest(val displayName: String? = null, val email: String? = null, val password: String? = null)

@Serializable
data class PasswordResetRequest(val email: String? = null, val code: String? = null, val newPassword: String? = null)

@Serializable
data class AuthResponse(val token: String, val tokenType: String = "Bearer", val expiresAt: Long, val user: UserDto)

class AuthService(
    private val db: PlaygroundDatabase,
    private val users: UserRepository,
    private val tokens: TokenRepository,
    private val hasher: PasswordHasher,
    private val tokenService: TokenService,
) {
    /**
     * Login reports UNKNOWN_USER and INVALID_PASSWORD separately (both 401) so tests can tell the
     * scenarios apart. A production API would return one generic error to avoid revealing which
     * emails exist; this is a documented, deliberate choice for a test playground.
     */
    suspend fun login(request: LoginRequest): AuthResponse {
        val email = request.email?.trim().orEmpty()
        val password = request.password.orEmpty()
        Validator().apply {
            check(email.isNotEmpty(), "email", "Email is required.")
            check(email.isEmpty() || Rules.isEmail(email), "email", "Email is not valid.")
            check(password.isNotEmpty(), "password", "Password is required.")
        }.throwIfInvalid()

        val user = db.query { users.findByEmail(email) }
        val failure = when {
            user == null -> ApiException.Unauthorized("UNKNOWN_USER", "No account exists for this email.")
            user.locked -> ApiException.Forbidden("ACCOUNT_LOCKED", "This account is locked.")
            !hasher.verify(password, user.passwordHash) -> ApiException.Unauthorized("INVALID_PASSWORD", "Incorrect password.")
            else -> null
        }
        if (failure != null) throw failure
        return issue(user!!)
    }

    suspend fun register(request: RegisterRequest): AuthResponse {
        val name = request.displayName?.trim().orEmpty()
        val email = request.email?.trim()?.lowercase().orEmpty()
        val password = request.password.orEmpty()
        Validator().apply {
            check(name.isNotEmpty(), "displayName", "Display name is required.")
            check(name.length <= NAME_MAX, "displayName", "Display name must be at most $NAME_MAX characters.")
            check(Rules.isEmail(email), "email", "Email is not valid.")
            Rules.passwordProblem(password)?.let { check(false, "password", it) }
        }.throwIfInvalid()

        val user = db.query {
            if (users.findByEmail(email) != null) throw ApiException.Conflict("EMAIL_TAKEN", "An account with this email already exists.")
            val record = UserRecord(
                id = "user_registered_${users.count() + 1}",
                email = email,
                displayName = name,
                role = REGISTERED_ROLE,
                passwordHash = hasher.hash(password),
                locked = false,
            )
            users.create(record)
            record
        }
        return issue(user)
    }

    /**
     * Resets a password with the fixed test code [RESET_CODE]. There is no email delivery: this is a
     * deterministic stand-in for a real reset flow, documented as test-only.
     */
    suspend fun resetPassword(request: PasswordResetRequest) {
        val email = request.email?.trim().orEmpty()
        val newPassword = request.newPassword.orEmpty()
        Validator().apply {
            check(Rules.isEmail(email), "email", "Email is not valid.")
            check(!request.code.isNullOrBlank(), "code", "Code is required.")
            Rules.passwordProblem(newPassword)?.let { check(false, "newPassword", it) }
        }.throwIfInvalid()
        if (request.code?.trim() != RESET_CODE) throw ApiException.BadRequest("INVALID_RESET_CODE", "The reset code is invalid.")
        db.query {
            val user = users.findByEmail(email) ?: throw ApiException.NotFound("UNKNOWN_USER", "No account exists for this email.")
            // Tokens issued before the reset stay valid until they expire (documented in docs/api.md).
            users.updatePassword(user.id, hasher.hash(newPassword))
        }
    }

    suspend fun me(caller: Caller): UserDto =
        db.query { users.findById(caller.userId) }?.toDto() ?: throw ApiException.Unauthorized("INVALID_TOKEN", "Unknown user.")

    suspend fun logout(caller: Caller) = db.query { tokens.revoke(caller.tokenId, caller.expiresAt) }

    suspend fun isRevoked(tokenId: String): Boolean = db.query { tokens.isRevoked(tokenId) }

    private fun issue(user: UserRecord): AuthResponse {
        val issued: IssuedToken = tokenService.issue(user.id, user.role)
        return AuthResponse(token = issued.token, expiresAt = issued.expiresAtEpochSeconds, user = user.toDto())
    }

    companion object {
        private const val NAME_MAX = 100

        /** The fixed, documented test reset code (matches the mobile apps' offline mode). */
        const val RESET_CODE = "123456"
    }
}

class ProductService(private val db: PlaygroundDatabase, private val products: ProductRepository) {
    suspend fun list(search: String?, category: String?, sort: String?, page: String?, size: String?): Page<ProductDto> {
        val pageNumber = page?.toIntOrNull()
        val pageSize = size?.toIntOrNull()
        val order = ProductSort.fromParameter(sort)
        Validator().apply {
            check(page == null || (pageNumber != null && pageNumber >= 1), "page", "Page must be an integer of at least 1.")
            check(
                size == null || (pageSize != null && pageSize in 1..Rules.PAGE_SIZE_MAX),
                "size",
                "Size must be an integer between 1 and ${Rules.PAGE_SIZE_MAX}.",
            )
            check(order != null, "sort", "Sort must be one of ${ProductSort.entries.joinToString { it.parameter }}.")
        }.throwIfInvalid()

        val query = ProductQuery(search, category, order!!, pageNumber ?: 1, pageSize ?: Rules.PAGE_SIZE_DEFAULT)
        val (items, total) = db.query { products.search(query) }
        val totalPages = if (total == 0) 0 else (total + query.size - 1) / query.size
        return Page(items, query.page, query.size, total, totalPages)
    }

    suspend fun detail(id: String): ProductDto = db.query { products.findById(id) } ?: throw productNotFound(id)

    suspend fun categories(): List<String> = db.query { products.categories() }

    // --- Admin ---

    suspend fun create(product: ProductDto): ProductDto {
        validate(product, requireId = true)
        return db.query {
            if (products.findById(product.id) !=
                null
            ) {
                throw ApiException.Conflict("PRODUCT_EXISTS", "Product ${product.id} already exists.")
            }
            products.create(product)
            product
        }
    }

    suspend fun update(id: String, product: ProductDto): ProductDto {
        val updated = product.copy(id = id)
        validate(updated, requireId = false)
        return db.query {
            if (!products.update(updated)) throw productNotFound(id)
            updated
        }
    }

    suspend fun delete(id: String) = db.query {
        if (products.findById(id) == null) throw productNotFound(id)
        if (products.isReferenced(id)) {
            throw ApiException.Conflict("PRODUCT_IN_USE", "Product $id is in a cart or wishlist and cannot be deleted.")
        }
        products.delete(id)
    }

    private fun validate(product: ProductDto, requireId: Boolean) = Validator().apply {
        if (requireId) check(Regex("^[a-z0-9_]{3,64}$").matches(product.id), "id", "Id must be 3–64 lowercase letters, digits or _.")
        check(product.name.isNotBlank(), "name", "Name is required.")
        check(product.category.isNotBlank(), "category", "Category is required.")
        check(product.priceCents > 0, "priceCents", "Price must be greater than 0.")
        check(product.stock >= 0, "stock", "Stock must not be negative.")
    }.throwIfInvalid()
}

fun productNotFound(id: String) = ApiException.NotFound("PRODUCT_NOT_FOUND", "Product $id does not exist.")

@Serializable
data class AddCartItemRequest(val productId: String? = null, val quantity: Int? = 1)

@Serializable
data class UpdateQuantityRequest(val quantity: Int? = null)

class CartService(private val db: PlaygroundDatabase, private val cart: CartRepository, private val products: ProductRepository) {
    suspend fun get(userId: String): CartDto = db.query { snapshot(userId) }

    /** Adds to the existing quantity. The resulting quantity must stay within 1–10 and within stock. */
    suspend fun add(userId: String, request: AddCartItemRequest): CartDto {
        val quantity = request.quantity ?: 1
        Validator().apply {
            check(!request.productId.isNullOrBlank(), "productId", "Product id is required.")
            check(quantity in Rules.QUANTITY_MIN..Rules.QUANTITY_MAX, "quantity", quantityMessage())
        }.throwIfInvalid()
        val productId = request.productId!!
        return db.query {
            val product = products.findById(productId) ?: throw productNotFound(productId)
            val newQuantity = (cart.quantity(userId, productId) ?: 0) + quantity
            if (newQuantity > Rules.QUANTITY_MAX) throw ApiException.Validation(listOf(fieldQuantity()))
            ensureStock(product, newQuantity)
            cart.set(userId, productId, newQuantity)
            snapshot(userId)
        }
    }

    suspend fun update(userId: String, productId: String, request: UpdateQuantityRequest): CartDto {
        val quantity = request.quantity
        Validator().apply {
            check(quantity != null && quantity in Rules.QUANTITY_MIN..Rules.QUANTITY_MAX, "quantity", quantityMessage())
        }.throwIfInvalid()
        return db.query {
            if (cart.quantity(userId, productId) == null) throw cartItemNotFound(productId)
            ensureStock(products.findById(productId) ?: throw productNotFound(productId), quantity!!)
            cart.set(userId, productId, quantity)
            snapshot(userId)
        }
    }

    suspend fun remove(userId: String, productId: String): CartDto = db.query {
        if (!cart.remove(userId, productId)) throw cartItemNotFound(productId)
        snapshot(userId)
    }

    suspend fun clear(userId: String): CartDto = db.query {
        cart.clear(userId)
        snapshot(userId)
    }

    private fun snapshot(userId: String): CartDto {
        val lines = cart.lines(userId).map { (product, quantity) -> CartItemDto(product, quantity, product.priceCents * quantity) }
        return CartDto(lines, lines.sumOf { it.quantity }, lines.sumOf { it.lineTotalCents })
    }

    private fun ensureStock(product: ProductDto, quantity: Int) {
        if (quantity > product.stock) {
            throw ApiException.Conflict("INSUFFICIENT_STOCK", "Only ${product.stock} of ${product.id} in stock.")
        }
    }

    private fun quantityMessage() = "Quantity must be between ${Rules.QUANTITY_MIN} and ${Rules.QUANTITY_MAX}."

    private fun fieldQuantity() = com.mobiletestplayground.backend.domain.FieldError("quantity", quantityMessage())

    private fun cartItemNotFound(productId: String) = ApiException.NotFound("CART_ITEM_NOT_FOUND", "Product $productId is not in the cart.")
}

@Serializable
data class WishlistRequest(val productId: String? = null)

class WishlistService(
    private val db: PlaygroundDatabase,
    private val wishlist: WishlistRepository,
    private val products: ProductRepository,
) {
    suspend fun get(userId: String) = db.query { WishlistDto(wishlist.items(userId)) }

    suspend fun add(userId: String, request: WishlistRequest): WishlistDto {
        Validator().apply { check(!request.productId.isNullOrBlank(), "productId", "Product id is required.") }.throwIfInvalid()
        val productId = request.productId!!
        return db.query {
            products.findById(productId) ?: throw productNotFound(productId)
            if (wishlist.contains(userId, productId)) {
                throw ApiException.Conflict("ALREADY_IN_WISHLIST", "Product $productId is already in the wishlist.")
            }
            wishlist.add(userId, productId)
            WishlistDto(wishlist.items(userId))
        }
    }

    suspend fun remove(userId: String, productId: String) = db.query {
        if (!wishlist.remove(userId, productId)) {
            throw ApiException.NotFound("WISHLIST_ITEM_NOT_FOUND", "Product $productId is not in the wishlist.")
        }
        WishlistDto(wishlist.items(userId))
    }
}

@Serializable
data class UpdateOrderStatusRequest(val status: String? = null)

class OrderService(
    private val db: PlaygroundDatabase,
    private val orders: OrderRepository,
    private val cart: CartRepository,
    private val products: ProductRepository,
) {
    /** Checkout: one transaction that re-checks stock, decrements it, creates the order and empties the cart. */
    suspend fun checkout(userId: String): OrderDto = db.query {
        val lines = cart.lines(userId)
        if (lines.isEmpty()) throw ApiException.Conflict("CART_EMPTY", "The cart is empty.")
        lines.forEach { (product, quantity) ->
            if (quantity > product.stock) {
                throw ApiException.Conflict("INSUFFICIENT_STOCK", "Only ${product.stock} of ${product.id} in stock.")
            }
        }
        lines.forEach { (product, quantity) -> products.setStock(product.id, product.stock - quantity) }
        val order = orders.create(
            userId,
            lines.map { (product, quantity) ->
                OrderItemDto(product.id, product.name, product.priceCents, quantity)
            },
        )
        cart.clear(userId)
        order
    }

    suspend fun mine(userId: String): List<OrderDto> = db.query { orders.forUser(userId) }

    /** Another customer's order is reported as not found (404), never as forbidden, so ids cannot be probed. */
    suspend fun detail(caller: Caller, id: Int): OrderDto {
        val order = db.query { orders.findById(id) }
        if (order == null || (caller.role != Role.ADMIN && order.userId != caller.userId)) throw orderNotFound(id)
        return order
    }

    suspend fun all(): List<OrderDto> = db.query { orders.all() }

    suspend fun updateStatus(id: Int, request: UpdateOrderStatusRequest): OrderDto {
        val status = OrderStatus.entries.firstOrNull { it.name == request.status }
        Validator().apply {
            check(status != null, "status", "Status must be one of ${OrderStatus.entries.joinToString()}.")
        }.throwIfInvalid()
        return db.query {
            if (!orders.updateStatus(id, status!!)) throw orderNotFound(id)
            orders.findById(id)!!
        }
    }

    private fun orderNotFound(id: Int) = ApiException.NotFound("ORDER_NOT_FOUND", "Order $id does not exist.")
}
