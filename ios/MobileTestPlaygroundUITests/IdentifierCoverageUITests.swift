import XCTest

/// Every element the accessibility layer exposes must carry an `accessibilityIdentifier`. Each test
/// opens a group of screens, scrolls each one to the end, and collects every element that has content
/// or behaviour (text, image, control, scroll view) but no identifier.
///
/// Like Android's merged tree, the text and image inside an identified control (button, row, switch,
/// field) belong to that control and need no identifier of their own.
///
/// Not checked: system-owned UI (keyboard and other system windows, status bar, scroll indicators, the
/// navigation bar's system back button and title) and the HTML inside the web view, which is
/// identified by its HTML ids.
final class IdentifierCoverageUITests: PlaygroundUITestCase {
    private var missing: [(screen: String, items: [String])] = []

    func testSignedOutScreensIdentifyEveryElement() {
        waitFor("login_screen")
        audit("login")

        tap("login_register")
        waitFor("register_screen")
        audit("register")
        goBack()

        tap("login_forgot_password")
        waitFor("forgot_email")
        audit("forgot_password")
        type("forgot_email", "test@example.com")
        tap("forgot_submit")
        waitFor("forgot_continue")
        audit("forgot_password_sent")
        tap("forgot_continue")
        waitFor("reset_password_screen")
        audit("reset_password")
        goBack()
        goBack()

        tap("login_settings")
        waitFor("settings_screen")
        audit("settings")
        tap("settings_test_mode")
        waitFor("test_mode_screen")
        audit("test_mode")

        assertNothingMissing()
    }

    func testMainScreensIdentifyEveryElement() {
        loginAsAdmin()
        audit("home")

        tap("bottom_nav_catalog")
        waitFor("product_001")
        audit("catalog")
        tap("product_001")
        waitFor("product_add_to_cart")
        audit("product_detail")
        tap("product_add_to_cart")

        tap("bottom_nav_cart")
        waitFor("cart_item_product_001")
        audit("cart")

        tap("bottom_nav_profile")
        waitFor("profile_screen")
        audit("profile")
        tap("profile_admin")
        waitFor("admin_dashboard")
        audit("admin")
        tap("admin_open_products")
        waitFor("admin_products")
        audit("admin_products")
        goBack()
        tap("admin_open_users")
        waitFor("admin_users")
        audit("admin_users")

        assertNothingMissing()
    }

    func testScenarioScreensIdentifyEveryElement() {
        loginAsAdmin()
        let scenarios = [
            "auth", "forms", "controls", "lists", "scrolling", "gestures", "navigation", "dialogs", "permissions",
            "webview", "network", "accessibility", "errors", "dynamic_content", "test_data", "deep_links",
            "date_time", "file_picker", "notifications"
        ]
        for id in scenarios {
            openScenarioFromTop(id)
            waitFor(id == "auth" ? "auth_scenario_screen" : "\(id)_screen", timeout: 10)
            audit("scenario_\(id)")
            goBack()
            waitFor("home_screen")
        }
        openScenarioFromTop("test_mode")
        waitFor("test_mode_screen")
        audit("scenario_test_mode")
        goBack()

        openScenarioFromTop("navigation")
        waitFor("navigation_screen")
        tap("nav_push_detail")
        waitFor("nav_detail_screen")
        audit("navigation_detail")
        goBack()
        tap("nav_open_modal")
        waitFor("nav_modal_screen")
        audit("navigation_modal")
        tap("nav_modal_close")
        tap("nav_open_nested")
        waitFor("nav_nested_screen")
        audit("navigation_nested")

        assertNothingMissing()
    }

    /// UI that only exists after an action: other tabs, the bottom sheet, the snackbar, error states.
    func testTransientStatesIdentifyEveryElement() {
        loginAsAdmin()

        openScenarioFromTop("lists")
        tap("lists_tab_dynamic")
        waitFor("dynamic_list_add")
        audit("lists_dynamic")
        tap("lists_tab_infinite")
        audit("lists_infinite")
        goBack()

        openScenarioFromTop("dialogs")
        tap("dialogs_open_bottom_sheet")
        waitFor("bottom_sheet")
        // No scrolling: a drag would dismiss the sheet.
        auditSnapshot("bottom_sheet")
        tap("bottom_sheet_close")
        tap("dialogs_show_snackbar")
        waitFor("snackbar")
        auditSnapshot("snackbar")
        goBack()

        openScenarioFromTop("errors")
        for trigger in ["network", "unexpected", "empty", "recoverable"] {
            tap("errors_trigger_\(trigger)")
            _ = element("errors_loading").waitForNonExistence(timeout: timeout)
            audit("errors_\(trigger)")
        }
        goBack()

        openScenarioFromTop("dynamic_content")
        tap("dynamic_content_load")
        waitFor("dynamic_content_loaded")
        tap("dynamic_content_toggle")
        audit("dynamic_content_loaded")

        assertNothingMissing()
    }

