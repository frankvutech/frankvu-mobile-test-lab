package com.mobiletestplayground.app.compose

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.ui.TestTags
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AuthenticationTest : PlaygroundComposeTest() {

    private fun assertLoginError(email: String, password: String, message: Int) {
        robot.login(email, password)
        robot.waitFor(TestTags.LOGIN_ERROR)
        compose.onNodeWithTag(TestTags.LOGIN_ERROR).assertTextEquals(robot.string(message))
    }

    @Test
    fun emptyEmailShowsRequiredError() = assertLoginError("", "", R.string.login_error_empty_email)

    @Test
    fun invalidEmailShowsFormatError() = assertLoginError("not-an-email", "x", R.string.login_error_invalid_email)

    @Test
    fun incorrectPasswordShowsError() = assertLoginError("test@example.com", "wrong", R.string.login_error_incorrect_password)

    @Test
    fun lockedAccountIsRejected() = assertLoginError("locked@example.com", "Locked123!", R.string.login_error_locked)

    @Test
    fun unknownUserIsRejected() = assertLoginError("nobody@example.com", "Password123!", R.string.login_error_unknown_user)

    @Test
    fun loadingIndicatorAppearsWhileSigningIn() {
        robot.login("test@example.com", "Password123!")
        robot.waitFor(TestTags.LOGIN_LOADING, timeoutMillis = 1_000)
        robot.waitFor(TestTags.HOME_SCREEN)
    }

    @Test
    fun successfulLoginShowsDashboardAndLogoutReturnsToLogin() {
        robot.loginAsCustomer()
        compose.onNodeWithTag(TestTags.HOME_WELCOME).assertTextEquals(robot.string(R.string.home_welcome, "Test Customer"))

        robot.tapInPlace(TestTags.BOTTOM_NAV_PROFILE)
        robot.waitFor(TestTags.PROFILE_SCREEN)
        compose.onNodeWithTag(TestTags.PROFILE_ROLE).assertTextEquals("CUSTOMER")
        robot.tap(TestTags.PROFILE_LOGOUT)

        robot.waitFor(TestTags.LOGIN_SCREEN)
        // A plain login screen after logout: no "please sign in to continue" redirect message.
        compose.onNodeWithTag(TestTags.LOGIN_REDIRECT_MESSAGE).assertDoesNotExist()
    }

    @Test
    fun passwordVisibilityToggleChangesItsLabel() {
        robot.waitFor(TestTags.LOGIN_SCREEN)
        val toggle = compose.onNodeWithTag(TestTags.LOGIN_PASSWORD_TOGGLE)
        toggle.assert(hasContentDescription(robot.string(R.string.login_show_password)))
        toggle.performClick()
        toggle.assert(hasContentDescription(robot.string(R.string.login_hide_password)))
    }

    @Test
    fun registeredUserCanSignIn() {
        robot.waitFor(TestTags.LOGIN_SCREEN)
        robot.tap(TestTags.LOGIN_REGISTER)
        robot.type(TestTags.REGISTER_NAME, "Ada Lovelace")
        robot.type(TestTags.REGISTER_EMAIL, "ada@example.com")
        robot.type(TestTags.REGISTER_PASSWORD, "Analytical1!")
        robot.type(TestTags.REGISTER_CONFIRM_PASSWORD, "Analytical1!")
        robot.tap(TestTags.REGISTER_BUTTON)
        robot.waitFor(TestTags.REGISTER_SUCCESS)
        compose.onNodeWithTag(TestTags.TOP_BAR_BACK).performClick()

        robot.login("ada@example.com", "Analytical1!")
        robot.waitFor(TestTags.HOME_SCREEN)
        compose.onNodeWithTag(TestTags.HOME_WELCOME).assertTextContains("Ada Lovelace", substring = true)
    }

    @Test
    fun passwordResetFlowChangesThePassword() {
        robot.waitFor(TestTags.LOGIN_SCREEN)
        robot.tap(TestTags.LOGIN_FORGOT_PASSWORD)
        robot.type(TestTags.FORGOT_EMAIL, "test@example.com")
        robot.tap(TestTags.FORGOT_SUBMIT)
        robot.waitFor(TestTags.FORGOT_CONFIRMATION)
        robot.tap(TestTags.FORGOT_CONTINUE)

        robot.waitFor(TestTags.RESET_SCREEN)
        robot.type(TestTags.RESET_CODE, "123456")
        robot.type(TestTags.RESET_PASSWORD, "NewPassword1!")
        robot.type(TestTags.RESET_CONFIRM_PASSWORD, "NewPassword1!")
        robot.tap(TestTags.RESET_SUBMIT)
        robot.waitFor(TestTags.RESET_SUCCESS)
        robot.tap(TestTags.RESET_BACK_TO_LOGIN)

        robot.login("test@example.com", "NewPassword1!")
        robot.waitFor(TestTags.HOME_SCREEN)
        compose.onNodeWithTag(TestTags.HOME_SCREEN).assertIsDisplayed()
    }
}
