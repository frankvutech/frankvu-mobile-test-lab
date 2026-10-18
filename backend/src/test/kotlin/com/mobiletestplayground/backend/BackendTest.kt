package com.mobiletestplayground.backend

import com.mobiletestplayground.backend.config.AppConfig
import com.mobiletestplayground.backend.domain.Rules
import com.mobiletestplayground.backend.persistence.ProductSort
import com.mobiletestplayground.backend.security.PasswordHasher
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnitTests {
    @Test
    fun `password hashes verify and never contain the password`() {
        val hasher = PasswordHasher(iterations = 1_000)
        val hash = hasher.hash("Password123!")
        assertTrue(hash.startsWith("pbkdf2_sha256$1000$"))
        assertFalse(hash.contains("Password123!"))
        assertTrue(hasher.verify("Password123!", hash))
        assertFalse(hasher.verify("password123!", hash))
        assertFalse(hasher.verify("Password123!", "garbage"))
        assertTrue(hash != hasher.hash("Password123!"), "salted: two hashes of one password differ")
    }

    @Test
    fun `password policy matches the mobile apps`() {
        assertEquals("Password must be at least 8 characters.", Rules.passwordProblem("Ab1!"))
        assertEquals("Password needs an uppercase letter.", Rules.passwordProblem("password1!"))
        assertEquals("Password needs a digit.", Rules.passwordProblem("Password!"))
        assertEquals("Password needs a special character.", Rules.passwordProblem("Password1"))
        assertNull(Rules.passwordProblem("Password123!"))
    }

    @Test
    fun `sort parameter parsing`() {
        assertEquals(ProductSort.ID, ProductSort.fromParameter(null))
        assertEquals(ProductSort.PRICE_DESC, ProductSort.fromParameter("price_desc"))
        assertNull(ProductSort.fromParameter("random"))
    }

    @Test
    fun `configuration from environment`() {
        val config = AppConfig.fromEnvironment(mapOf("PORT" to "9000", "TEST_SUPPORT_ENABLED" to "false", "DATABASE_URL" to "jdbc:x"))
        assertEquals(9000, config.port)
        assertFalse(config.testSupportEnabled)
        assertEquals("jdbc:x", config.jdbcUrl)
        assertEquals(AppConfig.DEV_ONLY_SECRET, config.jwtSecret)
    }
}

