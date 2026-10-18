package com.mobiletestplayground.app.uiautomator

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.ui.TestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Black-box tests through the accessibility layer, as an external driver sees the app. Compose
 * test tags are exposed as resource ids (`testTagsAsResourceId`), so `By.res("login_email")`
 * finds them. These tests also cover what Compose tests cannot: launching from intents and deep
 * links, the Home button, rotation and WebView content.
 *
 * Hilt requires the rule because the instrumented process runs HiltTestApplication.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class UiAutomatorTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    private val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /**
     * On failure, logs what UIAutomator saw and every thread of the app process (the tests run in it),
     * so a CI failure shows the screen and anything blocked without a screenshot or debugger.
     */
    @get:Rule(order = 1)
    val dumpOnFailure =
        object : TestWatcher() {
            override fun failed(e: Throwable?, description: Description) {
                val hierarchy = ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }.toString()
                hierarchy.chunked(LOG_CHUNK).forEach { Log.e(LOG_TAG, it) }
                Thread.getAllStackTraces().forEach { (thread, frames) ->
                    val trace = frames.take(MAX_FRAMES).joinToString("\n") { "    at $it" }
                    Log.e(LOG_TAG, "Thread ${thread.name} (${thread.state})\n$trace")
                }
            }
        }
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageName = context.packageName

    @Before
    fun setUp() {
        hilt.inject()
        device.setOrientationNatural()
    }

    @After
    fun tearDown() {
        device.setOrientationNatural()
    }

    private fun launch(intent: Intent = context.packageManager.getLaunchIntentForPackage(packageName)!!) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), TIMEOUT)
    }

    private fun launchDeepLink(link: String) = launch(Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(packageName))

    private fun find(selector: BySelector, timeout: Long = TIMEOUT): UiObject2 =
        assertNotNull(device.wait(Until.findObject(selector), timeout), "Not found: $selector")

    private fun id(tag: String): UiObject2 = find(By.res(tag))

    /**
     * Scrolls until the element is on screen. Uses the scrollable node when the accessibility tree
     * exposes one, otherwise a plain swipe (right after rotation the tree may still be rebuilding).
     */
    private fun scrollTo(tag: String): UiObject2 {
        repeat(MAX_SCROLLS) {
            device.findObject(By.res(tag))?.let { return it }
            val scrollable = device.findObject(By.scrollable(true))
            if (scrollable != null) {
                scrollable.scroll(Direction.DOWN, SCROLL_PERCENT)
            } else {
                device.swipe(
                    device.displayWidth / 2,
                    device.displayHeight * 3 / 4,
                    device.displayWidth / 2,
                    device.displayHeight / 4,
                    SWIPE_STEPS,
                )
            }
        }
        return id(tag)
    }

    private fun string(resId: Int, vararg args: Any) = context.getString(resId, *args)

    /**
     * Waits until the element shows [expected]. Reading `.text` right after navigation can hit a node
     * from the outgoing screen (StaleObjectException), so assertions wait for the expected state instead.
     */
    private fun assertText(tag: String, expected: String) {
        assertTrue(
            device.wait(Until.hasObject(By.res(tag).text(expected)), TIMEOUT),
            "Expected '$expected' in $tag but found '${device.findObject(By.res(tag))?.text}'",
        )
    }

    private fun login(email: String, password: String) {
        id(TestTags.LOGIN_EMAIL).text = email
        id(TestTags.LOGIN_PASSWORD).text = password
        id(TestTags.LOGIN_BUTTON).click()
    }

    @Test
    fun loginAndLogoutThroughTheAccessibilityLayer() {
        launch()
        login("test@example.com", "Password123!")
        id(TestTags.HOME_SCREEN)
        id(TestTags.BOTTOM_NAV_PROFILE).click()
        assertText(TestTags.PROFILE_EMAIL, "test@example.com")
        id(TestTags.PROFILE_LOGOUT).click()
        id(TestTags.LOGIN_SCREEN)
    }

    @Test
    fun loginErrorIsReadableByExternalDrivers() {
        launch()
        login("test@example.com", "wrong")
        assertText(TestTags.LOGIN_ERROR, string(R.string.login_error_incorrect_password))
    }

    @Test
    fun deepLinkWhileSignedOutRedirectsToLoginThenContinues() {
        launchDeepLink("mtp://playground/product/product_003")
        id(TestTags.LOGIN_REDIRECT_MESSAGE)
        login("test@example.com", "Password123!")
        assertText(TestTags.PRODUCT_NAME, "Product 003")
        assertText(TestTags.PRODUCT_PRICE, "12.49")
    }

    @Test
    fun adminDeepLinkAsCustomerShowsAccessDenied() {
        launch()
        login("test@example.com", "Password123!")
        id(TestTags.HOME_SCREEN)
        launchDeepLink("mtp://playground/admin")
        assertText(TestTags.ACCESS_DENIED_MESSAGE, string(R.string.access_denied_message))
    }

    @Test
    fun deepLinkIntoTheNavigationStack() {
        launch()
        login("admin@example.com", "Admin123!")
        id(TestTags.HOME_SCREEN)
        launchDeepLink("mtp://playground/detail/3")
        assertText(TestTags.NAV_DETAIL_LEVEL, string(R.string.navigation_level, 3))
    }

    @Test
    fun stateSurvivesBackgroundAndForeground() {
        launch()
        login("test@example.com", "Password123!")
        id(TestTags.HOME_SCREEN)
        scrollTo(TestTags.scenario("controls")).click()
        id(TestTags.CONTROLS_PRIMARY_BUTTON).click()
        id(TestTags.CONTROLS_PRIMARY_BUTTON).click()

        device.pressHome()
        device.wait(Until.gone(By.pkg(packageName).depth(0)), TIMEOUT)
        // Relaunching from the launcher resumes the existing task rather than starting fresh.
        context.startActivity(context.packageManager.getLaunchIntentForPackage(packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        assertText(TestTags.CONTROLS_TAP_COUNT, "2")
    }

    @Test
    fun stateSurvivesRotation() {
        launch()
        login("test@example.com", "Password123!")
        id(TestTags.HOME_SCREEN)
        scrollTo(TestTags.scenario("navigation")).click()
        id(TestTags.NAV_COUNTER_INCREMENT).click()
        id(TestTags.NAV_COUNTER_INCREMENT).click()

        device.setOrientationLeft()
        id(TestTags.NAVIGATION_SCREEN)
        // In landscape the counter is below the fold: scroll it into view before reading it.
        scrollTo(TestTags.NAV_COUNTER_VALUE)
        assertText(TestTags.NAV_COUNTER_VALUE, "2")
        device.setOrientationNatural()
        id(TestTags.NAVIGATION_SCREEN)
        scrollTo(TestTags.NAV_COUNTER_VALUE)
        assertText(TestTags.NAV_COUNTER_VALUE, "2")
    }

    @Test
    fun webViewContentIsReachableAndTalksToNative() {
        launch()
        login("test@example.com", "Password123!")
        id(TestTags.HOME_SCREEN)
        scrollTo(TestTags.scenario("webview")).click()
        // The WebView is a classic View, so it has a namespaced View id rather than a bare test tag.
        find(By.res(packageName, "webview"), WEB_TIMEOUT)

        // Inside the WebView, Chromium exposes HTML ids as resource ids.
        val input = find(By.res("web_input"), WEB_TIMEOUT)
        input.click()
        input.text = "Ada"
        find(By.res("web_button")).click()
        assertText(TestTags.WEBVIEW_MESSAGE, "button:Ada")

        id(TestTags.WEBVIEW_SEND_TO_WEB).click()
        assertTrue(device.wait(Until.hasObject(By.textContains(string(R.string.webview_native_greeting))), TIMEOUT))

        // Back leaves the hybrid screen for the native dashboard.
        id(TestTags.TOP_BAR_BACK).click()
        id(TestTags.HOME_SCREEN)
    }

    @Test
    fun systemBackNavigatesTheStack() {
        launch()
        login("test@example.com", "Password123!")
        id(TestTags.HOME_SCREEN)
        scrollTo(TestTags.scenario("navigation")).click()
        id(TestTags.NAV_PUSH_DETAIL).click()
        id(TestTags.NAV_DETAIL_PUSH_NEXT).click()
        assertText(TestTags.NAV_DETAIL_LEVEL, string(R.string.navigation_level, 2))
        device.pressBack()
        assertText(TestTags.NAV_DETAIL_LEVEL, string(R.string.navigation_level, 1))
        device.pressBack()
        id(TestTags.NAVIGATION_SCREEN)
    }

    private companion object {
        // Generous because UIAutomator, unlike Compose tests, does not wait for the UI to go idle: on a
        // software-rendered CI emulator a screen transition can take seconds. Waits end as soon as the
        // element appears, so a fast device does not pay for it.
        const val TIMEOUT = 15_000L
        const val WEB_TIMEOUT = 20_000L
        const val LOG_TAG = "UiAutomatorTest"
        const val LOG_CHUNK = 3_000
        const val MAX_FRAMES = 25
        const val MAX_SCROLLS = 15
        const val SCROLL_PERCENT = 0.8f
        const val SWIPE_STEPS = 20
    }
}
