package com.mobiletestplayground.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.mobiletestplayground.app.admin.AccessDeniedScreen
import com.mobiletestplayground.app.admin.AdminDashboardScreen
import com.mobiletestplayground.app.admin.AdminProductsScreen
import com.mobiletestplayground.app.admin.AdminUsersScreen
import com.mobiletestplayground.app.auth.ForgotPasswordScreen
import com.mobiletestplayground.app.auth.LoginScreen
import com.mobiletestplayground.app.auth.RegisterScreen
import com.mobiletestplayground.app.auth.ResetPasswordScreen
import com.mobiletestplayground.app.catalog.CartScreen
import com.mobiletestplayground.app.catalog.CatalogScreen
import com.mobiletestplayground.app.catalog.ProductDetailScreen
import com.mobiletestplayground.app.catalog.ProfileScreen
import com.mobiletestplayground.app.home.HomeScreen
import com.mobiletestplayground.app.scenarios.accessibility.AccessibilityScreen
import com.mobiletestplayground.app.scenarios.auth.AuthScenarioScreen
import com.mobiletestplayground.app.scenarios.controls.ControlsScreen
import com.mobiletestplayground.app.scenarios.datetime.DateTimeScreen
import com.mobiletestplayground.app.scenarios.deeplinks.DeepLinksScreen
import com.mobiletestplayground.app.scenarios.dialogs.DialogsScreen
import com.mobiletestplayground.app.scenarios.dynamic.DynamicContentScreen
import com.mobiletestplayground.app.scenarios.errors.ErrorHandlingScreen
import com.mobiletestplayground.app.scenarios.filepicker.FilePickerScreen
import com.mobiletestplayground.app.scenarios.forms.FormsScreen
import com.mobiletestplayground.app.scenarios.gestures.GesturesScreen
import com.mobiletestplayground.app.scenarios.lists.ListsScreen
import com.mobiletestplayground.app.scenarios.navigation.NavigationDetailScreen
import com.mobiletestplayground.app.scenarios.navigation.NavigationModalScreen
import com.mobiletestplayground.app.scenarios.navigation.NavigationNestedScreen
import com.mobiletestplayground.app.scenarios.navigation.NavigationRootScreen
import com.mobiletestplayground.app.scenarios.network.NetworkScreen
import com.mobiletestplayground.app.scenarios.notifications.NotificationsScreen
import com.mobiletestplayground.app.scenarios.permissions.PermissionsScreen
import com.mobiletestplayground.app.scenarios.scrolling.ScrollingScreen
import com.mobiletestplayground.app.scenarios.testdata.TestDataScreen
import com.mobiletestplayground.app.scenarios.webview.WebViewScreen
import com.mobiletestplayground.app.settings.SettingsScreen
import com.mobiletestplayground.app.settings.TestModeScreen
import com.mobiletestplayground.core.domain.access.AccessDecision
import com.mobiletestplayground.core.domain.access.AccessPolicy
import com.mobiletestplayground.core.domain.model.Session

data class AccessState(val session: Session?, val authenticationRequired: Boolean)

@Composable
fun PlaygroundNavHost(
    navController: NavHostController,
    startDestination: String,
    access: AccessState,
    onLogout: () -> Unit,
) {
    // Remembered so the NavHost builder lambda stays equal across recompositions and the graph is
    // built once.
    val back: () -> Unit = remember(navController) { { navController.popBackStack() } }
    val openLogin: () -> Unit = remember(navController) {
        { navController.navigate(Routes.login()) { popUpTo(navController.graph.id) { inclusive = true } } }
    }
    val guard = remember(back) { Guard(back) }
    val currentLogout by rememberUpdatedState(onLogout)
    val logout: () -> Unit = remember { { currentLogout() } }

    CompositionLocalProvider(LocalAccessState provides access) {
        NavHost(navController = navController, startDestination = startDestination) {
            buildGraph(navController, guard, back, openLogin, logout)
        }
    }
}