    // MARK: - Navigation

    private func loginAsAdmin() {
        login("admin@example.com", "Admin123!")
        waitFor("home_screen")
        app.dismissSavePasswordPrompt()
    }

    /// The dashboard keeps its scroll position after Back; `openScenario` only scrolls down, so start at the top.
    private func openScenarioFromTop(_ id: String) {
        let first = element("scenario_auth")
        var swipes = 0
        while !(first.exists && first.isHittable) && swipes < 10 {
            app.swipeDown()
            swipes += 1
        }
        openScenario(id)
    }

    private func goBack() {
        app.navigationBars.buttons.element(boundBy: 0).tap()
    }

    // MARK: - Audit

    /// Audits the screen, then scrolls down until the content stops changing and audits each position.
    private func audit(_ screen: String) {
        var previous = auditSnapshot(screen)
        for _ in 0..<maxScrolls {
            scroll(up: true)
            let current = auditSnapshot(screen)
            if current == previous { break }
            previous = current
        }
        for _ in 0..<maxScrolls {
            scroll(up: false)
            let current = signature()
            if current == previous { break }
            previous = current
        }
    }

    /// A slow drag along the right edge, away from sliders and gesture areas in the middle.
    private func scroll(up: Bool) {
        let top = app.coordinate(withNormalizedOffset: CGVector(dx: 0.96, dy: 0.3))
        let bottom = app.coordinate(withNormalizedOffset: CGVector(dx: 0.96, dy: 0.75))
        (up ? bottom : top).press(forDuration: 0.05, thenDragTo: up ? top : bottom, withVelocity: .slow, thenHoldForDuration: 0.1)
    }

    /// Records unidentified elements and returns a signature of what is visible, to detect the end of a scroll.
    @discardableResult
    private func auditSnapshot(_ screen: String) -> [String] {
        var found: [String] = []
        var visible = walkAppWindow(found: &found)
        // A snapshot taken mid-transition can be empty: take it again once.
        if !Self.hasIdentifiedElement(visible) {
            Thread.sleep(forTimeInterval: 1)
            found = []
            visible = walkAppWindow(found: &found)
        }
        // Guards the audit itself: a screen with no identified element means the walk saw nothing.
        if !Self.hasIdentifiedElement(visible) {
            found.append("audit saw no identified element (empty snapshot?)")
        }
        if !found.isEmpty {
            if let index = missing.firstIndex(where: { $0.screen == screen }) {
                for item in found where !missing[index].items.contains(item) { missing[index].items.append(item) }
            } else {
                var unique: [String] = []
                for item in found where !unique.contains(item) { unique.append(item) }
                missing.append((screen, unique))
            }
        }
        return visible
    }

    private static func hasIdentifiedElement(_ visible: [String]) -> Bool {
        visible.contains { !$0.split(separator: "|", omittingEmptySubsequences: false)[1].isEmpty }
    }

    private func signature() -> [String] {
        var found: [String] = []
        return walkAppWindow(found: &found)
    }

    /// Walks the app's main window only: the keyboard and other system UI live in separate windows.
    private func walkAppWindow(found: inout [String]) -> [String] {
        guard let root = try? app.snapshot() else { return [] }
        var visible: [String] = []
        let window = root.children.first { $0.elementType == .window } ?? root
        navigationBarTitle = Self.first(.navigationBar, in: window)?.identifier
        walk(window, taggedAncestor: nil, found: &found, visible: &visible)
        return visible
    }

