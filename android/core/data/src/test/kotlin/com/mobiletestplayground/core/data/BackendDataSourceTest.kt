package com.mobiletestplayground.core.data

import com.mobiletestplayground.core.data.network.NetworkClientFactory
import com.mobiletestplayground.core.data.remote.BackendClientProvider
import com.mobiletestplayground.core.data.remote.BackendConfig
import com.mobiletestplayground.core.data.repository.LocalAccountDirectory
import com.mobiletestplayground.core.data.repository.LocalAuthRepository
import com.mobiletestplayground.core.data.repository.RemoteAuthDataSource
import com.mobiletestplayground.core.data.repository.RemoteCartRepository
import com.mobiletestplayground.core.data.repository.RemoteProductRepository
import com.mobiletestplayground.core.data.repository.StoredTestModeRepository
import com.mobiletestplayground.core.data.storage.InMemoryAppStorage
import com.mobiletestplayground.core.domain.FailureReason
import com.mobiletestplayground.core.domain.Outcome
import com.mobiletestplayground.core.domain.auth.CredentialCheck
import com.mobiletestplayground.core.domain.auth.LoginError
import com.mobiletestplayground.core.domain.auth.LoginResult
import com.mobiletestplayground.core.domain.auth.LoginUseCase
import com.mobiletestplayground.core.domain.catalog.PlacedOrder
import com.mobiletestplayground.core.domain.model.Role
import com.mobiletestplayground.core.domain.seed.SeedData
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The backend data sources against a scripted transport that returns the backend's real response
 * shapes (see backend/src/main/resources/openapi/openapi.yaml). The live backend itself is covered by
 * the backend's own tests and the api-tests suite.
 */
class BackendDataSourceTest {
    private val requests = mutableListOf<String>()
    private var responder: (method: String, path: String) -> Pair<Int, String> = { _, _ -> 500 to "{}" }

    private val transport = Interceptor { chain ->
        val request = chain.request()
        val path = request.url.encodedPath
        requests += "${request.method} $path ${request.header("Authorization") ?: "-"}"
        val (code, body) = responder(request.method, path)
        if (code == 0) throw IOException("connection refused")
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }

    private val storage = InMemoryAppStorage()
    private val testMode = StoredTestModeRepository(storage)
    private val client = BackendClientProvider(BackendConfig("http://backend.test/", true), testMode, NetworkClientFactory.json, transport)
    private val auth = LocalAuthRepository(storage, LocalAccountDirectory(), RemoteAuthDataSource(client)) { true }

    private val adminJson = """{"token":"tkn-1","tokenType":"Bearer","expiresAt":1,
        "user":{"id":"user_admin","email":"admin@example.com","displayName":"Admin User","role":"ADMIN"}}"""

    private fun error(code: String) = """{"error":{"code":"$code","message":"m","details":[]}}"""

    @Test
    fun `login maps backend error codes to domain errors`() = runTest {
        val login = LoginUseCase(auth)
        responder = { _, _ -> 401 to error("UNKNOWN_USER") }
        assertEquals(LoginError.UNKNOWN_USER, (login("x@example.com", "p", false) as LoginResult.Failure).error)
        responder = { _, _ -> 401 to error("INVALID_PASSWORD") }
        assertEquals(LoginError.INCORRECT_PASSWORD, (login("x@example.com", "p", false) as LoginResult.Failure).error)
        responder = { _, _ -> 403 to error("ACCOUNT_LOCKED") }
        assertEquals(LoginError.ACCOUNT_LOCKED, (login("x@example.com", "p", false) as LoginResult.Failure).error)
        responder = { _, _ -> 0 to "" }
        assertEquals(LoginError.SERVICE_UNAVAILABLE, (login("x@example.com", "p", false) as LoginResult.Failure).error)
    }

    @Test
    fun `successful login keeps the token, remember me persists it, logout revokes it`() = runTest {
        responder = { _, path -> if (path.endsWith("/logout")) 204 to "" else 200 to adminJson }
        val result = LoginUseCase(auth)("admin@example.com", "Admin123!", rememberMe = true)
        assertEquals(Role.ADMIN, (result as LoginResult.Success).user.role)
        assertEquals("tkn-1", auth.token)
        assertEquals("tkn-1", storage.readSessionToken())

        auth.endSession()
        assertEquals("POST /api/v1/auth/logout Bearer tkn-1", requests.last())
        assertNull(auth.token)
        assertNull(storage.readSessionToken())
    }

    @Test
    fun `products come from the backend`() = runTest {
        responder = { _, _ ->
            200 to
                """{"items":[{"id":"product_003","name":"Product 003","description":"d","category":"Garden","priceCents":1249,"stock":10}],
                "page":1,"size":50,"totalItems":1,"totalPages":1}"""
        }
        val products = (RemoteProductRepository(client).products() as Outcome.Success).value
        assertEquals(listOf(SeedData.products[2]), products)
        responder = { _, _ -> 404 to error("PRODUCT_NOT_FOUND") }
        assertEquals(Outcome.Failure(FailureReason.NOT_FOUND), RemoteProductRepository(client).product("product_999"))
    }

    @Test
    fun `cart calls send the bearer token and mirror the server cart`() = runTest {
        val cart = RemoteCartRepository(client) { "tkn-9" }
        val cartJson = """{"items":[{"product":{"id":"product_001","name":"Product 001","description":"d","category":"Books",
            "priceCents":749,"stock":10},"quantity":2,"lineTotalCents":1498}],"totalQuantity":2,"totalCents":1498}"""
        responder = { method, path ->
            when {
                method == "POST" && path == "/api/v1/orders" ->
                    201 to
                        """{"id":1,"orderNumber":"ORDER-0001","userId":"u","status":"PLACED","totalCents":1498,"items":[]}"""
                else -> 201 to cartJson
            }
        }
        assertIs<Outcome.Success<Unit>>(cart.add(SeedData.products[0]))
        assertEquals("POST /api/v1/cart/items Bearer tkn-9", requests.last())
        assertEquals(2, cart.lines.value.single().quantity)
        assertEquals(Outcome.Success(PlacedOrder("ORDER-0001", 1498)), cart.checkout())
        assertEquals(emptyList(), cart.lines.value)

        responder = { _, _ -> 409 to error("INSUFFICIENT_STOCK") }
        assertEquals(Outcome.Failure(FailureReason.CONFLICT), cart.add(SeedData.products[19]))
    }

    @Test
    fun `cart without a session is unauthorized and makes no request`() = runTest {
        assertEquals(Outcome.Failure(FailureReason.UNAUTHORIZED), RemoteCartRepository(client) { null }.refresh())
        assertEquals(emptyList(), requests)
    }

    @Test
    fun `test mode can point the client at another server`() = runTest {
        testMode.update { it.copy(backendUrlOverride = "http://192.168.1.20:8080") }
        assertEquals("http://192.168.1.20:8080/", client.baseUrl())
        val check = RemoteAuthDataSource(client).login("a@b.co", "x")
        assertIs<CredentialCheck>(check.first)
    }
}
