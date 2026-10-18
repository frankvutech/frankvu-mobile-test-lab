package com.mobiletestplayground.app.compose

import androidx.annotation.StringRes
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.mobiletestplayground.app.MainActivity
import com.mobiletestplayground.core.ui.TestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule

typealias PlaygroundRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/**
 * Every Compose test starts the real app (real Hilt graph, real repositories) on a freshly cleared
 * install: the orchestrator runs each test in its own process with `clearPackageData`.
 */
@HiltAndroidTest
abstract class PlaygroundComposeTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose: PlaygroundRule = androidx.compose.ui.test.junit4.createAndroidComposeRule<MainActivity>()

    protected val robot by lazy { PlaygroundRobot(compose) }

    @Before
    fun injectHilt() = hilt.inject()
}

/** Reusable steps. Selectors are always test tags; expected text comes from string resources. */
@OptIn(ExperimentalTestApi::class)
class PlaygroundRobot(private val compose: PlaygroundRule) {

    fun string(@StringRes id: Int, vararg args: Any): String = compose.activity.getString(id, *args)

    fun waitFor(tag: String, timeoutMillis: Long = DEFAULT_TIMEOUT) {
        compose.waitUntilAtLeastOneExists(hasTestTag(tag), timeoutMillis)
    }

    fun waitGone(tag: String, timeoutMillis: Long = DEFAULT_TIMEOUT) {
        compose.waitUntilDoesNotExist(hasTestTag(tag), timeoutMillis)
    }

    fun tap(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }

    /** Taps a node that is not inside a scrollable container (app bar, bottom bar, dialog). */
    fun tapInPlace(tag: String) {
        compose.onNodeWithTag(tag).performClick()
    }

    fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextInput(text)
    }

    fun login(email: String, password: String, rememberMe: Boolean = false) {
        waitFor(TestTags.LOGIN_SCREEN)
        compose.onNodeWithTag(TestTags.LOGIN_EMAIL).performTextInput(email)
        compose.onNodeWithTag(TestTags.LOGIN_PASSWORD).performTextInput(password)
        if (rememberMe) compose.onNodeWithTag(TestTags.LOGIN_REMEMBER_ME).performClick()
        compose.onNodeWithTag(TestTags.LOGIN_BUTTON).performClick()
    }

    fun loginAsCustomer() {
        login(CUSTOMER_EMAIL, CUSTOMER_PASSWORD)
        waitFor(TestTags.HOME_SCREEN)
    }

    fun loginAsAdmin() {
        login(ADMIN_EMAIL, ADMIN_PASSWORD)
        waitFor(TestTags.HOME_SCREEN)
    }

    fun openScenario(id: String) {
        waitFor(TestTags.HOME_SCREEN)
        compose.onNodeWithTag(TestTags.HOME_SCENARIO_LIST).performScrollToNode(hasTestTag(TestTags.scenario(id)))
        compose.onNodeWithTag(TestTags.scenario(id)).performClick()
    }

    companion object {
        const val DEFAULT_TIMEOUT = 5_000L
        const val CUSTOMER_EMAIL = "test@example.com"
        const val CUSTOMER_PASSWORD = "Password123!"
        const val ADMIN_EMAIL = "admin@example.com"
        const val ADMIN_PASSWORD = "Admin123!"
    }
}
