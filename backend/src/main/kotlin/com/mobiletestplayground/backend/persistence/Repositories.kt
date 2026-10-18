package com.mobiletestplayground.backend.persistence

import com.mobiletestplayground.backend.domain.OrderDto
import com.mobiletestplayground.backend.domain.OrderItemDto
import com.mobiletestplayground.backend.domain.OrderStatus
import com.mobiletestplayground.backend.domain.ProductDto
import com.mobiletestplayground.backend.domain.Role
import com.mobiletestplayground.backend.domain.UserRecord
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/*
 * Repositories translate between tables and domain objects. They contain no business rules and do
 * not open transactions: services call them inside `PlaygroundDatabase.query { }`, so one service
 * operation (for example checkout) is one atomic transaction.
 */

class UserRepository {
    fun findByEmail(email: String): UserRecord? =
        Users.selectAll().where { Users.email.lowerCase() eq email.lowercase() }.singleOrNull()?.toUser()

    fun findById(id: String): UserRecord? = Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUser()

    fun count(): Long = Users.selectAll().count()

    fun create(user: UserRecord) {
        Users.insert {
            it[id] = user.id
            it[email] = user.email
            it[displayName] = user.displayName
            it[role] = user.role
            it[passwordHash] = user.passwordHash
            it[locked] = user.locked
        }
    }

    fun updatePassword(id: String, passwordHash: String) {
        Users.update({ Users.id eq id }) { it[Users.passwordHash] = passwordHash }
    }

    private fun ResultRow.toUser() = UserRecord(
        id = this[Users.id],
        email = this[Users.email],
        displayName = this[Users.displayName],
        role = this[Users.role],
        passwordHash = this[Users.passwordHash],
        locked = this[Users.locked],
    )
}

enum class ProductSort(val parameter: String) {
    ID("id"),
    NAME_ASC("name_asc"),
    NAME_DESC("name_desc"),
    PRICE_ASC("price_asc"),
    PRICE_DESC("price_desc"),
    ;

    companion object {
        fun fromParameter(value: String?): ProductSort? = if (value == null) ID else entries.firstOrNull { it.parameter == value }
    }
}

data class ProductQuery(val search: String?, val category: String?, val sort: ProductSort, val page: Int, val size: Int)

class ProductRepository {
    fun search(query: ProductQuery): Pair<List<ProductDto>, Int> {
        val conditions = mutableListOf<Op<Boolean>>()
        query.search?.takeIf { it.isNotBlank() }?.let { term ->
            conditions += Products.name.lowerCase() like "%${term.lowercase().replace("%", "").replace("_", "\\_")}%"
        }
        query.category?.takeIf { it.isNotBlank() }?.let { category ->
            conditions += Products.category.lowerCase() eq category.lowercase()
        }
        val where = conditions.reduceOrNull { left, right -> left and right }
        val base = if (where == null) Products.selectAll() else Products.selectAll().where { where }
        val total = base.count().toInt()
        val ordered = when (query.sort) {
            ProductSort.ID -> base.orderBy(Products.id to SortOrder.ASC)
            ProductSort.NAME_ASC -> base.orderBy(Products.name to SortOrder.ASC, Products.id to SortOrder.ASC)
            ProductSort.NAME_DESC -> base.orderBy(Products.name to SortOrder.DESC, Products.id to SortOrder.ASC)
            ProductSort.PRICE_ASC -> base.orderBy(Products.priceCents to SortOrder.ASC, Products.id to SortOrder.ASC)
            ProductSort.PRICE_DESC -> base.orderBy(Products.priceCents to SortOrder.DESC, Products.id to SortOrder.ASC)
        }
        val items = ordered.limit(query.size).offset(((query.page - 1) * query.size).toLong()).map { it.toProduct() }
        return items to total
    }

    fun findById(id: String): ProductDto? = Products.selectAll().where { Products.id eq id }.singleOrNull()?.toProduct()

    fun categories(): List<String> = Products.selectAll().map { it[Products.category] }.distinct().sorted()

    fun create(product: ProductDto) {
        Products.insert {
            it[id] = product.id
            it[name] = product.name
            it[description] = product.description
            it[category] = product.category
            it[priceCents] = product.priceCents
            it[stock] = product.stock
        }
    }

    fun update(product: ProductDto): Boolean = Products.update({ Products.id eq product.id }) {
        it[name] = product.name
        it[description] = product.description
        it[category] = product.category
        it[priceCents] = product.priceCents
        it[stock] = product.stock
    } > 0

    fun delete(id: String): Boolean = Products.deleteWhere { Products.id eq id } > 0

    fun setStock(id: String, stock: Int) {
        Products.update({ Products.id eq id }) { it[Products.stock] = stock }
    }

    fun isReferenced(id: String): Boolean =
        CartItems.selectAll().where { CartItems.productId eq id }.count() > 0 ||
            WishlistItems.selectAll().where { WishlistItems.productId eq id }.count() > 0
}

