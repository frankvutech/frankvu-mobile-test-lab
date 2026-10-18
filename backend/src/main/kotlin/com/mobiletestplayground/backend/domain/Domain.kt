package com.mobiletestplayground.backend.domain

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

enum class Role { CUSTOMER, ADMIN }

enum class OrderStatus { PLACED, SHIPPED, CANCELLED }

data class UserRecord(
    val id: String,
    val email: String,
    val displayName: String,
    val role: Role,
    val passwordHash: String,
    val locked: Boolean,
)

@Serializable
data class UserDto(val id: String, val email: String, val displayName: String, val role: Role)

fun UserRecord.toDto() = UserDto(id, email, displayName, role)

@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val priceCents: Int,
    val stock: Int,
)

@Serializable
data class CartItemDto(val product: ProductDto, val quantity: Int, val lineTotalCents: Int)

@Serializable
data class CartDto(val items: List<CartItemDto>, val totalQuantity: Int, val totalCents: Int)

@Serializable
data class WishlistDto(val items: List<ProductDto>)

@Serializable
data class OrderItemDto(val productId: String, val name: String, val priceCents: Int, val quantity: Int)

@Serializable
data class OrderDto(
    val id: Int,
    val orderNumber: String,
    val userId: String,
    val status: OrderStatus,
    val totalCents: Int,
    val items: List<OrderItemDto>,
)

@Serializable
data class Page<T>(val items: List<T>, val page: Int, val size: Int, val totalItems: Int, val totalPages: Int)

@Serializable
data class FieldError(val field: String, val message: String)

/**
 * Every failure the API reports. Each maps to one HTTP status and a stable machine-readable `code`,
 * so tests assert on codes rather than on message text.
 */
sealed class ApiException(val status: HttpStatusCode, val code: String, override val message: String) : RuntimeException(message) {
    class Validation(val errors: List<FieldError>) :
        ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "The request contains invalid fields.")

    class BadRequest(code: String, message: String) : ApiException(HttpStatusCode.BadRequest, code, message)

    class Unauthorized(code: String, message: String) : ApiException(HttpStatusCode.Unauthorized, code, message)

    class Forbidden(code: String, message: String) : ApiException(HttpStatusCode.Forbidden, code, message)

    class NotFound(code: String, message: String) : ApiException(HttpStatusCode.NotFound, code, message)

    class Conflict(code: String, message: String) : ApiException(HttpStatusCode.Conflict, code, message)
}

/** Collects field errors and throws one [ApiException.Validation] listing all of them. */
class Validator {
    private val errors = mutableListOf<FieldError>()

    fun check(condition: Boolean, field: String, message: String) {
        if (!condition) errors += FieldError(field, message)
    }

    fun throwIfInvalid() {
        if (errors.isNotEmpty()) throw ApiException.Validation(errors.toList())
    }
}

object Rules {
    private val email = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")
    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 64
    const val QUANTITY_MIN = 1
    const val QUANTITY_MAX = 10
    const val PAGE_SIZE_MAX = 50
    const val PAGE_SIZE_DEFAULT = 10

    fun isEmail(value: String) = email.matches(value)

    /** The same policy as the mobile apps: length, uppercase, digit, special character. */
    fun passwordProblem(value: String): String? = when {
        value.length < PASSWORD_MIN -> "Password must be at least $PASSWORD_MIN characters."
        value.length > PASSWORD_MAX -> "Password must be at most $PASSWORD_MAX characters."
        value.none { it.isUpperCase() } -> "Password needs an uppercase letter."
        value.none { it.isDigit() } -> "Password needs a digit."
        value.all { it.isLetterOrDigit() } -> "Password needs a special character."
        else -> null
    }
}
