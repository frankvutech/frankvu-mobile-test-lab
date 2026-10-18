package com.mobiletestplayground.app.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.ui.TestTags
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AuthorizationTest : PlaygroundComposeTest() {

    @Test
    fun customerIsDeniedTheAdminArea() {
        robot.loginAsCustomer()
        robot.openScenario("auth")
        robot.tap(TestTags.AUTH_OPEN_ADMIN)
        robot.waitFor(TestTags.ACCESS_DENIED_MESSAGE)
        compose.onNodeWithTag(TestTags.ACCESS_DENIED_MESSAGE).assertTextEquals(robot.string(R.string.access_denied_message))
        compose.onNodeWithTag(TestTags.ADMIN_DASHBOARD).assertDoesNotExist()
        compose.onNodeWithTag(TestTags.PROFILE_ADMIN).assertDoesNotExist()
    }

    @Test
    fun adminCanOpenAdminScreens() {
        robot.loginAsAdmin()
        robot.tapInPlace(TestTags.BOTTOM_NAV_PROFILE)
        robot.tap(TestTags.PROFILE_ADMIN)
        robot.waitFor(TestTags.ADMIN_DASHBOARD)
        robot.tap(TestTags.ADMIN_OPEN_USERS)
        robot.waitFor(TestTags.ADMIN_USERS)
        compose.onNodeWithTag(TestTags.adminUser("user_locked")).assertTextEquals(robot.string(R.string.admin_user_locked))
        robot.tapInPlace(TestTags.TOP_BAR_BACK)
        robot.tap(TestTags.ADMIN_OPEN_PRODUCTS)
        robot.waitFor(TestTags.ADMIN_PRODUCTS)
        compose.onNodeWithTag(TestTags.adminProduct("product_001")).assertTextEquals("7.49")
    }

    @Test
    fun loggingOutRemovesAccessToProtectedScreens() {
        robot.loginAsAdmin()
        robot.openScenario("auth")
        robot.tap(TestTags.AUTH_LOGOUT)
        robot.waitFor(TestTags.LOGIN_SCREEN)
        compose.onNodeWithTag(TestTags.AUTH_SCREEN).assertDoesNotExist()
        // Pressing back cannot return to the protected screen: the back stack was cleared.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag(TestTags.AUTH_SCREEN).assertDoesNotExist()
    }

    @Test
    fun cartCheckoutPlacesADeterministicOrder() {
        robot.loginAsCustomer()
        robot.tapInPlace(TestTags.BOTTOM_NAV_CATALOG)
        robot.waitFor(TestTags.CATALOG_SCREEN)
        robot.tapInPlace(TestTags.productAddToCart("product_001"))
        robot.tapInPlace(TestTags.productAddToCart("product_001"))
        robot.tapInPlace(TestTags.productAddToCart("product_002"))
        compose.onNodeWithTag(TestTags.CART_BADGE, useUnmergedTree = true).assertTextEquals("3")

        robot.tapInPlace(TestTags.BOTTOM_NAV_CART)
        robot.waitFor(TestTags.CART_SCREEN)
        compose.onNodeWithTag(TestTags.cartItemQuantity("product_001")).assertTextEquals("× 2")
        compose.onNodeWithTag(TestTags.CART_TOTAL).assertTextEquals("24.97")
        robot.tap(TestTags.CART_CHECKOUT_BUTTON)
        robot.waitFor(TestTags.ORDER_CONFIRMATION)
        compose.onNodeWithTag(TestTags.ORDER_NUMBER).assertTextEquals("ORDER-0001")
        compose.onNodeWithTag(TestTags.CART_EMPTY).assertIsDisplayed()
    }
}
