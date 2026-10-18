import Foundation
import Observation

/// The 20 scenarios on the dashboard. `id` is used for `scenario_<id>` identifiers and deep links.
enum Scenario: String, CaseIterable, Identifiable, Hashable {
    case auth, forms, controls, lists, scrolling, gestures, navigation, dialogs, permissions, webview
    case network, accessibility, errors
    case dynamicContent = "dynamic_content", testData = "test_data", deepLinks = "deep_links", dateTime = "date_time"
    case filePicker = "file_picker", notifications
    case testMode = "test_mode"

    var id: String { rawValue }

    var titleKey: String {
        switch self {
        case .dynamicContent: return "scenario_dynamic"
        default: return "scenario_\(rawValue)"
        }
    }

    var descriptionKey: String { titleKey + "_description" }

    var systemImage: String {
        switch self {
        case .auth: return "lock"
        case .forms: return "square.and.pencil"
        case .controls: return "switch.2"
        case .lists: return "list.bullet"
        case .scrolling: return "arrow.up.arrow.down"
        case .gestures: return "hand.tap"
        case .navigation: return "map"
        case .dialogs: return "bubble.left"
        case .permissions: return "checkmark.shield"
        case .webview: return "globe"
        case .network: return "cloud"
        case .accessibility: return "accessibility"
        case .errors: return "exclamationmark.triangle"
        case .dynamicContent: return "arrow.triangle.2.circlepath"
        case .testData: return "tablecells"
        case .deepLinks: return "link"
        case .dateTime: return "clock"
        case .filePicker: return "paperclip"
        case .notifications: return "bell"
        case .testMode: return "wrench.and.screwdriver"
        }
    }
}

/// Destinations pushed onto the Home stack.
enum Route: Hashable {
    case scenario(Scenario)
    case product(String)
    case admin, adminProducts, adminUsers
    case settings, testMode
    case navDetail(Int), navNested

    var accessLevel: AccessLevel {
        switch self {
        case .admin, .adminProducts, .adminUsers: return .admin
        case .settings, .testMode: return .publicAccess
        default: return .authenticated
        }
    }
}

enum Tab: Hashable { case home, catalog, cart, profile }

/// Navigation state for the whole app: selected tab, the Home stack, and a destination waiting
/// for login. Deep links and in-app links both go through `open(_:)`.
@Observable
final class Router {
    var tab: Tab = .home
    var homePath: [Route] = []
    var pendingRoute: Route?
    var pendingTab: Tab?
    var showNavModal = false
    /// Set by "Continue as guest" when Test Mode has disabled authentication.
    var guestMode = false

    func open(_ link: DeepLink) {
        switch link {
        case .home: show(tab: .home)
        case .cart: show(tab: .cart)
        case .profile: show(tab: .profile)
        case .admin: push(.admin)
        case .testMode: push(.testMode)
        case .product(let id): push(.product(id))
        case .detail(let level): push(.navDetail(level))
        case .scenario(let id):
            if id == Scenario.testMode.id {
                push(.testMode)
            } else if let scenario = Scenario(rawValue: id) {
                push(.scenario(scenario))
            }
        }
    }

    func open(url: URL) {
        if let link = DeepLinkParser.parse(url.absoluteString) { open(link) }
    }

    func push(_ route: Route) {
        tab = .home
        homePath.append(route)
        pendingRoute = route
    }

    func show(tab: Tab) {
        self.tab = tab
        pendingTab = tab
    }

    /// Called once the destination has been shown to a signed-in user.
    func clearPending() {
        pendingRoute = nil
        pendingTab = nil
    }

    func reset() {
        tab = .home
        homePath = []
        showNavModal = false
        guestMode = false
    }
}
