package com.mobiletestplayground.backend.persistence

import com.mobiletestplayground.backend.config.AppConfig
import com.mobiletestplayground.backend.domain.OrderStatus
import com.mobiletestplayground.backend.domain.Role
import com.mobiletestplayground.backend.security.PasswordHasher
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object Users : Table("users") {
    val id = varchar("id", 64)
    val email = varchar("email", 254).uniqueIndex()
    val displayName = varchar("display_name", 100)
    val role = enumerationByName("role", 16, Role::class)
    val passwordHash = varchar("password_hash", 200)
    val locked = bool("locked").default(false)
    override val primaryKey = PrimaryKey(id)
}

object Products : Table("products") {
    val id = varchar("id", 64)
    val name = varchar("name", 100)
    val description = varchar("description", 500)
    val category = varchar("category", 50).index()
    val priceCents = integer("price_cents")
    val stock = integer("stock")
    override val primaryKey = PrimaryKey(id)
}

object CartItems : Table("cart_items") {
    val userId = varchar("user_id", 64).references(Users.id)
    val productId = varchar("product_id", 64).references(Products.id)
    val quantity = integer("quantity")
    override val primaryKey = PrimaryKey(userId, productId)
}

object WishlistItems : Table("wishlist_items") {
    val userId = varchar("user_id", 64).references(Users.id)
    val productId = varchar("product_id", 64).references(Products.id)
    override val primaryKey = PrimaryKey(userId, productId)
}

object Orders : Table("orders") {
    val id = integer("id").autoIncrement()
    val orderNumber = varchar("order_number", 40).uniqueIndex()
    val userId = varchar("user_id", 64).references(Users.id)
    val status = enumerationByName("status", 16, OrderStatus::class)
    val totalCents = integer("total_cents")
    override val primaryKey = PrimaryKey(id)
}

object OrderItems : Table("order_items") {
    val orderId = integer("order_id").references(Orders.id)
    val productId = varchar("product_id", 64)
    val name = varchar("name", 100)
    val priceCents = integer("price_cents")
    val quantity = integer("quantity")
    override val primaryKey = PrimaryKey(orderId, productId)
}

/** Tokens invalidated by logout, until they would have expired anyway. */
object RevokedTokens : Table("revoked_tokens") {
    val tokenId = varchar("token_id", 64)
    val expiresAt = long("expires_at")
    override val primaryKey = PrimaryKey(tokenId)
}

private val allTables = listOf(Users, Products, CartItems, WishlistItems, Orders, OrderItems, RevokedTokens)

/**
 * Connects, creates the schema if missing (idempotent, deterministic initialization) and seeds an
 * empty database. [reset] restores the seed at any time; API tests call it through
 * `POST /api/v1/test/reset`.
 */
class PlaygroundDatabase(config: AppConfig, private val hasher: PasswordHasher) {
    private val dataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.dbUser
            password = config.dbPassword
            maximumPoolSize = POOL_SIZE
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        },
    )
    val database: Database = Database.connect(dataSource)

    /** Seed password hashes are computed once per process; hashing is deliberately slow. */
    private val seedHashes: Map<String, String> by lazy {
        SeedData.accounts.associate { it.id to hasher.hash(it.password) }
    }

    fun initialize() {
        transaction(database) {
            // Spread of a seven-element list once at startup; the copy is irrelevant.
            @Suppress("SpreadOperator")
            SchemaUtils.create(*allTables.toTypedArray())
            if (Users.selectAll().count() == 0L) seed()
        }
    }

    fun reset() {
        transaction(database) {
            listOf(OrderItems, Orders, CartItems, WishlistItems, RevokedTokens, Products, Users).forEach { it.deleteAll() }
            seed()
            resetOrderSequence()
        }
    }

    fun isReachable(): Boolean = runCatching { transaction(database) { exec("SELECT 1") { true } } }.getOrDefault(false) == true

    suspend fun <T> query(block: Transaction.() -> T): T = newSuspendedTransaction(Dispatchers.IO, database) { block() }

    fun close() = dataSource.close()

    private fun Transaction.seed() {
        Users.batchInsert(SeedData.accounts) { account ->
            this[Users.id] = account.id
            this[Users.email] = account.email
            this[Users.displayName] = account.displayName
            this[Users.role] = account.role
            this[Users.passwordHash] = seedHashes.getValue(account.id)
            this[Users.locked] = account.locked
        }
        Products.batchInsert(SeedData.products) { product ->
            this[Products.id] = product.id
            this[Products.name] = product.name
            this[Products.description] = product.description
            this[Products.category] = product.category
            this[Products.priceCents] = product.priceCents
            this[Products.stock] = product.stock
        }
        // The QA user starts with one wishlist item so "non-empty wishlist" scenarios need no setup.
        WishlistItems.batchInsert(listOf("product_002")) { productId ->
            this[WishlistItems.userId] = "user_qa"
            this[WishlistItems.productId] = productId
        }
    }

    /** Restarts order ids at 1 so the first order after a reset is always id 1 / ORDER-0001. */
    private fun Transaction.resetOrderSequence() {
        val url = database.url.lowercase()
        when {
            url.startsWith("jdbc:postgresql") -> exec("ALTER SEQUENCE orders_id_seq RESTART WITH 1")
            url.startsWith("jdbc:h2") -> exec("ALTER TABLE orders ALTER COLUMN id RESTART WITH 1")
        }
    }

    private companion object {
        const val POOL_SIZE = 8
    }
}