@Suppress("LongMethod") // The full destination table, kept in one place on purpose.
private fun NavGraphBuilder.buildGraph(
    navController: NavHostController,
    guard: Guard,
    back: () -> Unit,
    openLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    // --- Authentication (public) ---
    composable(
        Routes.LOGIN,
        arguments = listOf(
            navArgument("redirect") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { entry ->
        LoginScreen(
            redirect = entry.arguments?.getString("redirect"),
            onLoggedIn = { redirect ->
                navController.navigate(Routes.HOME) { popUpTo(navController.graph.id) { inclusive = true } }
                if (redirect != null && redirect != Routes.HOME) navController.navigate(redirect)
            },
            onRegister = { navController.navigate(Routes.REGISTER) },
            onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
            onSettings = { navController.navigate(Routes.SETTINGS) },
            onContinueAsGuest = {
                navController.navigate(Routes.HOME) { popUpTo(navController.graph.id) { inclusive = true } }
            },
        )
    }
    composable(Routes.REGISTER) { RegisterScreen(onBack = back, onRegistered = back) }
    composable(Routes.FORGOT_PASSWORD) {
        ForgotPasswordScreen(onBack = back, onContinue = { email -> navController.navigate(Routes.resetPassword(email)) })
    }
    composable(
        Routes.RESET_PASSWORD,
        arguments = listOf(
            navArgument("email") {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) { entry ->
        ResetPasswordScreen(
            initialEmail = entry.arguments?.getString("email").orEmpty(),
            onBack = back,
            onBackToLogin = openLogin,
        )
    }

    // --- Top level (bottom navigation) ---
    guarded(guard, Routes.HOME) {
        HomeScreen(
            onScenario = { navController.navigate(it.route) },
            onCart = { navController.navigateTopLevel(Routes.CART) },
            onProfile = { navController.navigateTopLevel(Routes.PROFILE) },
            onSettings = { navController.navigate(Routes.SETTINGS) },
        )
    }
    guarded(guard, Routes.CATALOG) { CatalogScreen(onProduct = { navController.navigate(Routes.product(it)) }) }
    guarded(guard, Routes.CART) { CartScreen() }
    guarded(guard, Routes.PROFILE) {
        ProfileScreen(
            onAdmin = { navController.navigate(Routes.ADMIN) },
            onSettings = { navController.navigate(Routes.SETTINGS) },
            onLogout = onLogout,
            onSignIn = openLogin,
        )
    }
    // The ViewModel reads productId from the route through SavedStateHandle.
    guarded(guard, Routes.PRODUCT, listOf(navArgument("productId") { type = NavType.StringType })) {
        ProductDetailScreen(onBack = back)
    }

    // --- Admin (role protected) ---
    guarded(guard, Routes.ADMIN) {
        AdminDashboardScreen(
            onBack = back,
            onProducts = { navController.navigate(Routes.ADMIN_PRODUCTS) },
            onUsers = { navController.navigate(Routes.ADMIN_USERS) },
        )
    }
    guarded(guard, Routes.ADMIN_PRODUCTS) { AdminProductsScreen(onBack = back) }
    guarded(guard, Routes.ADMIN_USERS) { AdminUsersScreen(onBack = back) }

    // --- Settings (public, so Test Mode reset is always reachable in debug builds) ---
    composable(Routes.SETTINGS) { SettingsScreen(onBack = back, onTestMode = { navController.navigate(Routes.TEST_MODE) }) }
    composable(Routes.TEST_MODE) { TestModeScreen(onBack = back) }

    // --- Scenarios ---
    guarded(guard, Scenario.AUTH.route) {
        AuthScenarioScreen(onBack = back, onOpenAdmin = { navController.navigate(Routes.ADMIN) }, onLogout = onLogout)
    }
    guarded(guard, Scenario.FORMS.route) { FormsScreen(onBack = back) }
    guarded(guard, Scenario.CONTROLS.route) { ControlsScreen(onBack = back) }
    guarded(guard, Scenario.LISTS.route) { ListsScreen(onBack = back) }
    guarded(guard, Scenario.SCROLLING.route) { ScrollingScreen(onBack = back) }
    guarded(guard, Scenario.GESTURES.route) { GesturesScreen(onBack = back) }
    guarded(guard, Scenario.DIALOGS.route) { DialogsScreen(onBack = back) }
    guarded(guard, Scenario.PERMISSIONS.route) { PermissionsScreen(onBack = back) }
    guarded(guard, Scenario.WEBVIEW.route) { WebViewScreen(onBack = back) }
    guarded(guard, Scenario.NETWORK.route) { NetworkScreen(onBack = back) }
    guarded(guard, Scenario.ACCESSIBILITY.route) { AccessibilityScreen(onBack = back) }
    guarded(guard, Scenario.ERRORS.route) { ErrorHandlingScreen(onBack = back) }
    guarded(guard, Scenario.DYNAMIC_CONTENT.route) { DynamicContentScreen(onBack = back) }
    guarded(guard, Scenario.TEST_DATA.route) { TestDataScreen(onBack = back) }
    guarded(guard, Scenario.DEEP_LINKS.route) {
        DeepLinksScreen(onBack = back, onOpenRoute = { navController.navigate(it) { launchSingleTop = true } })
    }
    guarded(guard, Scenario.DATE_TIME.route) { DateTimeScreen(onBack = back) }
    guarded(guard, Scenario.FILE_PICKER.route) { FilePickerScreen(onBack = back) }
    guarded(guard, Scenario.NOTIFICATIONS.route) { NotificationsScreen(onBack = back) }

    // --- Navigation scenario: a nested graph with stack, modal and nested-host destinations ---
    navigation(route = Routes.NAV_GRAPH, startDestination = Routes.NAV_ROOT) {
        guarded(guard, Routes.NAV_ROOT) {
            NavigationRootScreen(
                onBack = back,
                onPushDetail = { navController.navigate(Routes.navDetail(1)) },
                onOpenModal = { navController.navigate(Routes.NAV_MODAL) },
                onOpenNested = { navController.navigate(Routes.NAV_NESTED) },
            )
        }
        guarded(guard, Routes.NAV_DETAIL, listOf(navArgument("level") { type = NavType.IntType })) { entry ->
            val level = entry.arguments?.getInt("level") ?: 1
            NavigationDetailScreen(
                level = level,
                onBack = back,
                onPushNext = { navController.navigate(Routes.navDetail(level + 1)) },
                onPopToRoot = {
                    if (!navController.popBackStack(Routes.NAV_ROOT, inclusive = false)) {
                        // Reached by deep link without the root underneath: open the scenario fresh.
                        navController.navigate(Routes.NAV_GRAPH) {
                            popUpTo(navController.graph.findStartDestination().id)
                        }
                    }
                },
            )
        }
        dialog(Routes.NAV_MODAL, dialogProperties = DialogProperties(usePlatformDefaultWidth = false)) {
            NavigationModalScreen(onClose = back)
        }
        guarded(guard, Routes.NAV_NESTED) { NavigationNestedScreen(onBack = back) }
    }
}

/**
 * Provided around the NavHost rather than captured by the graph builder, so a session change
 * recomposes guarded destinations without rebuilding the navigation graph.
 */
val LocalAccessState = compositionLocalOf { AccessState(session = null, authenticationRequired = true) }

/** Renders a destination according to [AccessPolicy]; redirects to login are performed by the host. */
class Guard(val onBack: () -> Unit)

private fun NavGraphBuilder.guarded(
    guard: Guard,
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(route, arguments = arguments) { entry ->
        val access = LocalAccessState.current
        when (AccessPolicy.decide(Routes.accessLevel(route), access.session, access.authenticationRequired)) {
            AccessDecision.Granted -> content(entry)
            AccessDecision.Denied -> AccessDeniedScreen(onBack = guard.onBack)
            // Blank while the host navigates to login; never flashes protected content.
            AccessDecision.RequiresLogin -> Box(Modifier.fillMaxSize())
        }
    }
}
