# Automation guide

This project supports native automation only:

- **Android:** Compose UI Testing (in-process, semantics tree) and UIAutomator (out-of-process,
  accessibility layer).
- **iOS:** XCTest (unit) and XCUITest (UI).

All examples select elements by automation identifier. The full list is in [test-ids.md](test-ids.md).

## Android

### How identifiers reach each framework

Every element carries `Modifier.testTag("<id>")`. The root composable sets
`Modifier.semantics { testTagsAsResourceId = true }`, which publishes each tag as the node's
resource id, so:

| Framework | Selector |
|---|---|
| Compose UI Test | `composeTestRule.onNodeWithTag("login_email")` |
| UIAutomator | `device.findObject(By.res("login_email"))` |
| `adb shell uiautomator dump` | `resource-id="login_email"` |

The embedded WebView is a classic View: UIAutomator finds it as `By.res(packageName, "webview")`,
and HTML elements inside it by their HTML ids, e.g. `By.res("web_input")`.

### Running

```bash
cd android
./gradlew :app:connectedDebugAndroidTest                       # everything, on a running emulator/device
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.mobiletestplayground.app.compose.FormsTest
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.mobiletestplayground.app.uiautomator
```

Instrumented tests run in the offline/test mode by default (`TestAppModule` replaces the app's Hilt
module), so they do not need a backend. `BackendIntegrationTest` switches to backend mode in Test
Mode and needs the backend at `http://10.0.2.2:8080` (`make backend-run` on the host).

Instrumented tests use the AndroidX Test Orchestrator with `clearPackageData`, so **every test
starts from a clean install** (no session, seed data, default Test Mode). Tests run with Hilt
(`HiltTestRunner`) against the real dependency graph.

### Compose UI Test example

```kotlin
@HiltAndroidTest
class AuthenticationTest : PlaygroundComposeTest() {
    @Test
    fun incorrectPasswordShowsError() {
        robot.login("test@example.com", "wrong")
        robot.waitFor(TestTags.LOGIN_ERROR)
        compose.onNodeWithTag(TestTags.LOGIN_ERROR)
            .assertTextEquals(robot.string(R.string.login_error_incorrect_password))
    }
}
```

Expected text is read from string resources (`robot.string(...)`), so tests pass in any locale.
Waiting uses `waitUntilAtLeastOneExists` / `waitUntilDoesNotExist` — never `Thread.sleep` for
app state.

### UIAutomator example

```kotlin
launchDeepLink("mtp://playground/product/product_003")
id(TestTags.LOGIN_REDIRECT_MESSAGE)                 // redirected to login
login("test@example.com", "Password123!")
assertText(TestTags.PRODUCT_NAME, "Product 003")    // continues to the product
```

`assertText` waits for the expected text (`Until.hasObject(By.res(tag).text(expected))`) instead of
reading `.text` immediately, which avoids `StaleObjectException` while screens transition.

### Suites

| Suite | Framework | Covers |
|---|---|---|
| `AuthenticationTest` | Compose | Every login error, loading state, login/logout, password toggle, register, password reset |
| `AuthorizationTest` | Compose | Customer denied admin, admin allowed, logout removes access, cart checkout |
| `FormsTest` | Compose | Required/invalid/boundary errors, live revalidation, dropdown, radio, date/time pickers, success, reset |
| `ControlsTest` | Compose | Buttons, disabled state, switch, checkbox, toggle, radio, stepper bounds, progress, slider (semantics action) |
| `ListsTest` | Compose | Static list scroll-to-node, dynamic add/search/edit/sort/delete-with-confirm, infinite list to the end |
| `DialogsTest` | Compose | Confirmation cancel/confirm, info/warning/error alerts, action sheet, bottom sheet |
| `NavigationTest` | Compose | Stack push/back/pop-to-root, modal, nested NavHost, state across activity recreation |
| `NetworkAndErrorsTest` | Compose | HTTP 404 + retry, success, slow, empty, Test Mode network error, error recovery, inline validation |
| `GesturesTest` | Compose | Tap, double tap, long press, swipe, zoom clamp |
| `TestModeAndIdsTest` | Compose | All 20 scenario IDs, search, reset application state, guest mode + login redirect, dynamic content timing |
| `BackendIntegrationTest` | Compose | Against the live backend: backend login errors, catalog from the API, sold-out 409, server cart, checkout `ORDER-0001`, logout. Skipped when the backend is unreachable. |
| `UiAutomatorTest` | UIAutomator | Login/logout via resource ids, error text, deep link while signed out, admin deep link as customer, deep link into a stack, background/foreground, rotation, WebView round trip, system Back |

