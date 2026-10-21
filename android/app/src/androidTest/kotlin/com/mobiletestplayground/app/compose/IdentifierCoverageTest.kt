package com.mobiletestplayground.app.compose

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.mobiletestplayground.app.navigation.Scenario
import com.mobiletestplayground.core.ui.TestTags
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import kotlin.test.fail

/**
 * Every element the accessibility layer exposes must carry an automation ID (a test tag, published
 * as the resource id). The test opens every screen, scrolls every scrollable container to its end,
 * and collects each semantics node that has content or behaviour (text, description, input, click,
 * toggle, progress, scrolling) but no test tag.
 *
 * It walks the merged tree, i.e. what TalkBack and UIAutomator see: the text inside a tagged button
 * belongs to the button and needs no ID of its own.
 */
@HiltAndroidTest
class IdentifierCoverageTest : PlaygroundComposeTest() {

    private val missing = linkedMapOf<String, MutableSet<String>>()

    @Test
    fun signedOutScreensTagEveryElement() {
        robot.waitFor(TestTags.LOGIN_SCREEN)
        audit("login")

        robot.tap(TestTags.LOGIN_REGISTER)
        robot.waitFor(TestTags.REGISTER_SCREEN)
        audit("register")
        back()

        robot.tap(TestTags.LOGIN_FORGOT_PASSWORD)
        robot.waitFor(TestTags.FORGOT_EMAIL)
        audit("forgot_password")
        robot.type(TestTags.FORGOT_EMAIL, PlaygroundRobot.CUSTOMER_EMAIL)
        robot.tap(TestTags.FORGOT_SUBMIT)
        robot.waitFor(TestTags.FORGOT_CONTINUE)
        audit("forgot_password_sent")
        robot.tap(TestTags.FORGOT_CONTINUE)
        robot.waitFor(TestTags.RESET_SCREEN)
        audit("reset_password")
        back()
        back()

        robot.waitFor(TestTags.LOGIN_SCREEN)
        compose.onNodeWithTag(TestTags.LOGIN_SETTINGS).performClick()
        robot.waitFor(TestTags.SETTINGS_SCREEN)
        audit("settings")
        robot.tap(TestTags.SETTINGS_TEST_MODE)
        robot.waitFor(TestTags.TEST_MODE_SCREEN)
        audit("test_mode")

        assertNothingMissing()
    }

    @Test
    fun mainScreensTagEveryElement() {
        robot.loginAsAdmin()
        audit("home")

        robot.tapInPlace(TestTags.BOTTOM_NAV_CATALOG)
        robot.waitFor(TestTags.product("product_001"))
        audit("catalog")
        robot.tap(TestTags.product("product_001"))
        robot.waitFor(TestTags.PRODUCT_ADD_TO_CART)
        audit("product_detail")
        robot.tap(TestTags.PRODUCT_ADD_TO_CART)
        back()

        robot.tapInPlace(TestTags.BOTTOM_NAV_CART)
        robot.waitFor(TestTags.cartItem("product_001"))
        audit("cart")

        robot.tapInPlace(TestTags.BOTTOM_NAV_PROFILE)
        robot.waitFor(TestTags.PROFILE_SCREEN)
        audit("profile")
        robot.tap(TestTags.PROFILE_ADMIN)
        robot.waitFor(TestTags.ADMIN_DASHBOARD)
        audit("admin")
        robot.tap(TestTags.ADMIN_OPEN_PRODUCTS)
        robot.waitFor(TestTags.ADMIN_PRODUCTS)
        audit("admin_products")
        back()
        robot.tap(TestTags.ADMIN_OPEN_USERS)
        robot.waitFor(TestTags.ADMIN_USERS)
        audit("admin_users")

        assertNothingMissing()
    }

    @Test
    fun scenarioScreensTagEveryElement() {
        robot.loginAsAdmin()
        Scenario.entries.forEach { scenario ->
            openDeepLink(if (scenario == Scenario.TEST_MODE) "test-mode" else "scenario/${scenario.id}")
            compose.waitForIdle()
            audit("scenario_${scenario.id}")
        }

        openDeepLink("scenario/navigation")
        robot.tap(TestTags.NAV_PUSH_DETAIL)
        robot.waitFor(TestTags.NAV_DETAIL_SCREEN)
        audit("navigation_detail")
        back()
        robot.tap(TestTags.NAV_OPEN_MODAL)
        robot.waitFor(TestTags.NAV_MODAL_SCREEN)
        audit("navigation_modal")
        compose.onNodeWithTag(TestTags.NAV_MODAL_CLOSE).performClick()
        robot.tap(TestTags.NAV_OPEN_NESTED)
        robot.waitFor(TestTags.NAV_NESTED_SCREEN)
        audit("navigation_nested")

        assertNothingMissing()
    }

