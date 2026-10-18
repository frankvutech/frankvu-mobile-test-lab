import XCTest

/// XCUITest examples. Every query uses `accessibilityIdentifier` values (shared with Android test
/// tags); expected text is read from the app's own string table so tests work in any locale.
///
/// Each test launches with `-uiTestReset` (clean storage), `-uiTestFastTiming` (short delays) and
/// `-offlineMode` (local data). `BackendUITests` instead runs against a live backend.
class PlaygroundUITestCase: XCTestCase {
    var app: XCUIApplication!
    let timeout: TimeInterval = 5

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        // -offlineMode: these UI suites use the local deterministic data, so no backend is needed.
        app.launchArguments += ["-uiTestReset", "-uiTestFastTiming", "-offlineMode", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
    }

    /// Any element type with the identifier. SwiftUI sometimes exposes an identifier on both a container
    /// and its control, so the first match is used.
    func element(_ id: String) -> XCUIElement { app.descendants(matching: .any).matching(identifier: id).firstMatch }

    @discardableResult
    func waitFor(_ id: String, timeout: TimeInterval? = nil, file: StaticString = #filePath, line: UInt = #line) -> XCUIElement {
        let target = element(id)
        XCTAssertTrue(target.waitForExistence(timeout: timeout ?? self.timeout), "Missing \(id)", file: file, line: line)
        return target
    }

    /// Taps an element, scrolling first if it is not yet on screen (lists and forms are lazy).
    func tap(_ id: String, file: StaticString = #filePath, line: UInt = #line) {
        app.dismissSavePasswordPrompt(timeout: 0)
        let target = element(id)
        // A number pad has no Return key and covers the lower half of the screen: close it first.
        if app.keyboards.firstMatch.exists && !(target.exists && target.isHittable) { app.dismissKeyboard() }
        var swipes = 0
        while !(target.exists && target.isHittable) && swipes < 12 {
            if target.waitForExistence(timeout: swipes == 0 ? 2 : 0.5) && target.isHittable { break }
            // Slow, short swipes: a fast one moves a whole page and lazy rows that scroll past are removed.
            app.swipeUp(velocity: .slow)
            swipes += 1
        }
        XCTAssertTrue(target.exists, "Missing \(id)", file: file, line: line)
        // A tap while the list is still decelerating after a swipe is swallowed by the scroll view.
        if swipes > 0 { Thread.sleep(forTimeInterval: 0.8) }
        // When a row container and its button share the identifier, tap the button itself: the
        // container's centre can fall outside the button.
        let button = app.buttons.matching(identifier: id).firstMatch
        (button.exists ? button : target).tap()
    }

    func type(_ id: String, _ text: String) {
        let field = waitFor(id)
        field.tap()
        field.typeText(text)
    }

    func text(_ id: String) -> String { waitFor(id).label }

    /// The label of an element that may be below the fold of a lazy list: swipes until it exists.
    func scrolledText(_ id: String, file: StaticString = #filePath, line: UInt = #line) -> String {
        let target = element(id)
        var swipes = 0
        while !target.waitForExistence(timeout: 1) && swipes < 12 {
            app.swipeUp(velocity: .slow)
            swipes += 1
        }
        XCTAssertTrue(target.exists, "Missing \(id)", file: file, line: line)
        return target.label
    }

    func login(_ email: String, _ password: String) {
        waitFor("login_screen")
        type("login_email", email)
        type("login_password", password)
        tap("login_button")
    }

    func loginAsCustomer() {
        login("test@example.com", "Password123!")
        waitFor("home_screen")
        app.dismissSavePasswordPrompt()
    }

    /// Scrolls the dashboard until the scenario row is visible, then opens it.
    func openScenario(_ id: String) {
        let row = element("scenario_\(id)")
        var attempts = 0
        while !(row.exists && row.isHittable) && attempts < 10 {
            // The AutoFill sheet can arrive late; it would make every row unhittable.
            if app.dismissSavePasswordPrompt(timeout: 0) { continue }
            app.swipeUp()
            attempts += 1
        }
        row.tap()
    }
}

final class AuthenticationUITests: PlaygroundUITestCase {
    func testIncorrectPasswordShowsError() {
        login("test@example.com", "wrong")
        XCTAssertEqual(text("login_error"), "Incorrect password.")
    }

    func testLockedAccount() {
        login("locked@example.com", "Locked123!")
        XCTAssertEqual(text("login_error"), "This account is locked.")
    }

    func testLoginAndLogout() {
        loginAsCustomer()
        XCTAssertEqual(text("home_welcome"), "Hello, Test Customer")
        app.tabBars.buttons.element(boundBy: 3).tap()
        XCTAssertEqual(text("profile_role"), "CUSTOMER")
        tap("profile_logout")
        waitFor("login_screen")
        XCTAssertFalse(element("login_redirect_message").exists)
    }

    func testKeyboardInteractionSubmitsWithReturn() {
        waitFor("login_screen")
        type("login_email", "test@example.com")
        let password = element("login_password")
        password.tap()
        password.typeText("Password123!\n")
        waitFor("home_screen")
        app.dismissSavePasswordPrompt()
    }
}

