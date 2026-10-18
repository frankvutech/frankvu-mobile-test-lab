package com.mobiletestplayground.core.domain.network

import com.mobiletestplayground.core.domain.model.Product

/** The responses the Network Playground can produce. [id] is used in automation identifiers. */
enum class NetworkScenario(val id: String) {
    SUCCESS("success"),
    SLOW("slow"),
    TIMEOUT("timeout"),
    BAD_REQUEST("http_400"),
    UNAUTHORIZED("http_401"),
    FORBIDDEN("http_403"),
    NOT_FOUND("http_404"),
    SERVER_ERROR("http_500"),
    EMPTY("empty"),
    MALFORMED("malformed"),
}

sealed interface NetworkError {
    data object Timeout : NetworkError
    data object BadRequest : NetworkError
    data object Unauthorized : NetworkError
    data object Forbidden : NetworkError
    data object NotFound : NetworkError
    data class Server(val code: Int) : NetworkError
    data object MalformedResponse : NetworkError
    data object Connectivity : NetworkError
    data class UnexpectedStatus(val code: Int) : NetworkError
}

sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data object Empty : NetworkResult<Nothing>
    data class Failure(val error: NetworkError) : NetworkResult<Nothing>
}

/** Maps an HTTP status code to the domain error the UI understands. */
object HttpErrorMapper {
    const val BAD_REQUEST = 400
    const val UNAUTHORIZED = 401
    const val FORBIDDEN = 403
    const val NOT_FOUND = 404
    private val SERVER_ERRORS = 500..599

    fun map(code: Int): NetworkError = when (code) {
        BAD_REQUEST -> NetworkError.BadRequest
        UNAUTHORIZED -> NetworkError.Unauthorized
        FORBIDDEN -> NetworkError.Forbidden
        NOT_FOUND -> NetworkError.NotFound
        in SERVER_ERRORS -> NetworkError.Server(code)
        else -> NetworkError.UnexpectedStatus(code)
    }
}

interface NetworkPlaygroundRepository {
    suspend fun fetchProducts(scenario: NetworkScenario): NetworkResult<List<Product>>
}
