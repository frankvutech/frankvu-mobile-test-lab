import SwiftUI

@main
struct MobileTestPlaygroundApp: App {
    @State private var environment = AppEnvironment()
    @State private var router = Router()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(environment)
                .environment(router)
                .preferredColorScheme(colorScheme)
                .onOpenURL { router.open(url: $0) }
        }
    }

    private var colorScheme: ColorScheme? {
        switch environment.preferences.theme {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

/// Chooses between the signed-out flow and the main tabs, and applies the access policy.
struct RootView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(Router.self) private var router

    private var signedIn: Bool { env.auth.session != nil }
    private var guestAllowed: Bool { router.guestMode && !env.testMode.settings.authenticationRequired }

    var body: some View {
        Group {
            if signedIn || guestAllowed {
                MainTabView()
            } else {
                NavigationStack {
                    LoginView(showRedirectMessage: router.pendingRoute != nil || router.pendingTab != nil)
                }
            }
        }
        .onChange(of: signedIn) { _, isSignedIn in
            // Logout always returns to a plain login screen with an empty back stack.
            if !isSignedIn { router.reset() }
        }
    }
}

struct MainTabView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(Router.self) private var router

    var body: some View {
        @Bindable var router = router
        TabView(selection: $router.tab) {
            NavigationStack(path: $router.homePath) {
                HomeView()
                    .navigationDestination(for: Route.self) { RouteView(route: $0) }
            }
            .tabItem { Label(L10n.tr("nav_home"), systemImage: "house").accessibilityIdentifier(TestIDs.bottomNavHome) }
            .tag(Tab.home)

            NavigationStack { CatalogView() }
                .tabItem { Label(L10n.tr("nav_catalog"), systemImage: "storefront").accessibilityIdentifier(TestIDs.bottomNavCatalog) }
                .tag(Tab.catalog)

            NavigationStack { CartView() }
                .tabItem { Label(L10n.tr("nav_cart"), systemImage: "cart").accessibilityIdentifier(TestIDs.bottomNavCart) }
                .badge(env.cart.count)
                .tag(Tab.cart)

            NavigationStack { ProfileView() }
                .tabItem { Label(L10n.tr("nav_profile"), systemImage: "person").accessibilityIdentifier(TestIDs.bottomNavProfile) }
                .tag(Tab.profile)
        }
        .onAppear { router.clearPending() }
    }
}

/// Renders a destination according to `AccessPolicy`.
struct RouteView: View {
    @Environment(AppEnvironment.self) private var env
    let route: Route

    var body: some View {
        switch AccessPolicy.decide(route.accessLevel, session: env.auth.session,
                                   authenticationRequired: env.testMode.settings.authenticationRequired) {
        case .granted: destination
        case .denied: AccessDeniedView()
        case .requiresLogin: LoginView(showRedirectMessage: true)
        }
    }

    @ViewBuilder private var destination: some View {
        switch route {
        case .scenario(let scenario): ScenarioView(scenario: scenario)
        case .product(let id): ProductDetailView(productId: id)
        case .admin: AdminDashboardView()
        case .adminProducts: AdminProductsView()
        case .adminUsers: AdminUsersView()
        case .settings: SettingsView()
        case .testMode: TestModeView()
        case .navDetail(let level): NavigationDetailView(level: level)
        case .navNested: NavigationNestedView()
        }
    }
}

struct ScenarioView: View {
    let scenario: Scenario

    var body: some View {
        switch scenario {
        case .auth: AuthScenarioView()
        case .forms: FormsView()
        case .controls: ControlsView()
        case .lists: ListsView()
        case .scrolling: ScrollingView()
        case .gestures: GesturesView()
        case .navigation: NavigationRootView()
        case .dialogs: DialogsView()
        case .permissions: PermissionsView()
        case .webview: WebViewScreen()
        case .network: NetworkView()
        case .accessibility: AccessibilityView()
        case .errors: ErrorHandlingView()
        case .dynamicContent: DynamicContentView()
        case .testData: TestDataView()
        case .deepLinks: DeepLinksView()
        case .dateTime: DateTimeView()
        case .filePicker: FilePickerView()
        case .notifications: NotificationsView()
        case .testMode: TestModeView()
        }
    }
}
