package com.mobiletestplayground.core.domain.deeplink

/** Destinations reachable by deep link. Navigation applies [com.mobiletestplayground.core.domain.access.AccessPolicy]. */
sealed interface DeepLink {
    data object Home : DeepLink
    data class Scenario(val id: String) : DeepLink
    data class Product(val id: String) : DeepLink
    data object Cart : DeepLink
    data object Profile : DeepLink
    data object Admin : DeepLink
    data object TestMode : DeepLink
    data class Detail(val id: Int) : DeepLink
}

/**
 * Parses `mtp://playground/...` links. Kept free of android.net.Uri so it is unit tested on the JVM
 * and shared verbatim with the iOS implementation's rules.
 *
 * Supported paths:
 * `/home`, `/scenario/{id}`, `/product/{id}`, `/cart`, `/profile`, `/admin`, `/test-mode`, `/detail/{n}`.
 */
object DeepLinkParser {
    const val SCHEME = "mtp"
    const val HOST = "playground"

    private val idPattern = Regex("^[a-z0-9_]+$")

    fun parse(link: String): DeepLink? {
        val prefix = "$SCHEME://$HOST"
        if (!link.startsWith(prefix, ignoreCase = true)) return null
        val path = link.substring(prefix.length).substringBefore('?').substringBefore('#')
        val segments = path.split('/').filter { it.isNotEmpty() }
        return when (segments.size) {
            0 -> DeepLink.Home
            1 -> single(segments[0])
            2 -> withArgument(segments[0], segments[1])
            else -> null
        }
    }

    private fun single(segment: String): DeepLink? = when (segment) {
        "home" -> DeepLink.Home
        "cart" -> DeepLink.Cart
        "profile" -> DeepLink.Profile
        "admin" -> DeepLink.Admin
        "test-mode" -> DeepLink.TestMode
        else -> null
    }

    private fun withArgument(segment: String, argument: String): DeepLink? {
        if (!idPattern.matches(argument)) return null
        return when (segment) {
            "scenario" -> DeepLink.Scenario(argument)
            "product" -> DeepLink.Product(argument)
            "detail" -> argument.toIntOrNull()?.takeIf { it > 0 }?.let { DeepLink.Detail(it) }
            else -> null
        }
    }
}