    private func walk(_ node: XCUIElementSnapshot, taggedAncestor: String?, found: inout [String], visible: inout [String]) {
        if Self.skippedTypes.contains(node.elementType) || isSystemUI(node) { return }
        let id = node.identifier
        visible.append("\(node.elementType.rawValue)|\(id)|\(node.label)|\(node.frame.minY)")
        if isElement(node) && id.isEmpty {
            let text = node.label.isEmpty ? (node.value as? String ?? "") : node.label
            let location = taggedAncestor ?? "(no identified ancestor)"
            found.append("\(Self.typeName(node.elementType)) \"\(String(text.prefix(40)))\" in \(location)")
        }
        // The web view's HTML, the text inside a control, and the parts of an identified element that
        // reads as one (`.accessibilityElement(children: .combine)`, so it has a label or a value) are
        // not elements of their own.
        if node.elementType == .webView || Self.controlTypes.contains(node.elementType) { return }
        if !id.isEmpty && (!node.label.isEmpty || !((node.value as? String) ?? "").isEmpty) { return }
        let ancestor = id.isEmpty ? taggedAncestor : id
        for child in node.children { walk(child, taggedAncestor: ancestor, found: &found, visible: &visible) }
    }

    private func isElement(_ node: XCUIElementSnapshot) -> Bool {
        // A row cell without a label is only a container for the row's own elements.
        if node.elementType == .cell { return !node.label.isEmpty }
        if Self.elementTypes.contains(node.elementType) { return true }
        // Combined accessibility elements (e.g. a row read as one) surface as "other" with a label.
        return node.elementType == .other && !node.label.isEmpty
    }

    /// UI created by UIKit rather than the app: the navigation bar's back button and title (the app's
    /// own title is the `top_bar_title` toolbar item) and the scroll indicators.
    private func isSystemUI(_ node: XCUIElementSnapshot) -> Bool {
        if node.elementType == .button && node.identifier == "BackButton" { return true }
        if node.elementType == .staticText && node.identifier.isEmpty && node.label == navigationBarTitle { return true }
        return node.elementType == .other && (node.label.hasPrefix("Vertical scroll bar") || node.label.hasPrefix("Horizontal scroll bar"))
    }

    /// The title of the visible navigation bar (UIKit identifies the bar by it), read from the snapshot.
    private var navigationBarTitle: String?

    private static func first(_ type: XCUIElement.ElementType, in node: XCUIElementSnapshot) -> XCUIElementSnapshot? {
        if node.elementType == type { return node }
        for child in node.children {
            if let match = first(type, in: child) { return match }
        }
        return nil
    }

    private func assertNothingMissing() {
        guard !missing.isEmpty else { return }
        let total = missing.reduce(0) { $0 + $1.items.count }
        let report = missing.map { entry in
            "\(entry.screen) (\(entry.items.count)):\n" + entry.items.map { "  - \($0)" }.joined(separator: "\n")
        }.joined(separator: "\n")
        XCTFail("Elements without an accessibility identifier (\(total)):\n\(report)")
    }

    private let maxScrolls = 25

    /// System UI: system alerts and action sheets (UIAlertController) take no identifiers; tests address
    /// them by label, see docs/automation.md.
    private static let skippedTypes: Set<XCUIElement.ElementType> = [.keyboard, .statusBar, .alert, .sheet]

    /// Controls whose children (label text, icon) are part of the control. Cells are not among them:
    /// in SwiftUI forms and lists every row is a cell that holds the row's own elements.
    private static let controlTypes: Set<XCUIElement.ElementType> = [
        .button, .link, .switch, .toggle, .slider, .stepper, .textField, .secureTextField, .textView,
        .searchField, .segmentedControl, .picker, .datePicker, .menuItem
    ]

    private static let elementTypes: Set<XCUIElement.ElementType> = [
        .staticText, .button, .textField, .secureTextField, .textView, .searchField, .switch, .toggle, .slider,
        .stepper, .segmentedControl, .picker, .pickerWheel, .datePicker, .image, .link, .cell, .scrollView,
        .table, .collectionView, .progressIndicator, .activityIndicator, .menuItem, .webView
    ]

    private static func typeName(_ type: XCUIElement.ElementType) -> String {
        switch type {
        case .staticText: "text"
        case .button: "button"
        case .textField, .secureTextField, .textView, .searchField: "text field"
        case .switch, .toggle: "switch"
        case .image: "image"
        case .scrollView, .table, .collectionView: "scrollable"
        case .cell: "cell"
        case .other: "group"
        default: "element(\(type.rawValue))"
        }
    }
}
