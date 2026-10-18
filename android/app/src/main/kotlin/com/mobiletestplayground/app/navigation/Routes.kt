package com.mobiletestplayground.app.navigation

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.domain.access.AccessLevel
import com.mobiletestplayground.core.domain.deeplink.DeepLink
import com.mobiletestplayground.core.domain.deeplink.DeepLinkParser

/** Route patterns. Arguments use `{name}` placeholders, filled by the helper functions. */
object Routes {
    const val LOGIN = "login?redirect={redirect}"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot_password"
    const val RESET_PASSWORD = "reset_password?email={email}"
    const val HOME = "home"
    const val CATALOG = "catalog"
    const val CART = "cart"
    const val PROFILE = "profile"
    const val PRODUCT = "product/{productId}"
    const val ADMIN = "admin"
    const val ADMIN_PRODUCTS = "admin/products"
    const val ADMIN_USERS = "admin/users"
    const val SETTINGS = "settings"
    const val TEST_MODE = "test_mode"

    // The Navigation scenario is a nested graph.
    const val NAV_GRAPH = "scenario/navigation"
    const val NAV_ROOT = "nav/root"
    const val NAV_DETAIL = "nav/detail/{level}"
    const val NAV_MODAL = "nav/modal"
    const val NAV_NESTED = "nav/nested"

    val topLevel = setOf(HOME, CATALOG, CART, PROFILE)

    private val public = setOf(LOGIN, REGISTER, FORGOT_PASSWORD, RESET_PASSWORD, SETTINGS, TEST_MODE)

    fun login(redirect: String? = null) = if (redirect == null) "login" else "login?redirect=" + Uri.encode(redirect)

    fun resetPassword(email: String) = "reset_password?email=" + Uri.encode(email)

    fun product(id: String) = "product/$id"

    fun navDetail(level: Int) = "nav/detail/$level"

    fun scenario(id: String) = "scenario/$id"

    fun accessLevel(pattern: String): AccessLevel = when {
        pattern in public -> AccessLevel.PUBLIC
        pattern.startsWith(ADMIN) -> AccessLevel.ADMIN
        else -> AccessLevel.AUTHENTICATED
    }

    /** Rebuilds the concrete route of a back stack entry, e.g. `product/{productId}` → `product/product_003`. */
    fun concrete(entry: NavBackStackEntry): String? {
        val pattern = entry.destination.route ?: return null
        val args = entry.arguments
        return Regex("\\{([^}]+)\\}").replace(pattern) { match ->
            args?.getString(match.groupValues[1]).orEmpty()
        }
    }
}

/** The 20 scenarios on the dashboard. [id] is used for `scenario_<id>` tags and deep links. */
enum class Scenario(
    val id: String,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val icon: ImageVector,
) {
    AUTH("auth", R.string.scenario_auth, R.string.scenario_auth_description, Icons.Filled.Lock),
    FORMS("forms", R.string.scenario_forms, R.string.scenario_forms_description, Icons.Filled.EditNote),
    CONTROLS("controls", R.string.scenario_controls, R.string.scenario_controls_description, Icons.Filled.ToggleOn),
    LISTS("lists", R.string.scenario_lists, R.string.scenario_lists_description, Icons.AutoMirrored.Filled.List),
    SCROLLING("scrolling", R.string.scenario_scrolling, R.string.scenario_scrolling_description, Icons.Filled.SwapVert),
    GESTURES("gestures", R.string.scenario_gestures, R.string.scenario_gestures_description, Icons.Filled.TouchApp),
    NAVIGATION("navigation", R.string.scenario_navigation, R.string.scenario_navigation_description, Icons.Filled.Map),
    DIALOGS("dialogs", R.string.scenario_dialogs, R.string.scenario_dialogs_description, Icons.Filled.ChatBubble),
    PERMISSIONS("permissions", R.string.scenario_permissions, R.string.scenario_permissions_description, Icons.Filled.Security),
    WEBVIEW("webview", R.string.scenario_webview, R.string.scenario_webview_description, Icons.Filled.Public),
    NETWORK("network", R.string.scenario_network, R.string.scenario_network_description, Icons.Filled.CloudQueue),
    ACCESSIBILITY(
        "accessibility",
        R.string.scenario_accessibility,
        R.string.scenario_accessibility_description,
        Icons.Filled.Accessibility,
    ),
    ERRORS("errors", R.string.scenario_errors, R.string.scenario_errors_description, Icons.Filled.ErrorOutline),
    DYNAMIC_CONTENT("dynamic_content", R.string.scenario_dynamic, R.string.scenario_dynamic_description, Icons.Filled.Autorenew),
    TEST_DATA("test_data", R.string.scenario_test_data, R.string.scenario_test_data_description, Icons.Filled.Dataset),
    DEEP_LINKS("deep_links", R.string.scenario_deep_links, R.string.scenario_deep_links_description, Icons.Filled.Link),
    DATE_TIME("date_time", R.string.scenario_date_time, R.string.scenario_date_time_description, Icons.Filled.AccessTime),
    FILE_PICKER("file_picker", R.string.scenario_file_picker, R.string.scenario_file_picker_description, Icons.Filled.AttachFile),
    NOTIFICATIONS(
        "notifications",
        R.string.scenario_notifications,
        R.string.scenario_notifications_description,
        Icons.Filled.Notifications,
    ),
    TEST_MODE("test_mode", R.string.scenario_test_mode, R.string.scenario_test_mode_description, Icons.Filled.Build),
    ;

    val route: String
        get() = when (this) {
            TEST_MODE -> Routes.TEST_MODE
            else -> Routes.scenario(id)
        }

    companion object {
        fun byId(id: String): Scenario? = entries.firstOrNull { it.id == id }
    }
}

/** Maps a deep link to a concrete route. Access control is applied after navigation, by the host. */
object DeepLinkRouter {
    fun routeFor(link: String): String? = when (val parsed = DeepLinkParser.parse(link)) {
        null -> null
        DeepLink.Home -> Routes.HOME
        DeepLink.Cart -> Routes.CART
        DeepLink.Profile -> Routes.PROFILE
        DeepLink.Admin -> Routes.ADMIN
        DeepLink.TestMode -> Routes.TEST_MODE
        is DeepLink.Product -> Routes.product(parsed.id)
        is DeepLink.Scenario -> Scenario.byId(parsed.id)?.route
        is DeepLink.Detail -> Routes.navDetail(parsed.id)
    }
}