fun ResultRow.toProduct() = ProductDto(
    id = this[Products.id],
    name = this[Products.name],
    description = this[Products.description],
    category = this[Products.category],
    priceCents = this[Products.priceCents],
    stock = this[Products.stock],
)

class CartRepository {
    /** Lines in a stable order (product id), each with its current product data. */
    fun lines(userId: String): List<Pair<ProductDto, Int>> =
        (CartItems innerJoin Products).selectAll().where { CartItems.userId eq userId }
            .orderBy(CartItems.productId to SortOrder.ASC)
            .map { it.toProduct() to it[CartItems.quantity] }

    fun quantity(userId: String, productId: String): Int? =
        CartItems.selectAll().where { (CartItems.userId eq userId) and (CartItems.productId eq productId) }
            .singleOrNull()?.get(CartItems.quantity)

    fun set(userId: String, productId: String, quantity: Int) {
        val updated = CartItems.update({ (CartItems.userId eq userId) and (CartItems.productId eq productId) }) {
            it[CartItems.quantity] = quantity
        }
        if (updated == 0) {
            CartItems.insert {
                it[CartItems.userId] = userId
                it[CartItems.productId] = productId
                it[CartItems.quantity] = quantity
            }
        }
    }

    fun remove(userId: String, productId: String): Boolean =
        CartItems.deleteWhere { (CartItems.userId eq userId) and (CartItems.productId eq productId) } > 0

    fun clear(userId: String) {
        CartItems.deleteWhere { CartItems.userId eq userId }
    }
}

class WishlistRepository {
    fun items(userId: String): List<ProductDto> =
        (WishlistItems innerJoin Products).selectAll().where { WishlistItems.userId eq userId }
            .orderBy(WishlistItems.productId to SortOrder.ASC)
            .map { it.toProduct() }

    fun contains(userId: String, productId: String): Boolean =
        WishlistItems.selectAll().where { (WishlistItems.userId eq userId) and (WishlistItems.productId eq productId) }.count() > 0

    fun add(userId: String, productId: String) {
        WishlistItems.insert {
            it[WishlistItems.userId] = userId
            it[WishlistItems.productId] = productId
        }
    }

    fun remove(userId: String, productId: String): Boolean =
        WishlistItems.deleteWhere { (WishlistItems.userId eq userId) and (WishlistItems.productId eq productId) } > 0
}

class OrderRepository {
    fun create(userId: String, items: List<OrderItemDto>): OrderDto {
        val total = items.sumOf { it.priceCents * it.quantity }
        val id = Orders.insert {
            it[orderNumber] = "PENDING-" + System.nanoTime()
            it[Orders.userId] = userId
            it[status] = OrderStatus.PLACED
            it[totalCents] = total
        }[Orders.id]
        // The order number is derived from the id, so it is deterministic after a reset.
        Orders.update({ Orders.id eq id }) { it[orderNumber] = orderNumber(id) }
        items.forEach { item ->
            OrderItems.insert {
                it[orderId] = id
                it[productId] = item.productId
                it[name] = item.name
                it[priceCents] = item.priceCents
                it[quantity] = item.quantity
            }
        }
        return OrderDto(id, orderNumber(id), userId, OrderStatus.PLACED, total, items)
    }

    fun forUser(userId: String): List<OrderDto> =
        Orders.selectAll().where { Orders.userId eq userId }.orderBy(Orders.id to SortOrder.ASC).map { it.toOrder() }

    fun all(): List<OrderDto> = Orders.selectAll().orderBy(Orders.id to SortOrder.ASC).map { it.toOrder() }

    fun findById(id: Int): OrderDto? = Orders.selectAll().where { Orders.id eq id }.singleOrNull()?.toOrder()

    fun updateStatus(id: Int, status: OrderStatus): Boolean = Orders.update({ Orders.id eq id }) { it[Orders.status] = status } > 0

    private fun ResultRow.toOrder(): OrderDto {
        val id = this[Orders.id]
        val items = OrderItems.selectAll().where { OrderItems.orderId eq id }.orderBy(OrderItems.productId to SortOrder.ASC).map {
            OrderItemDto(it[OrderItems.productId], it[OrderItems.name], it[OrderItems.priceCents], it[OrderItems.quantity])
        }
        return OrderDto(id, this[Orders.orderNumber], this[Orders.userId], this[Orders.status], this[Orders.totalCents], items)
    }

    private fun orderNumber(id: Int) = "ORDER-" + id.toString().padStart(ORDER_DIGITS, '0')

    private companion object {
        const val ORDER_DIGITS = 4
    }
}

class TokenRepository {
    fun revoke(tokenId: String, expiresAt: Long) {
        if (!isRevoked(tokenId)) {
            RevokedTokens.insert {
                it[RevokedTokens.tokenId] = tokenId
                it[RevokedTokens.expiresAt] = expiresAt
            }
        }
    }

    fun isRevoked(tokenId: String): Boolean = RevokedTokens.selectAll().where { RevokedTokens.tokenId eq tokenId }.count() > 0
}

/** Default role for self-registered accounts. */
val REGISTERED_ROLE = Role.CUSTOMER
