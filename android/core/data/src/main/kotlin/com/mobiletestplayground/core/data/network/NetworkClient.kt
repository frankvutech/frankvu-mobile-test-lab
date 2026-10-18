package com.mobiletestplayground.core.data.network

import com.mobiletestplayground.core.domain.model.Product
import com.mobiletestplayground.core.domain.network.HttpErrorMapper
import com.mobiletestplayground.core.domain.network.NetworkError
import com.mobiletestplayground.core.domain.network.NetworkPlaygroundRepository
import com.mobiletestplayground.core.domain.network.NetworkResult
import com.mobiletestplayground.core.domain.network.NetworkScenario
import com.mobiletestplayground.core.domain.seed.SeedData
import com.mobiletestplayground.core.domain.testmode.TestModeRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.net.SocketTimeoutException

@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    val priceCents: Int,
    val category: String,
)

interface PlaygroundApi {
    @GET("products")
    suspend fun products(@Query("scenario") scenario: String): List<ProductDto>
}

/** Delays used by the simulated transport. Injected so tests can run without waiting. */
data class NetworkDelays(
    val slowMillis: Long = 2_000,
    val timeoutMillis: Long = 1_000,
    val testModeSlowMillis: Long = 2_000,
)

/**
 * A local OkHttp transport: every request is answered here and never reaches a socket. The
 * `scenario` query parameter selects the response, so the full Retrofit + serialization stack runs
 * exactly as it would against a real server, including real parsing failures.
 */
class ScenarioInterceptor(
    private val testMode: TestModeRepository,
    private val delays: NetworkDelays,
    private val json: Json,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val settings = testMode.settings.value
        if (settings.simulateSlowNetwork) Thread.sleep(delays.testModeSlowMillis)
        if (settings.simulateNetworkError) throw IOException("Simulated connectivity failure (Test Mode)")

        val scenario = NetworkScenario.entries.firstOrNull { it.id == request.url.queryParameter("scenario") }
            ?: NetworkScenario.NOT_FOUND
        val (code, body) = when (scenario) {
            NetworkScenario.SUCCESS -> HTTP_OK to productsJson()
            NetworkScenario.SLOW -> {
                Thread.sleep(delays.slowMillis)
                HTTP_OK to productsJson()
            }
            NetworkScenario.TIMEOUT -> {
                Thread.sleep(delays.timeoutMillis)
                throw SocketTimeoutException("Simulated timeout")
            }
            NetworkScenario.BAD_REQUEST -> HttpErrorMapper.BAD_REQUEST to ERROR_BODY
            NetworkScenario.UNAUTHORIZED -> HttpErrorMapper.UNAUTHORIZED to ERROR_BODY
            NetworkScenario.FORBIDDEN -> HttpErrorMapper.FORBIDDEN to ERROR_BODY
            NetworkScenario.NOT_FOUND -> HttpErrorMapper.NOT_FOUND to ERROR_BODY
            NetworkScenario.SERVER_ERROR -> HTTP_SERVER_ERROR to ERROR_BODY
            NetworkScenario.EMPTY -> HTTP_OK to "[]"
            NetworkScenario.MALFORMED -> HTTP_OK to """[{"id": "product_001", "name": """
        }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Simulated $code")
            .body(body.toResponseBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun productsJson(): String = json.encodeToString(
        SeedData.products.take(PRODUCT_COUNT).map { ProductDto(it.id, it.name, it.priceCents, it.category) },
    )

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_SERVER_ERROR = 500
        const val PRODUCT_COUNT = 5
        const val ERROR_BODY = """{"error": "simulated"}"""
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

object NetworkClientFactory {
    private const val BASE_URL = "https://playground.invalid/api/"

    val json = Json { ignoreUnknownKeys = true }

    fun create(interceptor: Interceptor): PlaygroundApi {
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PlaygroundApi::class.java)
    }
}

/** Runs a call and converts every outcome, including exceptions, into a [NetworkResult]. */
object SafeCall {
    suspend fun <T> execute(isEmpty: (T) -> Boolean, block: suspend () -> T): NetworkResult<T> = try {
        val value = block()
        if (isEmpty(value)) NetworkResult.Empty else NetworkResult.Success(value)
    } catch (e: HttpException) {
        NetworkResult.Failure(HttpErrorMapper.map(e.code()))
    } catch (e: SocketTimeoutException) {
        NetworkResult.Failure(NetworkError.Timeout)
    } catch (e: SerializationException) {
        NetworkResult.Failure(NetworkError.MalformedResponse)
    } catch (e: IllegalArgumentException) {
        // kotlinx.serialization reports truncated JSON as IllegalArgumentException in some paths.
        NetworkResult.Failure(NetworkError.MalformedResponse)
    } catch (e: IOException) {
        NetworkResult.Failure(NetworkError.Connectivity)
    }
}

class RetrofitNetworkPlaygroundRepository(
    private val api: PlaygroundApi,
) : NetworkPlaygroundRepository {
    override suspend fun fetchProducts(scenario: NetworkScenario): NetworkResult<List<Product>> = SafeCall.execute(
        isEmpty = { it.isEmpty() },
    ) {
        api.products(scenario.id).map { Product(it.id, it.name, it.priceCents, it.category) }
    }
}