### Useful adb commands

```bash
adb shell am start -a android.intent.action.VIEW -d "mtp://playground/scenario/forms" com.mobiletestplayground.app
adb shell pm clear com.mobiletestplayground.app        # clear app data (fresh install state)
adb shell am force-stop com.mobiletestplayground.app   # process restart (keeps persisted data)
adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml
adb shell pm revoke com.mobiletestplayground.app android.permission.CAMERA
```

## iOS

### Identifiers

Every element uses `.accessibilityIdentifier("<id>")` with the same values as Android
(`TestIDs.swift` is generated from the Android catalogue). Screen roots use `.screenRoot(id)`, which
makes the container an accessibility element that still exposes its children.

```swift
app.textFields["login_email"]
app.descendants(matching: .any)["login_error"].label
app.switches["form_terms"]
```

### Launch arguments

| Argument | Effect |
|---|---|
| `-uiTestReset` | Wipes local storage before the app loads: every launch is a clean install. |
| `-uiTestFastTiming` | Shorter fixed delays (login 0.3 s, network slow 0.5 s). |
| `-AppleLanguages (de)` | Run in German; identifiers are unchanged. |
| `-offlineMode` | Use the local deterministic data instead of the backend (the UI suites use this). |
| `-backendURL <url>` | Use a specific backend URL. |

### Running

```bash
make ios-test        # unit tests in the simulator
make ios-ui-test     # XCUITest
make ios-test IOS_DESTINATION='platform=iOS Simulator,name=iPhone 17,OS=latest'
xcrun simctl openurl booted "mtp://playground/scenario/forms"
```

### XCUITest example

```swift
func testIncorrectPasswordShowsError() {
    login("test@example.com", "wrong")
    XCTAssertEqual(text("login_error"), "Incorrect password.")
}
```

Suites in `MobileTestPlaygroundUITests`: authentication (errors, login/logout, keyboard Return),
authorization (customer denied, admin allowed, deep link redirect), forms, lists (scroll, delete
alert), navigation (stack, back, modal, background/foreground), alerts and action sheet, gestures,
WebView round trip, identifiers and Test Mode reset.

## Identifier coverage

Every element either app shows has an automation ID. Two audit tests enforce this and fail on any
element without one, so a new screen or control cannot ship untagged:

| Platform | Test | Sees |
|---|---|---|
| Android | `IdentifierCoverageTest` (Compose, instrumented) | The merged semantics tree, which is what TalkBack and UIAutomator see |
| iOS | `IdentifierCoverageUITests` (XCUITest) | Accessibility snapshots of the app window |

Both open every screen: signed out, signed in as admin, all 20 scenarios and the navigation
sub-screens. They scroll every container to its end. Both also open the states that only exist after
an action: the other list tabs, the bottom sheet, the snackbar, the error states and the loaded
dynamic content. On Android this also covers the four dialogs, the action sheet and the country menu.

```bash
cd android && ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.mobiletestplayground.app.compose.IdentifierCoverageTest
xcodebuild test -project ios/MobileTestPlayground.xcodeproj -scheme MobileTestPlayground \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -only-testing:MobileTestPlaygroundUITests/IdentifierCoverageUITests
```

A failure lists each element as `<kind> "<text>" in <nearest identified ancestor>`, grouped by
screen. Name new IDs by the rules in [test-ids.md](test-ids.md).

**What counts as an element:** text, images, input fields, buttons, toggles, sliders, progress
indicators and scroll containers. Text and icons inside an identified control are part of that
control. The same applies to an identified element that reads as one (merged, so it has its own
label or value), such as a list row. On Android this follows from the merged tree. On iOS, XCUITest
lists a button's text as a child, so the audit skips the children of identified controls and merged
elements.

**Not checked, because the app cannot set an identifier on them:**
- **Android:** two nodes Material's modal bottom sheet adds itself, the dismiss scrim ("Close
  sheet") and the drag-handle wrapper ("Drag handle"). Address the sheet by `action_sheet` /
  `bottom_sheet`.
