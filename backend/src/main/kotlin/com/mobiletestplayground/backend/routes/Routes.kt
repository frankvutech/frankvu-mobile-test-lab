package com.mobiletestplayground.backend.routes

import com.mobiletestplayground.backend.AUTH_JWT
import com.mobiletestplayground.backend.Services
import com.mobiletestplayground.backend.caller
import com.mobiletestplayground.backend.domain.ApiException
import com.mobiletestplayground.backend.domain.ProductDto
import com.mobiletestplayground.backend.service.AddCartItemRequest
import com.mobiletestplayground.backend.service.LoginRequest
import com.mobiletestplayground.backend.service.PasswordResetRequest
import com.mobiletestplayground.backend.service.RegisterRequest
import com.mobiletestplayground.backend.service.UpdateOrderStatusRequest
import com.mobiletestplayground.backend.service.UpdateQuantityRequest
import com.mobiletestplayground.backend.service.WishlistRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

/*
 * Route handlers only translate HTTP to service calls and back. Validation, authorization rules
 * beyond "is authenticated / is admin", and persistence all live in the service layer.
 */

private const val API = "/api/v1"

@Serializable
data class StatusResponse(val status: String, val checks: Map<String, String> = emptyMap())

private fun ApplicationCall.pathParameter(name: String): String =
    parameters[name] ?: throw ApiException.BadRequest("MISSING_PARAMETER", "Path parameter $name is required.")

private fun ApplicationCall.orderId(): Int =
    pathParameter("id").toIntOrNull() ?: throw ApiException.BadRequest("INVALID_ID", "Order id must be an integer.")

fun Route.healthRoutes(services: Services) {
    // Liveness: the process is up.
    get("/health") { call.respond(StatusResponse("ok")) }
    // Readiness: the database answers.
    get("/ready") {
        if (services.database.isReachable()) {
            call.respond(StatusResponse("ready", mapOf("database" to "up")))
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, StatusResponse("not_ready", mapOf("database" to "down")))
        }
    }
    staticResources("/openapi", "openapi")
}

fun Route.authRoutes(services: Services) = route("$API/auth") {
    post("/register") { call.respond(HttpStatusCode.Created, services.auth.register(call.receive<RegisterRequest>())) }
    post("/login") { call.respond(services.auth.login(call.receive<LoginRequest>())) }
    post("/password-reset") {
        services.auth.resetPassword(call.receive<PasswordResetRequest>())
        call.respond(HttpStatusCode.NoContent)
    }
    authenticate(AUTH_JWT) {
        get("/me") { call.respond(services.auth.me(call.caller())) }
        post("/logout") {
            services.auth.logout(call.caller())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

fun Route.productRoutes(services: Services) = route(API) {
    get("/products") {
        val p = call.request.queryParameters
        call.respond(services.catalog.list(p["q"], p["category"], p["sort"], p["page"], p["size"]))
    }
    get("/products/{id}") { call.respond(services.catalog.detail(call.pathParameter("id"))) }
    get("/categories") { call.respond(services.catalog.categories()) }
}

fun Route.cartRoutes(services: Services) = authenticate(AUTH_JWT) {
    route("$API/cart") {
        get { call.respond(services.cart.get(call.caller().userId)) }
        delete { call.respond(services.cart.clear(call.caller().userId)) }
        post("/items") {
            call.respond(HttpStatusCode.Created, services.cart.add(call.caller().userId, call.receive<AddCartItemRequest>()))
        }
        patch("/items/{productId}") {
            val caller = call.caller()
            call.respond(services.cart.update(caller.userId, call.pathParameter("productId"), call.receive<UpdateQuantityRequest>()))
        }
        delete("/items/{productId}") { call.respond(services.cart.remove(call.caller().userId, call.pathParameter("productId"))) }
    }
}

fun Route.wishlistRoutes(services: Services) = authenticate(AUTH_JWT) {
    route("$API/wishlist") {
        get { call.respond(services.wishlist.get(call.caller().userId)) }
        post("/items") {
            call.respond(HttpStatusCode.Created, services.wishlist.add(call.caller().userId, call.receive<WishlistRequest>()))
        }
        delete("/items/{productId}") { call.respond(services.wishlist.remove(call.caller().userId, call.pathParameter("productId"))) }
    }
}

fun Route.orderRoutes(services: Services) = authenticate(AUTH_JWT) {
    route("$API/orders") {
        post { call.respond(HttpStatusCode.Created, services.order.checkout(call.caller().userId)) }
        get { call.respond(services.order.mine(call.caller().userId)) }
        get("/{id}") { call.respond(services.order.detail(call.caller(), call.orderId())) }
    }
}

/** Server-side role enforcement: every admin endpoint checks the token's role, whatever the client shows. */
fun Route.adminRoutes(services: Services) = authenticate(AUTH_JWT) {
    route("$API/admin") {
        get("/products") {
            call.caller().requireAdmin()
            val p = call.request.queryParameters
            call.respond(services.catalog.list(p["q"], p["category"], p["sort"], p["page"], p["size"]))
        }
        post("/products") {
            call.caller().requireAdmin()
            call.respond(HttpStatusCode.Created, services.catalog.create(call.receive<ProductDto>()))
        }
        put("/products/{id}") {
            call.caller().requireAdmin()
            call.respond(services.catalog.update(call.pathParameter("id"), call.receive<ProductDto>()))
        }
        delete("/products/{id}") {
            call.caller().requireAdmin()
            services.catalog.delete(call.pathParameter("id"))
            call.respond(HttpStatusCode.NoContent)
        }
        get("/orders") {
            call.caller().requireAdmin()
            call.respond(services.order.all())
        }
        patch("/orders/{id}") {
            call.caller().requireAdmin()
            call.respond(services.order.updateStatus(call.orderId(), call.receive<UpdateOrderStatusRequest>()))
        }
    }
}

/**
 * Test support, enabled by TEST_SUPPORT_ENABLED (default true for local development and CI).
 * Must be disabled in any shared environment.
 */
fun Route.testSupportRoutes(services: Services) = route("$API/test") {
    post("/reset") {
        services.database.reset()
        call.respond(HttpStatusCode.NoContent)
    }
    // A deterministic 500 for exercising server-error handling in clients and tests.
    get("/server-error") { error("Deterministic server error for testing") }
}
