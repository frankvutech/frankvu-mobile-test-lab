package com.mobiletestplayground.core.data.remote

import com.mobiletestplayground.core.domain.FailureReason
import com.mobiletestplayground.core.domain.Outcome
import com.mobiletestplayground.core.domain.model.Product
import com.mobiletestplayground.core.domain.model.Role
import com.mobiletestplayground.core.domain.model.User
import com.mobiletestplayground.core.domain.testmode.TestModeRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit

// --- Wire format of the backend API (backend/src/main/resources/openapi/openapi.yaml) ---

@Serializable
data class UserJson(val id: String, val email: String, val displayName: String, val role: String) {
    fun toDomain() = User(id, email, displayName, if (role == "ADMIN") Role.ADMIN else Role.CUSTOMER)
}

@Serializable
data class AuthJson(val token: String, val user: UserJson)

@Serializable
data class LoginJson(val email: String, val password: String)

@Serializable
data class RegisterJson(val displayName: String, val email: String, val password: String)

@Serializable
data class PasswordResetJson(val email: String, val code: String, val newPassword: String)

@Serializable
data class ProductJson(val id: String, val name: String, val category: String, val priceCents: Int, val stock: Int = 0) {
    fun toDomain() = Product(id, name, priceCents, category)
}

@Serializable
data class ProductPageJson(val items: List<ProductJson>, val totalItems: Int)

@Serializable
data class CartLineJson(val product: ProductJson, val quantity: Int)

@Serializable
data class CartJson(val items: List<CartLineJson>, val totalCents: Int)

@Serializable
data class AddToCartJson(val productId: String, val quantity: Int = 1)

@Serializable
data class OrderJson(val id: Int, val orderNumber: String, val totalCents: Int)

@Serializable
data class ErrorJson(val error: ErrorBodyJson)

@Serializable
data class ErrorBodyJson(val code: String, val message: String = "")

/** The backend's REST surface used by the app. Auth headers are passed explicitly per call. */
interface BackendApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginJson): AuthJson

    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterJson): AuthJson

    @POST("api/v1/auth/password-reset")
    suspend fun resetPassword(@Body body: PasswordResetJson): retrofit2.Response<Unit>

    @POST("api/v1/auth/logout")
    suspend fun logout(@Header("Authorization") bearer: String): retrofit2.Response<Unit>

    @GET("api/v1/products")
    suspend fun products(@Query("size") size: Int = MAX_PAGE_SIZE, @Query("page") page: Int = 1): ProductPageJson

    @GET("api/v1/products/{id}")
    suspend fun product(@Path("id") id: String): ProductJson

    @GET("api/v1/cart")
    suspend fun cart(@Header("Authorization") bearer: String): CartJson

    @POST("api/v1/cart/items")
    suspend fun addToCart(@Header("Authorization") bearer: String, @Body body: AddToCartJson): CartJson

    @DELETE("api/v1/cart/items/{productId}")
    suspend fun removeFromCart(@Header("Authorization") bearer: String, @Path("productId") productId: String): CartJson

    @DELETE("api/v1/cart")
    suspend fun clearCart(@Header("Authorization") bearer: String): CartJson

    @POST("api/v1/orders")
    suspend fun placeOrder(@Header("Authorization") bearer: String): OrderJson

    companion object {
        const val MAX_PAGE_SIZE = 50
    }
}

/** Build-time backend settings, provided by the app module from BuildConfig. */
data class BackendConfig(
    /** e.g. `http://10.0.2.2:8080/` — the Android emulator's address for the host machine. */
    val defaultBaseUrl: String,
    /** Whether a fresh install starts in backend mode (true) or the offline/test mode (false). */
    val useBackendByDefault: Boolean,
)

/**
 * Creates the Retrofit client for the current base URL (build default or the Test Mode override),
 * rebuilding it only when the URL changes. Test Mode's slow/error switches apply here too.
 */
class BackendClientProvider(
    private val config: BackendConfig,
    private val testMode: TestModeRepository,
    private val json: Json,
    /** Tests only: answers requests locally instead of over the network. */
    private val transport: Interceptor? = null,
) {
    private var cached: Pair<String, BackendApi>? = null

    fun baseUrl(): String = normalize(testMode.settings.value.backendUrlOverride?.takeIf { it.isNotBlank() } ?: config.defaultBaseUrl)

    @Synchronized
    fun api(): BackendApi {
        val url = baseUrl()
        cached?.let { (cachedUrl, api) -> if (cachedUrl == url) return api }
        val client = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(TestModeInterceptor(testMode))
            .apply { transport?.let(::addInterceptor) }
            .build()
        val api = Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BackendApi::class.java)
        cached = url to api
        return api
    }

    private fun normalize(url: String) = if (url.endsWith("/")) url else "$url/"

    private companion object {
        const val TIMEOUT_SECONDS = 10L
    }
}

/** Applies Test Mode's "slow network" and "network error" switches to backend calls. */
class TestModeInterceptor(private val testMode: TestModeRepository) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val settings = testMode.settings.value
        if (settings.simulateSlowNetwork) Thread.sleep(SLOW_MILLIS)
        if (settings.simulateNetworkError) throw IOException("Simulated connectivity failure (Test Mode)")
        return chain.proceed(chain.request())
    }

    private companion object {
        const val SLOW_MILLIS = 2_000L
    }
}

/** Maps HTTP and transport failures to domain reasons, and reads the backend's error `code`. */
object BackendErrors {
    private val json = Json { ignoreUnknownKeys = true }

    fun code(error: HttpException): String? = runCatching {
        json.decodeFromString(ErrorJson.serializer(), error.response()?.errorBody()?.string().orEmpty()).error.code
    }.getOrNull()

    fun reason(error: Throwable): FailureReason = when (error) {
        is HttpException -> when (error.code()) {
            HTTP_UNAUTHORIZED -> FailureReason.UNAUTHORIZED
            HTTP_NOT_FOUND -> FailureReason.NOT_FOUND
            HTTP_CONFLICT -> FailureReason.CONFLICT
            else -> FailureReason.UNKNOWN
        }
        is IOException -> FailureReason.UNAVAILABLE
        else -> FailureReason.UNKNOWN
    }

    suspend fun <T> call(block: suspend () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (e: HttpException) {
        Outcome.Failure(reason(e))
    } catch (e: IOException) {
        Outcome.Failure(FailureReason.UNAVAILABLE)
    } catch (e: kotlinx.serialization.SerializationException) {
        Outcome.Failure(FailureReason.UNKNOWN)
    }

    private const val HTTP_UNAUTHORIZED = 401
    private const val HTTP_NOT_FOUND = 404
    private const val HTTP_CONFLICT = 409
}