/** End-to-end through Ktor's test engine with a private in-memory H2 database per test. */
class ApiIntegrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun api(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module(AppConfig.inMemory("test_" + UUID.randomUUID().toString().replace("-", ""))) }
        block()
    }

    private suspend fun HttpResponse.obj(): JsonObject = json.parseToJsonElement(bodyAsText()).jsonObject

    private suspend fun HttpResponse.errorCode(): String = obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    private fun HttpRequestBuilder.jsonBody(body: String) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpClient.login(email: String, password: String): String {
        val response = post("/api/v1/auth/login") { jsonBody("""{"email":"$email","password":"$password"}""") }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return response.obj()["token"]!!.jsonPrimitive.content
    }

    @Test
    fun `health and readiness`() = api {
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        val ready = client.get("/ready")
        assertEquals(HttpStatusCode.OK, ready.status)
        assertEquals("up", ready.obj()["checks"]!!.jsonObject["database"]!!.jsonPrimitive.content)
        assertEquals(HttpStatusCode.OK, client.get("/openapi/openapi.yaml").status)
    }

    @Test
    fun `login outcomes`() = api {
        val wrong = client.post("/api/v1/auth/login") { jsonBody("""{"email":"test@example.com","password":"nope"}""") }
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        assertEquals("INVALID_PASSWORD", wrong.errorCode())
        val locked = client.post("/api/v1/auth/login") { jsonBody("""{"email":"locked@example.com","password":"Locked123!"}""") }
        assertEquals(HttpStatusCode.Forbidden, locked.status)
        val invalid = client.post("/api/v1/auth/login") { jsonBody("""{"email":"bad","password":""}""") }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        assertEquals(2, invalid.obj()["error"]!!.jsonObject["details"]!!.jsonArray.size)
        val malformed = client.post("/api/v1/auth/login") { jsonBody("{not json") }
        assertEquals("MALFORMED_REQUEST", malformed.errorCode())
    }

    @Test
    fun `tokens are required, verified and revocable`() = api {
        assertEquals("UNAUTHORIZED", client.get("/api/v1/auth/me").errorCode())
        assertEquals("INVALID_TOKEN", client.get("/api/v1/auth/me") { bearerAuth("not.a.token") }.errorCode())
        val token = client.login("test@example.com", "Password123!")
        val me = client.get("/api/v1/auth/me") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, me.status, me.bodyAsText())
        assertEquals("CUSTOMER", me.obj()["role"]!!.jsonPrimitive.content)
        assertEquals(HttpStatusCode.NoContent, client.post("/api/v1/auth/logout") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/auth/me") { bearerAuth(token) }.status)
    }

    @Test
    fun `admin endpoints are enforced on the server`() = api {
        val customer = client.login("test@example.com", "Password123!")
        val admin = client.login("admin@example.com", "Admin123!")
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/admin/orders") { bearerAuth(customer) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/admin/orders") { bearerAuth(admin) }.status)
    }

    @Test
    fun `product search, filter, sort and pagination`() = api {
        val page = client.get("/api/v1/products?category=Books&sort=price_desc&size=2&page=2").obj()
        assertEquals(5, page["totalItems"]!!.jsonPrimitive.int)
        assertEquals(3, page["totalPages"]!!.jsonPrimitive.int)
        val ids = page["items"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        assertEquals(listOf("product_009", "product_005"), ids)
        assertEquals(0, client.get("/api/v1/products?page=99").obj()["items"]!!.jsonArray.size)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/products?size=51").status)
        assertEquals("PRODUCT_NOT_FOUND", client.get("/api/v1/products/product_999").errorCode())
    }

    @Test
    fun `cart, checkout and order isolation`() = api {
        val token = client.login("test@example.com", "Password123!")
        client.post("/api/v1/cart/items") {
            bearerAuth(token)
            jsonBody("""{"productId":"product_001","quantity":2}""")
        }
        val updated = client.patch("/api/v1/cart/items/product_001") {
            bearerAuth(token)
            jsonBody("""{"quantity":3}""")
        }.obj()
        assertEquals(3 * 749, updated["totalCents"]!!.jsonPrimitive.int)
        val soldOut = client.post("/api/v1/cart/items") {
            bearerAuth(token)
            jsonBody("""{"productId":"product_020"}""")
        }
        assertEquals("INSUFFICIENT_STOCK", soldOut.errorCode())

        val order = client.post("/api/v1/orders") { bearerAuth(token) }
        assertEquals(HttpStatusCode.Created, order.status)
        assertEquals("ORDER-0001", order.obj()["orderNumber"]!!.jsonPrimitive.content)
        assertEquals(0, client.get("/api/v1/cart") { bearerAuth(token) }.obj()["totalQuantity"]!!.jsonPrimitive.int)
        assertEquals("CART_EMPTY", client.post("/api/v1/orders") { bearerAuth(token) }.errorCode())
        assertEquals(7, client.get("/api/v1/products/product_001").obj()["stock"]!!.jsonPrimitive.int)

        val other = client.login("qa@example.com", "QaPassword123!")
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/orders/1") { bearerAuth(other) }.status)
    }

    @Test
    fun `reset restores the seed and order numbering`() = api {
        val token = client.login("qa@example.com", "QaPassword123!")
        client.delete("/api/v1/wishlist/items/product_002") { bearerAuth(token) }
        client.post("/api/v1/cart/items") {
            bearerAuth(token)
            jsonBody("""{"productId":"product_003"}""")
        }
        client.post("/api/v1/orders") { bearerAuth(token) }
        assertEquals(HttpStatusCode.NoContent, client.post("/api/v1/test/reset").status)

        val fresh = client.login("qa@example.com", "QaPassword123!")
        assertEquals(1, client.get("/api/v1/wishlist") { bearerAuth(fresh) }.obj()["items"]!!.jsonArray.size)
        assertEquals(0, client.get("/api/v1/orders") { bearerAuth(fresh) }.bodyAsText().let { json.parseToJsonElement(it).jsonArray.size })
        client.post("/api/v1/cart/items") {
            bearerAuth(fresh)
            jsonBody("""{"productId":"product_003"}""")
        }
        assertEquals("ORDER-0001", client.post("/api/v1/orders") { bearerAuth(fresh) }.obj()["orderNumber"]!!.jsonPrimitive.content)
        assertEquals(HttpStatusCode.InternalServerError, client.get("/api/v1/test/server-error").status)
    }

    @Test
    fun `password reset with the fixed test code`() = api {
        val wrongCode = client.post("/api/v1/auth/password-reset") {
            jsonBody("""{"email":"test@example.com","code":"000000","newPassword":"NewPass1!"}""")
        }
        assertEquals("INVALID_RESET_CODE", wrongCode.errorCode())
        val unknown = client.post("/api/v1/auth/password-reset") {
            jsonBody("""{"email":"ghost@example.com","code":"123456","newPassword":"NewPass1!"}""")
        }
        assertEquals(HttpStatusCode.NotFound, unknown.status)
        val ok = client.post("/api/v1/auth/password-reset") {
            jsonBody("""{"email":"test@example.com","code":"123456","newPassword":"NewPass1!"}""")
        }
        assertEquals(HttpStatusCode.NoContent, ok.status)
        client.login("test@example.com", "NewPass1!")
    }

    @Test
    fun `test support can be disabled`() = testApplication {
        application { module(AppConfig.inMemory("disabled_" + UUID.randomUUID()).copy(testSupportEnabled = false)) }
        assertEquals(HttpStatusCode.NotFound, client.post("/api/v1/test/reset").status)
    }
}