final class AuthorizationUITests: PlaygroundUITestCase {
    func testCustomerIsDeniedAdmin() {
        loginAsCustomer()
        openScenario("auth")
        tap("auth_open_admin")
        XCTAssertEqual(text("access_denied_message"), "You do not have permission to view this screen.")
    }

    func testAdminSeesAdminDashboard() {
        login("admin@example.com", "Admin123!")
        waitFor("home_screen")
        app.dismissSavePasswordPrompt()
        openScenario("auth")
        tap("auth_open_admin")
        waitFor("admin_dashboard")
        tap("admin_open_users")
        XCTAssertEqual(text("admin_user_locked"), "Locked")
    }

    func testDeepLinkWhileSignedOutRedirectsThenContinues() {
        app.open(URL(string: "mtp://playground/product/product_003")!)
        waitFor("login_redirect_message")
        login("test@example.com", "Password123!")
        app.dismissSavePasswordPrompt()
        XCTAssertEqual(text("product_name"), "Product 003")
        XCTAssertEqual(text("product_price"), "12.49")
    }
}

final class FormsUITests: PlaygroundUITestCase {
    func testEmptySubmitShowsErrorsAndValidSubmitSucceeds() {
        loginAsCustomer()
        openScenario("forms")
        waitFor("forms_screen")
        tap("form_submit")
        // 8 required fields are empty (comments are optional); the summary sits next to the button.
        XCTAssertEqual(scrolledText("form_error_summary"), "8 fields need attention.")
        for _ in 0..<6 { app.swipeDown() }
        XCTAssertEqual(text("form_full_name_error"), "This field is required.")
        type("form_full_name", "Ada Lovelace")
        type("form_email", "ada@example.com")
        type("form_password", "password1")
        type("form_age", "36")
        element("form_country").tap()
        app.buttons["Germany"].tap()
        app.buttons["Pro"].tap()
        tap("form_date_confirm")
        XCTAssertEqual(text("form_date_value"), "2025-01-15")
        let terms = app.switches["form_terms"]
        while !terms.isHittable { app.swipeUp() }
        // SwiftUI Toggle: tap the switch control itself, not the row label.
        if terms.switches.firstMatch.exists {
            terms.switches.firstMatch.tap()
        } else {
            terms.tap()
        }
        tap("form_submit")
        waitFor("form_success")
    }
}

final class ListsAndNavigationUITests: PlaygroundUITestCase {
    func testStaticListScrollsToLastItem() {
        loginAsCustomer()
        openScenario("lists")
        let last = element("list_item_030")
        var swipes = 0
        while !last.exists && swipes < 15 {
            app.swipeUp()
            swipes += 1
        }
        XCTAssertTrue(last.exists)
    }

    func testDynamicListDeleteWithConfirmationAlert() {
        loginAsCustomer()
        openScenario("lists")
        app.buttons["Dynamic"].tap()
        tap("dynamic_list_delete_3")
        let alert = app.alerts.firstMatch
        XCTAssertTrue(alert.waitForExistence(timeout: timeout))
        alert.buttons["Cancel"].firstMatch.tap()
        XCTAssertTrue(element("dynamic_list_item_3").exists)
        tap("dynamic_list_delete_3")
        app.alerts.firstMatch.buttons["Delete"].firstMatch.tap()
        XCTAssertTrue(element("dynamic_list_item_3").waitForNonExistence(timeout: timeout))
    }

    func testStackNavigationBackAndModal() {
        loginAsCustomer()
        openScenario("navigation")
        tap("nav_push_detail")
        tap("nav_detail_push_next")
        XCTAssertEqual(text("nav_detail_level"), "Level 2")
        app.navigationBars.buttons.element(boundBy: 0).tap()
        XCTAssertEqual(text("nav_detail_level"), "Level 1")
        tap("nav_detail_pop_to_root")
        tap("nav_open_modal")
        waitFor("nav_modal_screen")
        tap("nav_modal_close")
        XCTAssertTrue(element("nav_modal_screen").waitForNonExistence(timeout: timeout))
    }

    func testStateSurvivesBackgroundAndForeground() {
        loginAsCustomer()
        openScenario("controls")
        tap("controls_primary_button")
        tap("controls_primary_button")
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertEqual(text("controls_tap_count"), "2")
    }
}

final class AlertsAndGesturesUITests: PlaygroundUITestCase {
    func testConfirmationAndActionSheet() {
        loginAsCustomer()
        openScenario("dialogs")
        tap("dialogs_open_confirmation")
        app.alerts.firstMatch.buttons["Delete"].firstMatch.tap()
        XCTAssertEqual(text("dialogs_result"), "confirmed")
        tap("dialogs_open_action_sheet")
        app.buttons["Archive"].firstMatch.tap()
        XCTAssertEqual(text("dialogs_result"), "action_archive")
    }

