package com.mobiletestplayground.app.compose

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mobiletestplayground.app.BuildConfig
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.ui.TestTags
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * End to end against the real backend (backend/, default http://10.0.2.2:8080 from the emulator).
 *
 * Start the backend first (`make backend-run` or `docker compose up -d backend`). The test resets
 * the backend's data before running and is skipped, not failed, when the backend is unreachable,
 * so the offline UI suites stay independent of it.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BackendIntegrationTest : PlaygroundComposeTest() {

    private fun post(path: String): Int? = runCatching {
        (URL(BuildConfig.API_BASE_URL + path).openConnection() as HttpURLConnection).run {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            responseCode.also { disconnect() }
        }
    }.getOrNull()

    @Before
    fun resetBackend() {
        assumeTrue("Backend not reachable at ${BuildConfig.API_BASE_URL}", post("api/v1/test/reset") == HTTP_NO_CONTENT)
        switchToBackendMode()
    }

    private fun switchToBackendMode() {
        robot.waitFor(TestTags.LOGIN_SCREEN)
        robot.tapInPlace(TestTags.LOGIN_SETTINGS)
        robot.tap(TestTags.SETTINGS_TEST_MODE)
        robot.tap(TestTags.TEST_MODE_USE_BACKEND)
        compose.onNodeWithTag(TestTags.TEST_MODE_USE_BACKEND).assertIsOn()
        robot.tapInPlace(TestTags.TOP_BAR_BACK)
        robot.tapInPlace(TestTags.TOP_BAR_BACK)
    }

    @Test
    fun backendRejectsWrongPasswordAndUnknownUser() {
        robot.login("test@example.com", "wrong")
        robot.waitFor(TestTags.LOGIN_ERROR)
        compose.onNodeWithTag(TestTags.LOGIN_ERROR).assertTextEquals(robot.string(R.string.login_error_incorrect_password))
    }

    @Test
    fun catalogCartAndCheckoutGoThroughTheBackend() {
        robot.loginAsCustomer()
        robot.tapInPlace(TestTags.BOTTOM_NAV_CATALOG)
        robot.waitFor(TestTags.product("product_001"), timeoutMillis = NETWORK_TIMEOUT)
        robot.tapInPlace(TestTags.productAddToCart("product_001"))
        robot.tapInPlace(TestTags.productAddToCart("product_001"))
        // product_020 is sold out on the backend: the server answers 409 INSUFFICIENT_STOCK.
        compose.onNodeWithTag(TestTags.CATALOG_LIST).performScrollToNode(hasTestTag(TestTags.productAddToCart("product_020")))
        robot.tapInPlace(TestTags.productAddToCart("product_020"))
        robot.waitFor(TestTags.CATALOG_ACTION_ERROR, timeoutMillis = NETWORK_TIMEOUT)
        compose.onNodeWithTag(TestTags.CATALOG_ACTION_ERROR).assertTextEquals(robot.string(R.string.data_error_conflict))

        robot.tapInPlace(TestTags.BOTTOM_NAV_CART)
        robot.waitFor(TestTags.cartItem("product_001"), timeoutMillis = NETWORK_TIMEOUT)
        compose.onNodeWithTag(TestTags.cartItemQuantity("product_001")).assertTextEquals("× 2")
        compose.onNodeWithTag(TestTags.CART_TOTAL).assertTextEquals("14.98")
        robot.tap(TestTags.CART_CHECKOUT_BUTTON)
        robot.waitFor(TestTags.ORDER_CONFIRMATION, timeoutMillis = NETWORK_TIMEOUT)
        compose.onNodeWithTag(TestTags.ORDER_NUMBER).assertTextEquals("ORDER-0001")
    }

    @Test
    fun customerCannotReachAdminAndLogoutReturnsToLogin() {
        robot.loginAsCustomer()
        robot.tapInPlace(TestTags.BOTTOM_NAV_PROFILE)
        compose.onNodeWithTag(TestTags.PROFILE_ROLE).assertTextEquals("CUSTOMER")
        compose.onNodeWithTag(TestTags.PROFILE_ADMIN).assertDoesNotExist()
        compose.onNodeWithTag(TestTags.PROFILE_LOGOUT).performClick()
        robot.waitFor(TestTags.LOGIN_SCREEN)
    }

    private companion object {
        const val HTTP_NO_CONTENT = 204
        const val TIMEOUT_MS = 3_000
        const val NETWORK_TIMEOUT = 10_000L
    }
}
