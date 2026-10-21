import XCTest

/// Every element the accessibility layer exposes must carry an `accessibilityIdentifier`. Each test
/// opens a group of screens, scrolls each one to the end, and collects every element that has content
/// or behaviour (text, image, control, scroll view) but no identifier.
///
/// Not checked: system-owned UI (keyboard, status bar, the navigation bar's system back button) and
/// the HTML inside the web view, which is identified by its HTML ids.
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
            openLink("scenario/\(id)")
            waitFor(id == "auth" ? "auth_scenario_screen" : "\(id)_screen", timeout: 10)
            audit("scenario_\(id)")
        }
        openLink("test-mode")
        waitFor("test_mode_screen")
        audit("scenario_test_mode")

        openLink("scenario/navigation")
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

    // MARK: - Navigation

    private func loginAsAdmin() {
        login("admin@example.com", "Admin123!")
        waitFor("home_screen")
        app.dismissSavePasswordPrompt()
    }

    private func openLink(_ path: String) {
        app.open(URL(string: "mtp://playground/\(path)")!)
        _ = app.wait(for: .runningForeground, timeout: timeout)
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
        guard let root = try? app.snapshot() else { return [] }
        var found: [String] = []
        var visible: [String] = []
        walk(root, taggedAncestor: nil, found: &found, visible: &visible)
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

    private func signature() -> [String] {
        guard let root = try? app.snapshot() else { return [] }
        var found: [String] = []
        var visible: [String] = []
        walk(root, taggedAncestor: nil, found: &found, visible: &visible)
        return visible
    }

    private func walk(_ node: XCUIElementSnapshot, taggedAncestor: String?, found: inout [String], visible: inout [String]) {
        if Self.skippedTypes.contains(node.elementType) || isSystemBackButton(node) { return }
        let id = node.identifier
        visible.append("\(node.elementType.rawValue)|\(id)|\(node.label)|\(node.frame.minY)")
        if isElement(node) && id.isEmpty {
            let text = node.label.isEmpty ? (node.value as? String ?? "") : node.label
            let location = taggedAncestor ?? "(no identified ancestor)"
            found.append("\(Self.typeName(node.elementType)) \"\(String(text.prefix(40)))\" in \(location)")
        }
        if node.elementType == .webView { return }
        let ancestor = id.isEmpty ? taggedAncestor : id
        for child in node.children { walk(child, taggedAncestor: ancestor, found: &found, visible: &visible) }
    }

    private func isElement(_ node: XCUIElementSnapshot) -> Bool {
        if Self.elementTypes.contains(node.elementType) { return true }
        // Combined accessibility elements (e.g. a row read as one) surface as "other" with a label.
        return node.elementType == .other && !node.label.isEmpty
    }

    /// The navigation bar's back button is created by UIKit and named after the previous screen.
    private func isSystemBackButton(_ node: XCUIElementSnapshot) -> Bool {
        node.elementType == .button && node.identifier == "BackButton"
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

    private static let skippedTypes: Set<XCUIElement.ElementType> = [.keyboard, .statusBar]

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