- **Both:** system-drawn UI: status bar, keyboard, scroll indicators, the system back button,
  permission prompts, and the contents of the platform date/time pickers. Address a picker by its
  own ID.
- **iOS:** system alerts and confirmation dialogs (the four dialogs and the action sheet are
  `UIAlertController`s, whose title, message and buttons take no identifiers; address them by label,
  e.g. `app.alerts.buttons["Delete"]`). Also the HTML inside the web view; use its HTML ids.

## Platform differences

| Area | Android | iOS |
|---|---|---|
| Screen title | `top_bar_title` (app bar title) | `top_bar_title` (a toolbar item set by `screenTitle(_:)`; the system title stays for VoiceOver and back buttons) |
| Scroll container | `<screen>_scroll`, separate from the screen root | The screen root is usually the scroll container itself (`Form`/`List`), so it has one ID; `dialogs_scroll` exists where an overlay wraps the form |
| Back button | `top_bar_back` | System back button: `app.navigationBars.buttons.element(boundBy: 0)` |
| Bottom navigation | `bottom_nav_*` tags | `bottom_nav_*` identifiers on the tab bar buttons (verified on iOS 26 / Xcode 27); index-based `app.tabBars.buttons.element(boundBy: n)` also works |
| Date picker | Dialog: `form_date` → `date_picker_confirm` | Inline `DatePicker` (`form_date`) + `form_date_confirm` to apply it |
| Stepper | `controls_stepper_increment` / `_decrement` | Native `Stepper` (`controls_stepper`) |
| Dialog buttons | `dialog_confirm`, `dialog_cancel`, `dialog_ok` | System alerts: select buttons by label (`app.alerts.buttons["Delete"]`) |
| Permission denial | Denied after the first refusal (can ask again); permanently denied after the second | iOS asks once; any refusal is reported as **Denied permanently** (Settings only) |
| Guest mode | `login_continue_as_guest` after disabling authentication in Test Mode | Same |
| Save Password prompt | Not shown | iOS AutoFill offers "Save Password?" after login; the sheet blocks taps. Tests dismiss it with "Not Now" (`dismissSavePasswordPrompt()`) |
| Lazy lists and forms | Compose `performScrollTo` / `performScrollToNode` | Rows below the fold do not exist yet: swipe until they do (`tap`, `scrolledText` helpers) |
| Remember Me across restart | Verified by JVM tests (`RememberMeTest`); instrumented tests share the app process and cannot kill it | Verified by unit tests (`AuthTests`); XCUITest can `terminate()` and relaunch without `-uiTestReset` |
| Notifications | "Send now" posts immediately | iOS requires a ≥ 1 s trigger, so "Send now" fires after 1 s |

## Scenario reference

- **Network** (`network_scenario_<id>`): `success`, `slow` (2 s), `timeout` (after 1 s), `http_400`,
  `http_401`, `http_403`, `http_404`, `http_500`, `empty`, `malformed`. Test Mode's network error
  overrides every scenario with a connectivity error.
- **Dynamic content**: load (1.5 s), delayed button (enabled 3 s after opening), countdown (5 → Done,
  1 s steps), text change (after 1 s).
- **Infinite list**: pages of 20 after 0.7 s each; ends at row 100 (`infinite_end`).
- **Dialog results** (`dialogs_result`) are stable codes: `confirmed`, `cancelled`, `information_ok`,
  `warning_ok`, `error_ok`, `action_<option>`, `action_cancelled`, `bottom_sheet_closed`,
  `snackbar_undo`, `snackbar_dismissed`.
- **Gesture results** (`gesture_result`): `Gesture detected: TAP | DOUBLE_TAP | LONG_PRESS | SWIPE_LEFT |
  SWIPE_RIGHT | SWIPE_UP | SWIPE_DOWN | DRAG_AND_DROP | PINCH | PAN`. Zoom is clamped to 1.0x–4.0x.
  Coordinate-free alternatives: `gesture_zoom_in`, `gesture_zoom_out`, and accessibility actions on the
  swipe area.
- **Deep links**: `mtp://playground/` + `home`, `cart`, `profile`, `admin`, `test-mode`,
  `scenario/<id>`, `product/<id>`, `detail/<n>`. Access rules apply after navigation.
