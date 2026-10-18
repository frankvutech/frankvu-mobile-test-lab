package com.mobiletestplayground.core.domain.seed

import com.mobiletestplayground.core.domain.model.DynamicItem
import com.mobiletestplayground.core.domain.model.Product
import com.mobiletestplayground.core.domain.model.Role
import com.mobiletestplayground.core.domain.model.User

/**
 * The deterministic data set every run starts from. Nothing here is random, so a test written
 * against "Product 003" finds the same product, at the same price, on every launch and platform.
 *
 * The iOS app mirrors these values in `SeedData.swift`; keep both in step.
 */
object SeedData {

    data class SeedAccount(
        val user: User,
        val password: String,
        val locked: Boolean = false,
    )

    val accounts: List<SeedAccount> = listOf(
        SeedAccount(User("user_customer", "test@example.com", "Test Customer", Role.CUSTOMER), "Password123!"),
        SeedAccount(User("user_qa", "qa@example.com", "QA User", Role.CUSTOMER), "QaPassword123!"),
        SeedAccount(User("user_admin", "admin@example.com", "Admin User", Role.ADMIN), "Admin123!"),
        SeedAccount(User("user_locked", "locked@example.com", "Locked User", Role.CUSTOMER), "Locked123!", locked = true),
    )

    private val categories = listOf("Books", "Electronics", "Garden", "Toys")

    /** Product 001 … Product 020. Price is a fixed function of the index. */
    val products: List<Product> = (1..20).map { index ->
        val number = index.toString().padStart(3, '0')
        Product(
            id = "product_$number",
            name = "Product $number",
            priceCents = 499 + index * 250,
            category = categories[(index - 1) % categories.size],
        )
    }

    /** Item 001 … Item 030 for the static list scenario. */
    val staticListItems: List<String> = (1..30).map { "Item " + it.toString().padStart(3, '0') }

    val dynamicItems: List<DynamicItem> = listOf(
        DynamicItem(1, "Apple"),
        DynamicItem(2, "Banana"),
        DynamicItem(3, "Cherry"),
        DynamicItem(4, "Date"),
        DynamicItem(5, "Elderberry"),
    )

    /** The infinite list ends here so the end-of-list state is reachable. */
    const val PAGED_ITEM_COUNT = 100
    const val PAGE_SIZE = 20

    /** The password-reset code is fixed so the reset flow can be automated end to end. */
    const val PASSWORD_RESET_CODE = "123456"
}