    func testGestures() {
        loginAsCustomer()
        openScenario("gestures")
        element("gesture_tap_target").tap()
        XCTAssertEqual(text("gesture_result"), "Gesture detected: TAP")
        element("gesture_double_tap_target").doubleTap()
        XCTAssertEqual(text("gesture_result"), "Gesture detected: DOUBLE_TAP")
        element("gesture_long_press_target").press(forDuration: 1.0)
        XCTAssertEqual(text("gesture_result"), "Gesture detected: LONG_PRESS")
        element("gesture_swipe_area").swipeLeft()
        XCTAssertEqual(text("gesture_result"), "Gesture detected: SWIPE_LEFT")
        tap("gesture_zoom_in")
        XCTAssertEqual(text("gesture_zoom_level"), "Zoom level: 2.0x")
    }
}

final class WebViewUITests: PlaygroundUITestCase {
    func testWebViewRoundTrip() {
        loginAsCustomer()
        openScenario("webview")
        let web = app.webViews.firstMatch
        XCTAssertTrue(web.waitForExistence(timeout: 10))
        let field = web.textFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        field.tap()
        field.typeText("Ada")
        web.buttons["Send to app"].tap()
        XCTAssertTrue(element("webview_message").waitForLabel("button:Ada", timeout: timeout))
        tap("webview_send_to_web")
        XCTAssertTrue(web.staticTexts["From app: Hello from the app"].waitForExistence(timeout: timeout))
        app.navigationBars.buttons.element(boundBy: 0).tap()
        waitFor("home_screen")
    }
}

final class IdentifierUITests: PlaygroundUITestCase {
    func testDashboardExposesScenarioIdentifiers() {
        loginAsCustomer()
        for id in ["auth", "forms", "controls", "lists"] { XCTAssertTrue(element("scenario_\(id)").exists, id) }
        type("home_search", "zzz")
        waitFor("home_empty_search")
    }

    func testTestModeResetReturnsToLogin() {
        loginAsCustomer()
        openScenario("test_mode")
        tap("test_mode_reset_app")
        waitFor("login_screen")
    }
}

extension XCUIApplication {
    /// Closes the keyboard by tapping the navigation bar (works for keyboards without a Return key).
    func dismissKeyboard() {
        let bar = navigationBars.firstMatch
        if bar.exists { bar.tap() }
    }

    /// iOS AutoFill offers to save the password after a successful login, in a sheet that covers the
    /// app and blocks every tap. Real users see it too; tests decline it.
    @discardableResult
    func dismissSavePasswordPrompt(timeout: TimeInterval = 5) -> Bool {
        let notNow = buttons["Not Now"].firstMatch
        guard timeout == 0 ? notNow.exists : notNow.waitForExistence(timeout: timeout) else { return false }
        notNow.tap()
        return true
    }
}

extension XCUIElement {
    func waitForLabel(_ label: String, timeout: TimeInterval) -> Bool {
        let predicate = NSPredicate(format: "label == %@", label)
        return XCTWaiter().wait(for: [XCTNSPredicateExpectation(predicate: predicate, object: self)], timeout: timeout) == .completed
    }
}

/// End to end against a running backend (`make backend-run`). Skipped when it is not reachable.
final class BackendUITests: XCTestCase {
    func testLoginAndCheckoutAgainstTheBackend() throws {
        let base = ProcessInfo.processInfo.environment["PLAYGROUND_BACKEND_URL"] ?? "http://localhost:8080"
        var request = URLRequest(url: URL(string: base + "/api/v1/test/reset")!)
        request.httpMethod = "POST"
        let reset = XCTestExpectation(description: "reset")
        var status = 0
        URLSession.shared.dataTask(with: request) { _, response, _ in
            status = (response as? HTTPURLResponse)?.statusCode ?? 0
            reset.fulfill()
        }.resume()
        wait(for: [reset], timeout: 5)
        try XCTSkipUnless(status == 204, "Backend not reachable at \(base)")

        let app = XCUIApplication()
        app.launchArguments += ["-uiTestReset", "-uiTestFastTiming", "-backendURL", base, "-AppleLanguages", "(en)"]
        app.launch()
        let email = app.textFields["login_email"]
        XCTAssertTrue(email.waitForExistence(timeout: 5))
        email.tap()
        email.typeText("test@example.com")
        let password = app.secureTextFields["login_password"]
        password.tap()
        password.typeText("Password123!")
        app.buttons["login_button"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["home_screen"].waitForExistence(timeout: 10))
        app.dismissSavePasswordPrompt()

        app.dismissSavePasswordPrompt()
        app.tabBars.buttons.element(boundBy: 1).tap()
        XCTAssertTrue(app.descendants(matching: .any)["catalog_screen"].firstMatch.waitForExistence(timeout: 10))
        let add = app.buttons["product_001_add_to_cart"].firstMatch
        XCTAssertTrue(add.waitForExistence(timeout: 10))
        add.tap()
        app.tabBars.buttons.element(boundBy: 2).tap()
        XCTAssertTrue(app.descendants(matching: .any)["cart_item_product_001"].firstMatch.waitForExistence(timeout: 10))
        let checkout = app.buttons["cart_checkout_button"].firstMatch
        XCTAssertTrue(checkout.waitForExistence(timeout: 10))
        checkout.tap()
        XCTAssertTrue(app.staticTexts["ORDER-0001"].waitForExistence(timeout: 10))
    }
}
