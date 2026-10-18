package com.mobiletestplayground.app.compose

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.ui.TestTags
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FormsTest : PlaygroundComposeTest() {
    @Before
    fun open() {
        robot.loginAsCustomer()
        robot.openScenario("forms")
        robot.waitFor(TestTags.FORMS_SCREEN)
    }

    @Test
    fun emptySubmitShowsRequiredErrors() {
        robot.tap(TestTags.FORM_SUBMIT)
        val required = robot.string(R.string.error_required)
        compose.onNodeWithTag(TestTags.formError(TestTags.FORM_FULL_NAME)).assertTextEquals(required)
        compose.onNodeWithTag(TestTags.formError(TestTags.FORM_EMAIL)).assertTextEquals(required)
        compose.onNodeWithTag(TestTags.formError(TestTags.FORM_TERMS)).assertTextEquals(robot.string(R.string.error_must_accept))
        compose.onNodeWithTag(TestTags.FORM_SUCCESS).assertDoesNotExist()
    }

    @Test
    fun invalidValuesShowSpecificErrors() {
        robot.type(TestTags.FORM_FULL_NAME, "A")
        robot.type(TestTags.FORM_EMAIL, "ada@")
        robot.type(TestTags.FORM_AGE, "abc")
        robot.tap(TestTags.FORM_SUBMIT)
        compose.onNodeWithTag(TestTags.formError(TestTags.FORM_FULL_NAME)).assertTextEquals(robot.string(R.string.error_too_short, 2))
        compose.onNodeWithTag(TestTags.formError(TestTags.FORM_EMAIL)).assertTextEquals(robot.string(R.string.login_error_invalid_email))
        compose.onNodeWithTag(TestTags.formError(TestTags.FORM_AGE)).assertTextEquals(robot.string(R.string.error_not_a_number))

        // Errors update live once shown.
        compose.onNodeWithTag(TestTags.FORM_AGE).performTextClearance()
        compose.onNodeWithTag(TestTags.FORM_AGE).performTextInput("17")
        compose.onNodeWithTag(TestTags.formError(TestTags.FORM_AGE)).assertTextEquals(robot.string(R.string.error_out_of_range, 18, 120))
    }

    @Test
    fun validSubmissionSucceeds() {
        robot.type(TestTags.FORM_FULL_NAME, "Ada Lovelace")
        robot.type(TestTags.FORM_EMAIL, "ada@example.com")
        robot.type(TestTags.FORM_PASSWORD, "password1")
        robot.type(TestTags.FORM_AGE, "36")
        robot.tap(TestTags.FORM_COUNTRY)
        robot.tapInPlace(TestTags.formCountryOption("de"))
        robot.tap(TestTags.formPlan("pro"))
        robot.tap(TestTags.FORM_DATE)
        robot.waitFor(TestTags.DATE_PICKER_DIALOG)
        robot.tapInPlace(TestTags.DATE_PICKER_CONFIRM)
        compose.onNodeWithTag(TestTags.FORM_DATE_VALUE).assertTextEquals("2025-01-15")
        robot.tap(TestTags.FORM_TIME)
        robot.tapInPlace(TestTags.TIME_PICKER_CONFIRM)
        compose.onNodeWithTag(TestTags.FORM_TIME_VALUE).assertTextEquals("09:30")
        robot.tap(TestTags.FORM_TERMS)
        robot.tap(TestTags.FORM_SUBMIT)
        robot.waitFor(TestTags.FORM_SUCCESS)

        robot.tap(TestTags.FORM_RESET)
        compose.onNodeWithTag(TestTags.FORM_SUCCESS).assertDoesNotExist()
        compose.onNodeWithTag(TestTags.FORM_DATE_VALUE).assertTextEquals(robot.string(R.string.value_not_set))
    }
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ControlsTest : PlaygroundComposeTest() {
    @Test
    fun everyControlHasObservableState() {
        robot.loginAsCustomer()
        robot.openScenario("controls")
        robot.waitFor(TestTags.CONTROLS_SCREEN)

        robot.tap(TestTags.CONTROLS_PRIMARY_BUTTON)
        robot.tap(TestTags.CONTROLS_SECONDARY_BUTTON)
        compose.onNodeWithTag(TestTags.CONTROLS_TAP_COUNT).assertTextEquals("2")
        compose.onNodeWithTag(TestTags.CONTROLS_DISABLED_BUTTON).assertIsNotEnabled()

        val on = robot.string(R.string.value_on)
        compose.onNodeWithTag(TestTags.CONTROLS_SWITCH).assertIsOff()
        robot.tap(TestTags.CONTROLS_SWITCH)
        compose.onNodeWithTag(TestTags.CONTROLS_SWITCH).assertIsOn()
        compose.onNodeWithTag(TestTags.CONTROLS_SWITCH_STATE).assertTextEquals(on)
        robot.tap(TestTags.CONTROLS_CHECKBOX)
        compose.onNodeWithTag(TestTags.CONTROLS_CHECKBOX_STATE).assertTextEquals(on)
        robot.tap(TestTags.CONTROLS_TOGGLE)
        compose.onNodeWithTag(TestTags.CONTROLS_TOGGLE_STATE).assertTextEquals(on)

        robot.tap(TestTags.controlsRadio("large"))
        compose.onNodeWithTag(TestTags.CONTROLS_RADIO_STATE).assertTextEquals(robot.string(R.string.size_large))

        compose.onNodeWithTag(TestTags.CONTROLS_STEPPER_DECREMENT).assertIsNotEnabled()
        repeat(3) { robot.tap(TestTags.CONTROLS_STEPPER_INCREMENT) }
        compose.onNodeWithTag(TestTags.CONTROLS_STEPPER_VALUE).assertTextEquals("3")
        compose.onNodeWithTag(TestTags.CONTROLS_STEPPER_DECREMENT).assertIsEnabled()

        robot.tap(TestTags.CONTROLS_PROGRESS_ADVANCE)
        compose.onNodeWithTag(TestTags.CONTROLS_PROGRESS_VALUE).assertTextEquals("25%")

        compose.onNodeWithTag(TestTags.CONTROLS_SLIDER).performSemanticsAction(SemanticsActions.SetProgress) { it(80f) }
        compose.onNodeWithTag(TestTags.CONTROLS_SLIDER_VALUE).assertTextEquals("80")
    }
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ListsTest : PlaygroundComposeTest() {
    @Before
    fun open() {
        robot.loginAsCustomer()
        robot.openScenario("lists")
        robot.waitFor(TestTags.LISTS_SCREEN)
    }

    @Test
    fun staticListScrollsToTheLastItem() {
        compose.onNodeWithTag(TestTags.listItem(30)).assertDoesNotExist()
        compose.onNodeWithTag(TestTags.STATIC_LIST).performScrollToNode(hasTestTag(TestTags.listItem(30)))
        compose.onNodeWithTag(TestTags.listItem(30)).assertIsDisplayed()
    }

    @Test
    fun dynamicListAddSearchEditAndDelete() {
        robot.tapInPlace(TestTags.LISTS_TAB_DYNAMIC)
        robot.waitFor(TestTags.dynamicListItem(5))
        compose.onNodeWithTag(TestTags.DYNAMIC_LIST_INPUT).performTextInput("Fig")
        robot.tapInPlace(TestTags.DYNAMIC_LIST_ADD)
        robot.waitFor(TestTags.dynamicListItem(6))

        compose.onNodeWithTag(TestTags.DYNAMIC_LIST_SEARCH).performTextInput("an")
        compose.onNodeWithTag(TestTags.DYNAMIC_LIST_COUNT).assertTextEquals(robot.string(R.string.lists_count, 1, 6))
        compose.onNodeWithTag(TestTags.dynamicListItem(2)).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.DYNAMIC_LIST_SEARCH).performTextClearance()

        robot.tapInPlace(TestTags.dynamicListEdit(1))
        robot.waitFor(TestTags.DYNAMIC_LIST_EDIT_DIALOG)
        compose.onNodeWithTag(TestTags.DYNAMIC_LIST_EDIT_INPUT).performTextClearance()
        compose.onNodeWithTag(TestTags.DYNAMIC_LIST_EDIT_INPUT).performTextInput("Apricot")
        robot.tapInPlace(TestTags.DIALOG_CONFIRM)
        robot.tapInPlace(TestTags.dynamicListSort("desc"))

        robot.tapInPlace(TestTags.dynamicListDelete(3))
        robot.waitFor(TestTags.DIALOG_CONFIRMATION)
        robot.tapInPlace(TestTags.DIALOG_CANCEL)
        compose.onNodeWithTag(TestTags.dynamicListItem(3)).assertIsDisplayed()
        robot.tapInPlace(TestTags.dynamicListDelete(3))
        robot.tapInPlace(TestTags.DIALOG_CONFIRM)
        robot.waitGone(TestTags.dynamicListItem(3))
        compose.onNodeWithTag(TestTags.DYNAMIC_LIST_COUNT).assertTextEquals(robot.string(R.string.lists_count, 5, 5))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun infiniteListLoadsUntilTheEnd() {
        robot.tapInPlace(TestTags.LISTS_TAB_INFINITE)
        // Lazy lists compose only visible rows, so wait for the first one rather than the 20th.
        robot.waitFor(TestTags.infiniteItem(1))
        // Keep scrolling: each page takes a fixed 700 ms; the list ends at row 100.
        repeat(MAX_SWIPES) {
            if (compose.onAllNodes(hasTestTag(TestTags.INFINITE_END)).fetchSemanticsNodes().isNotEmpty()) return@repeat
            compose.onNodeWithTag(TestTags.INFINITE_LIST).performTouchInput { swipeUp() }
            compose.waitForIdle()
            Thread.sleep(SETTLE_MILLIS)
        }
        robot.waitFor(TestTags.INFINITE_END, timeoutMillis = 10_000)
        compose.onNodeWithTag(
            TestTags.INFINITE_COUNT,
        ).assertTextEquals(compose.activity.resources.getQuantityString(R.plurals.lists_loaded, 100, 100))
    }

    private companion object {
        const val MAX_SWIPES = 40
        const val SETTLE_MILLIS = 150L
    }
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DialogsTest : PlaygroundComposeTest() {
    @Before
    fun open() {
        robot.loginAsCustomer()
        robot.openScenario("dialogs")
        robot.waitFor(TestTags.DIALOGS_SCREEN)
    }

    @Test
    fun confirmationDialogCancelAndConfirm() {
        robot.tap(TestTags.DIALOGS_OPEN_CONFIRMATION)
        robot.waitFor(TestTags.DIALOG_CONFIRMATION)
        robot.tapInPlace(TestTags.DIALOG_CANCEL)
        compose.onNodeWithTag(TestTags.DIALOGS_RESULT).assertTextEquals("cancelled")
        robot.tap(TestTags.DIALOGS_OPEN_CONFIRMATION)
        robot.tapInPlace(TestTags.DIALOG_CONFIRM)
        compose.onNodeWithTag(TestTags.DIALOG_CONFIRMATION).assertDoesNotExist()
        compose.onNodeWithTag(TestTags.DIALOGS_RESULT).assertTextEquals("confirmed")
    }

    @Test
    fun alertsSheetsAndSnackbar() {
        listOf(
            TestTags.DIALOGS_OPEN_INFORMATION to (TestTags.DIALOG_INFORMATION to "information_ok"),
            TestTags.DIALOGS_OPEN_WARNING to (TestTags.DIALOG_WARNING to "warning_ok"),
            TestTags.DIALOGS_OPEN_ERROR to (TestTags.DIALOG_ERROR to "error_ok"),
        ).forEach { (open, expected) ->
            robot.tap(open)
            robot.waitFor(expected.first)
            robot.tapInPlace(TestTags.DIALOG_OK)
            compose.onNodeWithTag(TestTags.DIALOGS_RESULT).assertTextEquals(expected.second)
        }

        robot.tap(TestTags.DIALOGS_OPEN_ACTION_SHEET)
        robot.waitFor(TestTags.actionSheetOption("archive"))
        robot.tapInPlace(TestTags.actionSheetOption("archive"))
        robot.waitGone(TestTags.ACTION_SHEET)
        compose.onNodeWithTag(TestTags.DIALOGS_RESULT).assertTextEquals("action_archive")

        robot.tap(TestTags.DIALOGS_OPEN_BOTTOM_SHEET)
        robot.waitFor(TestTags.BOTTOM_SHEET_CLOSE)
        robot.tapInPlace(TestTags.BOTTOM_SHEET_CLOSE)
        robot.waitGone(TestTags.BOTTOM_SHEET)
        compose.onNodeWithTag(TestTags.DIALOGS_RESULT).assertTextEquals("bottom_sheet_closed")
    }
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationTest : PlaygroundComposeTest() {
    @Before
    fun open() {
        robot.loginAsCustomer()
        robot.openScenario("navigation")
        robot.waitFor(TestTags.NAVIGATION_SCREEN)
    }

    @Test
    fun stackPushBackAndPopToRoot() {
        robot.tap(TestTags.NAV_PUSH_DETAIL)
        robot.waitFor(TestTags.NAV_DETAIL_SCREEN)
        compose.onNodeWithTag(TestTags.NAV_DETAIL_LEVEL).assertTextEquals(robot.string(R.string.navigation_level, 1))
        robot.tap(TestTags.NAV_DETAIL_PUSH_NEXT)
        robot.tap(TestTags.NAV_DETAIL_PUSH_NEXT)
        compose.onNodeWithTag(TestTags.NAV_DETAIL_LEVEL).assertTextEquals(robot.string(R.string.navigation_level, 3))
        robot.tapInPlace(TestTags.TOP_BAR_BACK)
        compose.onNodeWithTag(TestTags.NAV_DETAIL_LEVEL).assertTextEquals(robot.string(R.string.navigation_level, 2))
        robot.tap(TestTags.NAV_DETAIL_POP_TO_ROOT)
        robot.waitFor(TestTags.NAVIGATION_SCREEN)
    }

    @Test
    fun modalAndNestedNavigation() {
        robot.tap(TestTags.NAV_OPEN_MODAL)
        robot.waitFor(TestTags.NAV_MODAL_SCREEN)
        robot.tapInPlace(TestTags.NAV_MODAL_CLOSE)
        robot.waitGone(TestTags.NAV_MODAL_SCREEN)

        robot.tap(TestTags.NAV_OPEN_NESTED)
        robot.waitFor(TestTags.NAV_NESTED_SCREEN)
        compose.onNodeWithTag(TestTags.NAV_NESTED_CONTENT).assertTextEquals(robot.string(R.string.navigation_tab_content, "A"))
        robot.tapInPlace(TestTags.navNestedTab("c"))
        compose.onNodeWithTag(TestTags.NAV_NESTED_CONTENT).assertTextEquals(robot.string(R.string.navigation_tab_content, "C"))
    }

    @Test
    fun viewModelStateSurvivesActivityRecreation() {
        repeat(3) { robot.tap(TestTags.NAV_COUNTER_INCREMENT) }
        compose.onNodeWithTag(TestTags.NAV_COUNTER_VALUE).assertTextEquals("3")
        compose.activityRule.scenario.recreate()
        robot.waitFor(TestTags.NAVIGATION_SCREEN)
        compose.onNodeWithTag(TestTags.NAV_COUNTER_VALUE).assertTextEquals("3")
    }
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NetworkAndErrorsTest : PlaygroundComposeTest() {
    @Test
    fun httpErrorShowsMessageAndRetryRepeatsTheRequest() {
        robot.loginAsCustomer()
        robot.openScenario("network")
        robot.waitFor(TestTags.NETWORK_IDLE)
        robot.tap(TestTags.networkScenario("http_404"))
        robot.waitFor(TestTags.NETWORK_ERROR)
        compose.onNodeWithTag(TestTags.NETWORK_ERROR_MESSAGE).assertTextEquals(robot.string(R.string.network_error_404))
        robot.tap(TestTags.NETWORK_RETRY)
        robot.waitFor(TestTags.NETWORK_ERROR)

        robot.tap(TestTags.networkScenario("success"))
        robot.waitFor(TestTags.NETWORK_SUCCESS)
        compose.onNodeWithTag(TestTags.networkResultItem("product_005")).assertIsDisplayed()

        robot.tap(TestTags.networkScenario("slow"))
        robot.waitFor(TestTags.NETWORK_LOADING, timeoutMillis = 1_000)
        robot.waitFor(TestTags.NETWORK_SUCCESS)

        robot.tap(TestTags.networkScenario("empty"))
        robot.waitFor(TestTags.NETWORK_EMPTY)
    }

    @Test
    fun testModeNetworkErrorAffectsEveryRequest() {
        robot.loginAsCustomer()
        robot.tapInPlace(TestTags.HOME_SETTINGS)
        robot.tap(TestTags.SETTINGS_TEST_MODE)
        robot.tap(TestTags.TEST_MODE_NETWORK_ERROR)
        compose.onNodeWithTag(TestTags.TEST_MODE_NETWORK_ERROR).assertIsOn()
        robot.tapInPlace(TestTags.TOP_BAR_BACK)
        robot.tapInPlace(TestTags.TOP_BAR_BACK)
        robot.openScenario("network")
        robot.tap(TestTags.networkScenario("success"))
        robot.waitFor(TestTags.NETWORK_ERROR)
        compose.onNodeWithTag(TestTags.NETWORK_ERROR_MESSAGE).assertTextEquals(robot.string(R.string.network_error_connectivity))
    }

    @Test
    fun errorScenarioRecoversOnRetryAndValidatesInline() {
        robot.loginAsCustomer()
        robot.openScenario("errors")
        robot.tap(TestTags.ERRORS_TRIGGER_RECOVERABLE)
        compose.onNodeWithTag(TestTags.ERRORS_STATE).assertTextEquals("RECOVERABLE")
        robot.tap(TestTags.ERRORS_RETRY)
        compose.onNodeWithTag(TestTags.ERRORS_STATE).assertTextEquals("RECOVERED")
        robot.tap(TestTags.ERRORS_TRIGGER_UNEXPECTED)
        compose.onNodeWithTag(TestTags.ERRORS_MESSAGE).assertTextEquals(robot.string(R.string.errors_message_unexpected))
        robot.tap(TestTags.ERRORS_DISMISS)
        compose.onNodeWithTag(TestTags.ERRORS_STATE).assertTextEquals("NONE")

        robot.type(TestTags.ERRORS_INLINE_INPUT, "12a")
        compose.onNodeWithTag(TestTags.ERRORS_INLINE_ERROR).assertTextEquals(robot.string(R.string.errors_inline_digits))
    }
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class GesturesTest : PlaygroundComposeTest() {
    @Test
    fun gesturesProduceDeterministicResults() {
        robot.loginAsCustomer()
        robot.openScenario("gestures")
        robot.waitFor(TestTags.GESTURES_SCREEN)
        val result = compose.onNodeWithTag(TestTags.GESTURE_RESULT)

        compose.onNodeWithTag(TestTags.GESTURE_TAP_TARGET).performTouchInput { click() }
        result.assertTextEquals(robot.string(R.string.gesture_detected, "TAP"))
        compose.onNodeWithTag(TestTags.GESTURE_DOUBLE_TAP_TARGET).performTouchInput { doubleClick() }
        result.assertTextEquals(robot.string(R.string.gesture_detected, "DOUBLE_TAP"))
        compose.onNodeWithTag(TestTags.GESTURE_LONG_PRESS_TARGET).performTouchInput { longClick() }
        result.assertTextEquals(robot.string(R.string.gesture_detected, "LONG_PRESS"))
        compose.onNodeWithTag(TestTags.GESTURE_SWIPE_AREA).performTouchInput { swipeLeft() }
        result.assertTextEquals(robot.string(R.string.gesture_detected, "SWIPE_LEFT"))

        robot.tap(TestTags.GESTURE_ZOOM_IN)
        compose.onNodeWithTag(TestTags.GESTURE_ZOOM_LEVEL).assertTextEquals(robot.string(R.string.gesture_zoom_level, "2.0x"))
        repeat(5) { robot.tap(TestTags.GESTURE_ZOOM_IN) }
        compose.onNodeWithTag(TestTags.GESTURE_ZOOM_LEVEL).assertTextEquals(robot.string(R.string.gesture_zoom_level, "4.0x"))
    }
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TestModeAndIdsTest : PlaygroundComposeTest() {
    @Test
    fun dashboardExposesAllTwentyScenarioIds() {
        robot.loginAsCustomer()
        val ids = listOf(
            "auth", "forms", "controls", "lists", "scrolling", "gestures", "navigation", "dialogs", "permissions", "webview",
            "network", "accessibility", "errors", "dynamic_content", "test_data", "deep_links", "date_time", "file_picker",
            "notifications", "test_mode",
        )
        ids.forEach { id ->
            compose.onNodeWithTag(TestTags.HOME_SCENARIO_LIST).performScrollToNode(hasTestTag(TestTags.scenario(id)))
            compose.onNodeWithTag(TestTags.scenario(id)).assertIsDisplayed()
        }
    }

    @Test
    fun searchFiltersScenarios() {
        robot.loginAsCustomer()
        compose.onNodeWithTag(TestTags.HOME_SEARCH).performTextInput("zzz")
        robot.waitFor(TestTags.HOME_EMPTY_SEARCH)
        compose.onNodeWithTag(TestTags.HOME_SEARCH).performTextClearance()
        compose.onNodeWithTag(TestTags.HOME_SEARCH).performTextInput("webview")
        compose.onNodeWithTag(TestTags.scenario("webview")).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.scenario("forms")).assertDoesNotExist()
    }

    @Test
    fun resetApplicationStateSignsOutAndRestoresSeedData() {
        robot.loginAsCustomer()
        robot.openScenario("test_mode")
        robot.waitFor(TestTags.TEST_MODE_SCREEN)
        robot.tap(TestTags.TEST_MODE_GENERATE_DATA)
        robot.waitFor(TestTags.TEST_MODE_STATUS)
        robot.tap(TestTags.TEST_MODE_RESET_APP)
        robot.waitFor(TestTags.LOGIN_SCREEN)

        robot.loginAsCustomer()
        robot.openScenario("test_data")
        compose.onNodeWithTag(TestTags.TEST_DATA_ITEM_COUNT).assertTextEquals("5")
    }

    @Test
    fun disablingAuthenticationAllowsGuestAccessButNotAdmin() {
        robot.waitFor(TestTags.LOGIN_SCREEN)
        robot.tapInPlace(TestTags.LOGIN_SETTINGS)
        robot.tap(TestTags.SETTINGS_TEST_MODE)
        robot.tap(TestTags.TEST_MODE_AUTH_REQUIRED)
        compose.onNodeWithTag(TestTags.TEST_MODE_AUTH_REQUIRED).assertIsOff()

        robot.tapInPlace(TestTags.TOP_BAR_BACK)
        robot.tapInPlace(TestTags.TOP_BAR_BACK)
        robot.tap(TestTags.LOGIN_CONTINUE_AS_GUEST)
        robot.waitFor(TestTags.HOME_SCREEN)
        compose.onNodeWithTag(TestTags.HOME_WELCOME).assertTextEquals(robot.string(R.string.home_welcome_guest))

        robot.openScenario("auth")
        robot.tap(TestTags.AUTH_OPEN_ADMIN)
        robot.waitFor(TestTags.LOGIN_REDIRECT_MESSAGE)
        robot.login(PlaygroundRobot.ADMIN_EMAIL, PlaygroundRobot.ADMIN_PASSWORD)
        // After signing in, the original destination opens.
        robot.waitFor(TestTags.ADMIN_DASHBOARD)
    }

    @Test
    fun dynamicContentAppearsOnSchedule() {
        robot.loginAsCustomer()
        robot.openScenario("dynamic_content")
        compose.onNodeWithTag(TestTags.DYNAMIC_CONTENT_DELAYED_BUTTON).assertIsNotEnabled()
        robot.tap(TestTags.DYNAMIC_CONTENT_LOAD)
        robot.waitFor(TestTags.DYNAMIC_CONTENT_LOADING, timeoutMillis = 1_000)
        robot.waitFor(TestTags.DYNAMIC_CONTENT_LOADED)
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag(TestTags.DYNAMIC_CONTENT_DELAYED_BUTTON).assertIsEnabled() }.isSuccess
        }
        robot.tap(TestTags.DYNAMIC_CONTENT_TOGGLE)
        robot.waitGone(TestTags.DYNAMIC_CONTENT_TOGGLE_TARGET)
    }
}
