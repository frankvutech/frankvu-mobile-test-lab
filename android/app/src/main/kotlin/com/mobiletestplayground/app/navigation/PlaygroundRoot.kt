package com.mobiletestplayground.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mobiletestplayground.app.AppViewModel
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.domain.access.AccessDecision
import com.mobiletestplayground.core.domain.access.AccessPolicy
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.CenteredLoading

/**
 * Root of the Compose tree. Waits for persisted state to load, then hosts navigation and applies
 * the access policy to whatever destination is current.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlaygroundRoot(appViewModel: AppViewModel) {
    val ready by appViewModel.ready.collectAsStateWithLifecycle()
    // Exposes every testTag as a resource id, so UIAutomator's By.res("login_email") works.
    Box(
        Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true },
    ) {
        if (!ready) {
            CenteredLoading(TestTags.APP_LOADING)
        } else {
            PlaygroundNavigation(appViewModel)
        }
    }
}

@Composable
private fun PlaygroundNavigation(appViewModel: AppViewModel) {
    val navController = rememberNavController()
    val session by appViewModel.session.collectAsStateWithLifecycle()
    val testMode by appViewModel.testMode.collectAsStateWithLifecycle()
    val cartCount by appViewModel.cartCount.collectAsStateWithLifecycle()
    val pendingRoute by appViewModel.pendingRoute.collectAsStateWithLifecycle()
    val entry by navController.currentBackStackEntryAsState()

    // Chosen once: a remembered user (or disabled authentication) starts on Home.
    val startDestination = remember {
        if (session != null || !testMode.authenticationRequired) Routes.HOME else Routes.LOGIN
    }

    val previousSession = remember { mutableStateOf(session) }
    LaunchedEffect(entry, session, testMode.authenticationRequired) {
        val justLoggedOut = previousSession.value != null && session == null
        previousSession.value = session
        val current = entry ?: return@LaunchedEffect
        val pattern = current.destination.route ?: return@LaunchedEffect
        if (justLoggedOut && testMode.authenticationRequired) {
            // Logout (or a Test Mode reset) always lands on a plain login screen with an empty back stack,
            // even from a public screen such as Settings.
            navController.navigate(Routes.login()) { popUpTo(navController.graph.id) { inclusive = true } }
            return@LaunchedEffect
        }
        val decision = AccessPolicy.decide(Routes.accessLevel(pattern), session, testMode.authenticationRequired)
        if (decision == AccessDecision.RequiresLogin) {
            // Remember where the user was going, and continue there after signing in.
            navController.navigate(Routes.login(Routes.concrete(current))) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        navController.navigate(route) { launchSingleTop = true }
        appViewModel.consumePendingRoute()
    }

    val showBottomBar = entry?.destination?.route in Routes.topLevel
    Scaffold(
        bottomBar = {
            if (showBottomBar) BottomBar(navController, cartCount)
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            PlaygroundNavHost(
                navController = navController,
                startDestination = startDestination,
                access = AccessState(session, testMode.authenticationRequired),
                onLogout = appViewModel::logout,
            )
        }
    }
}

private data class BottomItem(val route: String, val label: Int, val icon: ImageVector, val tag: String)

private val bottomItems = listOf(
    BottomItem(Routes.HOME, R.string.nav_home, Icons.Filled.Home, TestTags.BOTTOM_NAV_HOME),
    BottomItem(Routes.CATALOG, R.string.nav_catalog, Icons.Filled.Storefront, TestTags.BOTTOM_NAV_CATALOG),
    BottomItem(Routes.CART, R.string.nav_cart, Icons.Filled.ShoppingCart, TestTags.BOTTOM_NAV_CART),
    BottomItem(Routes.PROFILE, R.string.nav_profile, Icons.Filled.Person, TestTags.BOTTOM_NAV_PROFILE),
)

@Composable
private fun BottomBar(navController: NavHostController, cartCount: Int) {
    val entry by navController.currentBackStackEntryAsState()
    NavigationBar {
        bottomItems.forEach { item ->
            val selected = entry?.destination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigateTopLevel(item.route) },
                icon = {
                    if (item.route == Routes.CART && cartCount > 0) {
                        BadgedBox(badge = { Badge { Text(cartCount.toString(), Modifier.testTag(TestTags.CART_BADGE)) } }) {
                            Icon(item.icon, contentDescription = null)
                        }
                    } else {
                        Icon(item.icon, contentDescription = null)
                    }
                },
                label = { Text(stringResource(item.label)) },
                modifier = Modifier.testTag(item.tag),
            )
        }
    }
}

/** Standard bottom-navigation behaviour: one copy of each tab, state saved and restored. */
fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        // Home is always the root once signed in (login pops the whole graph and opens Home).
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
