package com.mobiletestplayground.backend

import com.auth0.jwt.interfaces.Payload
import com.mobiletestplayground.backend.config.AppConfig
import com.mobiletestplayground.backend.domain.ApiException
import com.mobiletestplayground.backend.domain.FieldError
import com.mobiletestplayground.backend.domain.Role
import com.mobiletestplayground.backend.persistence.CartRepository
import com.mobiletestplayground.backend.persistence.OrderRepository
import com.mobiletestplayground.backend.persistence.PlaygroundDatabase
import com.mobiletestplayground.backend.persistence.ProductRepository
import com.mobiletestplayground.backend.persistence.TokenRepository
import com.mobiletestplayground.backend.persistence.UserRepository
import com.mobiletestplayground.backend.persistence.WishlistRepository
import com.mobiletestplayground.backend.routes.adminRoutes
import com.mobiletestplayground.backend.routes.authRoutes
import com.mobiletestplayground.backend.routes.cartRoutes
import com.mobiletestplayground.backend.routes.healthRoutes
import com.mobiletestplayground.backend.routes.orderRoutes
import com.mobiletestplayground.backend.routes.productRoutes
import com.mobiletestplayground.backend.routes.testSupportRoutes
import com.mobiletestplayground.backend.routes.wishlistRoutes
import com.mobiletestplayground.backend.security.PasswordHasher
import com.mobiletestplayground.backend.security.TokenService
import com.mobiletestplayground.backend.service.AuthService
import com.mobiletestplayground.backend.service.Caller
import com.mobiletestplayground.backend.service.CartService
import com.mobiletestplayground.backend.service.OrderService
import com.mobiletestplayground.backend.service.ProductService
import com.mobiletestplayground.backend.service.WishlistService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

@Serializable
data class ErrorBody(val code: String, val message: String, val details: List<FieldError> = emptyList())

@Serializable
data class ErrorResponse(val error: ErrorBody)

/** Everything the routes need, built once per application. */
class Services(val config: AppConfig) {
    val hasher = PasswordHasher()
    val database = PlaygroundDatabase(config, hasher)
    val tokens = TokenService(config)
    private val users = UserRepository()
    private val products = ProductRepository()
    private val carts = CartRepository()
    private val wishlists = WishlistRepository()
    private val orders = OrderRepository()
    val auth = AuthService(database, users, TokenRepository(), hasher, tokens)
    val catalog = ProductService(database, products)
    val cart = CartService(database, carts, products)
    val wishlist = WishlistService(database, wishlists, products)
    val order = OrderService(database, orders, carts, products)
}

fun main() {
    val config = AppConfig.fromEnvironment()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(config) }.start(wait = true)
}

const val AUTH_JWT = "jwt"

fun Application.module(config: AppConfig = AppConfig.fromEnvironment()) {
    val services = Services(config)
    services.database.initialize()
    monitor.subscribe(ApplicationStopped) { services.database.close() }

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            },
        )
    }
    install(CallLogging) { level = Level.INFO }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        listOf(HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete).forEach(::allowMethod)
    }
    installErrorHandling()
    install(Authentication) {
        jwt(AUTH_JWT) {
            realm = TokenService.REALM
            verifier(services.tokens.verifier)
            validate { credential ->
                val tokenId = credential.payload.id
                if (tokenId == null || services.auth.isRevoked(tokenId)) null else JWTPrincipal(credential.payload)
            }
            challenge { _, _ ->
                val header = call.request.headers[HttpHeaders.Authorization]
                val error = if (header.isNullOrBlank()) {
                    ApiException.Unauthorized("UNAUTHORIZED", "A bearer token is required.")
                } else {
                    ApiException.Unauthorized("INVALID_TOKEN", "The bearer token is invalid, expired or revoked.")
                }
                call.respondError(error)
            }
        }
    }

    routing {
        swaggerUI(path = "docs", swaggerFile = "openapi/openapi.yaml")
        healthRoutes(services)
        authRoutes(services)
        productRoutes(services)
        cartRoutes(services)
        wishlistRoutes(services)
        orderRoutes(services)
        adminRoutes(services)
        if (config.testSupportEnabled) testSupportRoutes(services)
    }
}

private fun Application.installErrorHandling() {
    install(StatusPages) {
        exception<ApiException> { call, cause -> call.respondError(cause) }
        exception<BadRequestException> { call, cause ->
            val serialization = generateSequence(cause as Throwable) { it.cause }.any { it is SerializationException }
            val message = if (serialization) "The request body is not valid JSON for this endpoint." else cause.message.orEmpty()
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorBody("MALFORMED_REQUEST", message)))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled error on ${call.request.httpMethod.value} ${call.request.path()}", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(ErrorBody("INTERNAL_ERROR", "An unexpected error occurred.")))
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorBody("ROUTE_NOT_FOUND", "No endpoint at ${call.request.path()}.")))
        }
        status(HttpStatusCode.MethodNotAllowed) { call, _ ->
            call.respond(HttpStatusCode.MethodNotAllowed, ErrorResponse(ErrorBody("METHOD_NOT_ALLOWED", "Method not allowed.")))
        }
    }
}

suspend fun ApplicationCall.respondError(error: ApiException) {
    val details = (error as? ApiException.Validation)?.errors.orEmpty()
    respond(error.status, ErrorResponse(ErrorBody(error.code, error.message, details)))
}

/** The caller of an authenticated route. Only valid inside `authenticate(AUTH_JWT)`. */
fun ApplicationCall.caller(): Caller {
    val payload: Payload = principal<JWTPrincipal>()?.payload
        ?: throw ApiException.Unauthorized("UNAUTHORIZED", "A bearer token is required.")
    val role = runCatching { Role.valueOf(payload.getClaim(TokenService.ROLE_CLAIM).asString()) }.getOrNull()
        ?: throw ApiException.Unauthorized("INVALID_TOKEN", "The token has no valid role.")
    return Caller(payload.subject, role, payload.id, payload.expiresAt.toInstant().epochSecond)
}
