package com.mobiletestplayground.app.uiautomator

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
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
import java.util.concurrent.TimeoutException
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
        dismissSystemDialogs()
        device.setOrientationNatural()
    }

    @After
    fun tearDown() {
        device.setOrientationNatural()
    }

    /**
     * Taps "Wait" on any "<app> isn't responding" system dialog. A freshly booted CI emulator often shows
     * one for the launcher, and it covers the app under test. Returns true if a dialog was dismissed.
     */
    private fun dismissSystemDialogs(): Boolean {
        var dismissed = false
        repeat(MAX_SYSTEM_DIALOGS) {
            val wait = device.findObject(By.res("android", "aerr_wait"))
                ?: device.findObject(By.pkg("android").text("Wait"))
                ?: return dismissed
            Log.w(LOG_TAG, "Dismissing a system \"isn't responding\" dialog")
            wait.click()
            device.wait(Until.gone(By.res("android", "aerr_wait")), DIALOG_TIMEOUT)
            dismissed = true
        }
        return dismissed
    }

    private fun launch(intent: Intent = context.packageManager.getLaunchIntentForPackage(packageName)!!) {
        dismissSystemDialogs()
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), TIMEOUT)
    }

    private fun launchDeepLink(link: String) = launch(Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(packageName))

    /**
     * Waits for the element and returns it, or null after [timeout]. UiAutomation caches the nodes it has
     * read and drops them only when the app sends accessibility events. On GitHub's emulator the app at
     * times sent none for seconds after a scroll, so `device.wait` could keep matching cached nodes from
     * before it (a failure dump showed a list with a card missing from the middle). Clearing the cache
     * before every poll makes each check read the app's current tree (API 34+).
     */
    private fun waitFor(selector: BySelector, timeout: Long): UiObject2? {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (true) {
            clearAccessibilityCache()
            device.findObject(selector)?.let { return it }
            if (SystemClock.uptimeMillis() >= deadline) return null
            SystemClock.sleep(POLL_INTERVAL)
        }
    }

    private fun clearAccessibilityCache() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.clearCache()
        }
    }

    /**
     * Finds the element once the UI has settled. UIAutomator does not wait for the app the way Compose
     * tests do: on a slow emulator a node can appear before its screen accepts input, and a click sent
     * then is lost. Waiting for idle (no accessibility events for a moment) and finding the node again
     * avoids that, and costs nothing noticeable on a fast device.
     */
    private fun find(selector: BySelector, timeout: Long = TIMEOUT): UiObject2 {
        val first = waitFor(selector, timeout)
            ?: if (dismissSystemDialogs()) waitFor(selector, timeout) else null
        assertNotNull(first, "Not found: $selector")
        device.waitForIdle(IDLE_TIMEOUT)
        return assertNotNull(waitFor(selector, timeout), "Not found after idle: $selector")
    }

    private fun id(tag: String): UiObject2 = find(By.res(tag))

    /**
     * Scrolls until the element is on screen: down first, then back up, each way until the list stops
     * moving. The accessibility tree lags behind a scroll: Compose reports the new position in a later
     * event, and `waitForIdle` returns at once when no event has arrived yet, so a single `hasObject`
     * right after the scroll can still see the old page. On a freshly booted emulator that lag let the
     * helper scroll past the element and back without ever seeing it. Each step therefore waits for
     * the element for a moment (see [waitFor]) before scrolling on.
     */
    private fun scrollTo(tag: String): UiObject2 {
        if (waitFor(By.res(tag), 0) != null) return id(tag)
        for (direction in listOf(Direction.DOWN, Direction.UP)) {
            var moved = true
            var steps = 0
            while (moved && steps++ < MAX_SCROLLS) {
                moved = scroll(direction)
                if (waitFor(By.res(tag), SCROLL_SETTLE_TIMEOUT) != null) return id(tag)
            }
        }
        return id(tag)
    }

    /**
     * Scrolls one step through the accessibility layer, as TalkBack does: ACTION_SCROLL_FORWARD or
     * ACTION_SCROLL_BACKWARD on the first scrollable node, then waits for the app's scroll event so
     * the next step starts from the new position. Swipe gestures were unreliable on the
     * software-rendered CI emulator (the list did not move at all), and an accessibility action does not
     * depend on touch timing. Falls back to a swipe when no scrollable node is exposed (right after
     * rotation the tree may still be rebuilding). Returns false once the list cannot move further.
     */
    private fun scroll(direction: Direction): Boolean {
        val action = if (direction == Direction.DOWN) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val scrollable = uiAutomation.rootInActiveWindow?.let(::firstScrollable)
        if (scrollable != null) {
            var moved = false
            try {
                uiAutomation.executeAndWaitForEvent(
                    { moved = scrollable.performAction(action) },
                    { it.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && it.packageName == packageName },
                    SCROLL_EVENT_TIMEOUT,
                )
            } catch (e: TimeoutException) {
                // Expected when the list was already at its end; otherwise the settle wait covers it.
                Log.d(LOG_TAG, "No scroll event (moved=$moved)", e)
            }
            return moved
        }
        val (from, to) = if (direction == Direction.DOWN) 3 to 1 else 1 to 3
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * from / 4,
            device.displayWidth / 2,
            device.displayHeight * to / 4,
            SWIPE_STEPS,
        )
        return true
    }

    private fun firstScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return null
    }

    /**
     * Scrolls to the element and clicks it. A list that is still settling after the scroll can replace
     * the node between finding and clicking (StaleObjectException), so the element is found again.
     */
    private fun scrollToAndClick(tag: String) {
        repeat(STALE_RETRIES) {
            try {
                scrollTo(tag).click()
                return
            } catch (e: StaleObjectException) {
                Log.w(LOG_TAG, "Stale node for $tag, retrying", e)
            }
        }
        scrollTo(tag).click()
    }

    private fun string(resId: Int, vararg args: Any) = context.getString(resId, *args)

    /**
     * Waits until the element shows [expected]. Reading `.text` right after navigation can hit a node
     * from the outgoing screen (StaleObjectException), so assertions wait for the expected state instead.
     */
    private fun assertText(tag: String, expected: String) {
        assertTrue(
            waitFor(By.res(tag).text(expected), TIMEOUT) != null,
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
        scrollToAndClick(TestTags.scenario("controls"))
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
        scrollToAndClick(TestTags.scenario("navigation"))
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
        scrollToAndClick(TestTags.scenario("webview"))
        // The WebView is a classic View, so it has a namespaced View id rather than a bare test tag. Only
        // its presence is checked: once the page loads, Chromium's own tree can take the node's place.
        assertTrue(waitFor(By.res(packageName, "webview"), WEB_TIMEOUT) != null, "WebView not shown")

        // Inside the WebView, Chromium exposes HTML ids as resource ids.
        val input = find(By.res("web_input"), WEB_TIMEOUT)
        input.click()
        input.text = "Ada"
        find(By.res("web_button")).click()
        assertText(TestTags.WEBVIEW_MESSAGE, "button:Ada")

        id(TestTags.WEBVIEW_SEND_TO_WEB).click()
        assertTrue(waitFor(By.textContains(string(R.string.webview_native_greeting)), TIMEOUT) != null)

        // Back leaves the hybrid screen for the native dashboard.
        id(TestTags.TOP_BAR_BACK).click()
        id(TestTags.HOME_SCREEN)
    }

    @Test
    fun systemBackNavigatesTheStack() {
        launch()
        login("test@example.com", "Password123!")
        id(TestTags.HOME_SCREEN)
        scrollToAndClick(TestTags.scenario("navigation"))
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
        const val STALE_RETRIES = 2
        const val IDLE_TIMEOUT = 3_000L
        const val MAX_SYSTEM_DIALOGS = 3
        const val DIALOG_TIMEOUT = 2_000L
        const val MAX_SCROLLS = 15
        const val SCROLL_EVENT_TIMEOUT = 3_000L
        const val SCROLL_SETTLE_TIMEOUT = 2_000L
        const val POLL_INTERVAL = 250L
        const val SWIPE_STEPS = 20
    }
}
