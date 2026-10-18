package com.mobiletestplayground.backend.persistence

import com.mobiletestplayground.backend.domain.Role

/**
 * The deterministic data set. Products and accounts match the mobile apps' seed data
 * (android/core/domain/.../SeedData.kt, ios/.../Models.swift), so a scenario reproduced in the API
 * tests looks the same in either app.
 *
 * These are LOCAL TEST CREDENTIALS ONLY. They are stored hashed (PBKDF2) like any other password.
 */
object SeedData {
    data class Account(
        val id: String,
        val email: String,
        val displayName: String,
        val role: Role,
        val password: String,
        val locked: Boolean = false,
    )

    data class SeedProduct(
        val id: String,
        val name: String,
        val description: String,
        val category: String,
        val priceCents: Int,
        val stock: Int,
    )

    val accounts = listOf(
        Account("user_customer", "test@example.com", "Test Customer", Role.CUSTOMER, "Password123!"),
        Account("user_qa", "qa@example.com", "QA User", Role.CUSTOMER, "QaPassword123!"),
        Account("user_admin", "admin@example.com", "Admin User", Role.ADMIN, "Admin123!"),
        Account("user_locked", "locked@example.com", "Locked User", Role.CUSTOMER, "Locked123!", locked = true),
    )

    private val categories = listOf("Books", "Electronics", "Garden", "Toys")

    /** Stock levels that make scenarios reachable: product_019 has 1 left, product_020 is sold out. */
    private fun stockFor(index: Int) = when (index) {
        LOW_STOCK_INDEX -> 1
        SOLD_OUT_INDEX -> 0
        else -> DEFAULT_STOCK
    }

    val products = (1..PRODUCT_COUNT).map { index ->
        val number = index.toString().padStart(3, '0')
        SeedProduct(
            id = "product_$number",
            name = "Product $number",
            description = "Deterministic sample product number $number.",
            category = categories[(index - 1) % categories.size],
            priceCents = BASE_PRICE + index * PRICE_STEP,
            stock = stockFor(index),
        )
    }

    const val PRODUCT_COUNT = 20
    private const val BASE_PRICE = 499
    private const val PRICE_STEP = 250
    private const val DEFAULT_STOCK = 10
    private const val LOW_STOCK_INDEX = 19
    private const val SOLD_OUT_INDEX = 20
}
