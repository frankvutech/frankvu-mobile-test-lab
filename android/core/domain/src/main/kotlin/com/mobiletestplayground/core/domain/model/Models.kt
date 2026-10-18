package com.mobiletestplayground.core.domain.model

/** Roles drive authorization. Every account has exactly one. */
enum class Role { CUSTOMER, ADMIN }

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val role: Role,
)

/**
 * An authenticated session.
 *
 * [rememberMe] decides whether the session survives a process restart: a remembered session is
 * persisted, a non-remembered one lives only in memory and disappears when the process dies.
 */
data class Session(
    val user: User,
    val rememberMe: Boolean,
)

data class Product(
    val id: String,
    val name: String,
    val priceCents: Int,
    val category: String,
)

data class CartLine(
    val product: Product,
    val quantity: Int,
) {
    val totalCents: Int get() = product.priceCents * quantity
}

/** An editable row in the Dynamic List scenario. [id] is stable and never reused. */
data class DynamicItem(
    val id: Int,
    val title: String,
)

/** A row in the Infinite Scroll scenario. */
data class PagedItem(
    val index: Int,
    val title: String,
)