    private fun openDeepLink(path: String) {
        val activity = compose.activity
        compose.runOnUiThread {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("mtp://playground/$path")).setPackage(activity.packageName),
            )
        }
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** Audits what is on screen, then every position of every vertically scrollable container. */
    private fun audit(screen: String) {
        compose.waitForIdle()
        auditVisible(screen)
        scrollables().forEach { tag ->
            auditWhileScrolling(tag, screen)
            // Back to the top, so the screen is as the next step expects it.
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.let { node ->
                compose.runOnUiThread { node.config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, -SCROLL_BACK) }
                compose.waitForIdle()
            }
        }
    }

    private fun auditWhileScrolling(tag: String, screen: String) {
        run {
            repeat(MAX_SCROLL_STEPS) {
                // A nested container can leave the composition while its parent scrolls.
                val node = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull() ?: return@run
                val range = node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) ?: return@run
                if (range.value() >= range.maxValue()) return@run
                compose.runOnUiThread {
                    node.config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, node.size.height * SCROLL_FRACTION)
                }
                compose.waitForIdle()
                auditVisible(screen)
            }
        }
    }

    /** Tags of vertically scrollable containers on screen; an untagged one is reported by [auditVisible]. */
    private fun scrollables(): List<String> = roots().flatMap { root ->
        root.descendants().filter { SemanticsActions.ScrollBy in it.config && SemanticsProperties.VerticalScrollAxisRange in it.config }
            .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
    }

    private fun auditVisible(screen: String) {
        roots().forEach { root ->
            root.descendants().filter { it.isElement() && SemanticsProperties.TestTag !in it.config }
                .forEach { missing.getOrPut(screen) { linkedSetOf() } += it.describe() }
        }
    }

    private fun roots(): List<SemanticsNode> = compose.onAllNodes(isRoot()).fetchSemanticsNodes()

    private fun SemanticsNode.descendants(): Sequence<SemanticsNode> = sequence {
        yield(this@descendants)
        children.forEach { yieldAll(it.descendants()) }
    }

    private fun SemanticsNode.isElement(): Boolean = ELEMENT_KEYS.any { it in config }

    private fun SemanticsNode.describe(): String {
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")
            ?: config.getOrNull(SemanticsProperties.EditableText)?.text
            ?: config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
            ?: ""
        val role = config.getOrNull(SemanticsProperties.Role)?.toString()
        val kind = role ?: when {
            SemanticsActions.OnClick in config -> "clickable"
            SemanticsActions.ScrollBy in config -> "scrollable"
            SemanticsProperties.ProgressBarRangeInfo in config -> "progress"
            SemanticsProperties.EditableText in config -> "text field"
            else -> "text"
        }
        return "$kind \"${text.take(MAX_TEXT)}\" in ${nearestTaggedAncestor() ?: "(no tagged ancestor)"}"
    }

    private fun SemanticsNode.nearestTaggedAncestor(): String? {
        var node = parent
        while (node != null) {
            node.config.getOrNull(SemanticsProperties.TestTag)?.let { return it }
            node = node.parent
        }
        return null
    }

    private fun assertNothingMissing() {
        if (missing.isEmpty()) return
        val report = missing.entries.joinToString("\n") { (screen, items) ->
            "$screen (${items.size}):\n" + items.joinToString("\n") { "  - $it" }
        }
        report.lines().forEach { Log.e(LOG_TAG, it) }
        fail("Elements without an automation ID (${missing.values.sumOf { it.size }}):\n$report")
    }

    private companion object {
        const val LOG_TAG = "IdentifierCoverage"
        const val MAX_SCROLL_STEPS = 40
        const val SCROLL_FRACTION = 0.6f
        const val MAX_TEXT = 40
        const val SCROLL_BACK = 1_000_000f

        val ELEMENT_KEYS = listOf(
            SemanticsProperties.Text,
            SemanticsProperties.EditableText,
            SemanticsProperties.ContentDescription,
            SemanticsProperties.ToggleableState,
            SemanticsProperties.ProgressBarRangeInfo,
            SemanticsActions.OnClick,
            SemanticsActions.ScrollBy,
            SemanticsActions.SetProgress,
        )
    }
}
