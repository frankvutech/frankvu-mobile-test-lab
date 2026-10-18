import SwiftUI

struct HomeView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(Router.self) private var router
    @State private var query = ""

    private var visible: [Scenario] {
        let needle = query.trimmingCharacters(in: .whitespaces)
        guard !needle.isEmpty else { return Scenario.allCases }
        return Scenario.allCases.filter {
            L10n.tr($0.titleKey).localizedCaseInsensitiveContains(needle) || $0.id.localizedCaseInsensitiveContains(needle)
        }
    }

    var body: some View {
        List {
            Section {
                Text(env.auth.session.map { L10n.tr("home_welcome", $0.user.displayName) } ?? L10n.tr("home_welcome_guest"))
                    .font(.title2)
                    .accessibilityIdentifier(TestIDs.homeWelcome)
                TextField(L10n.tr("home_search"), text: $query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .accessibilityIdentifier(TestIDs.homeSearch)
            }
            Section {
                if visible.isEmpty {
                    Text(L10n.tr("home_no_results")).accessibilityIdentifier(TestIDs.homeEmptySearch)
                }
                ForEach(visible) { scenario in
                    Button {
                        router.push(scenario == .testMode ? .testMode : .scenario(scenario))
                    } label: {
                        Label {
                            VStack(alignment: .leading) {
                                Text(L10n.tr(scenario.titleKey)).font(.headline)
                                Text(L10n.tr(scenario.descriptionKey)).font(.caption).foregroundStyle(.secondary)
                            }
                        } icon: {
                            Image(systemName: scenario.systemImage)
                        }
                    }
                    .foregroundStyle(.primary)
                    .accessibilityIdentifier(TestIDs.scenario(scenario.id))
                }
            }
        }
        .accessibilityIdentifier(TestIDs.homeScenarioList)
        .navigationTitle(L10n.tr("app_name"))
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { router.show(tab: .cart) } label: { Image(systemName: "cart") }
                    .accessibilityLabel(L10n.tr("nav_cart"))
                    .accessibilityIdentifier(TestIDs.homeCart)
                Button { router.show(tab: .profile) } label: { Image(systemName: "person") }
                    .accessibilityLabel(L10n.tr("nav_profile"))
                    .accessibilityIdentifier(TestIDs.homeProfile)
                Button { router.push(.settings) } label: { Image(systemName: "gearshape") }
                    .accessibilityLabel(L10n.tr("settings_title"))
                    .accessibilityIdentifier(TestIDs.homeSettings)
            }
        }
        .screenRoot(TestIDs.homeScreen)
    }
}

extension FailureReason {
    var messageKey: String {
        switch self {
        case .unavailable: return "data_error_unavailable"
        case .unauthorized: return "data_error_unauthorized"
        case .notFound: return "product_not_found"
        case .conflict: return "data_error_conflict"
        case .unknown: return "data_error_unknown"
        }
    }
}

/// Loading → content or error, shared by screens that read from a repository.
enum Loadable<T> {
    case loading, ready(T), failed(FailureReason)

    init(_ result: Result<T, FailureReason>) {
        switch result {
        case .success(let value): self = .ready(value)
        case .failure(let reason): self = .failed(reason)
        }
    }

    var value: T? { if case .ready(let value) = self { return value } else { return nil } }
}

struct LoadStateView<T>: View {
    let state: Loadable<T>
    let loadingID: String
    let errorID: String
    let retry: () -> Void

    var body: some View {
        switch state {
        case .loading: ProgressView().accessibilityIdentifier(loadingID)
        case .failed(let reason):
            ErrorLabel(text: L10n.tr(reason.messageKey), id: errorID)
            Button(L10n.tr("action_retry"), action: retry).accessibilityIdentifier(TestIDs.dataRetry)
        case .ready: EmptyView()
        }
    }
}

struct CatalogView: View {
    @Environment(AppEnvironment.self) private var env
    @State private var state: Loadable<[Product]> = .loading
    @State private var actionError: FailureReason?

    var body: some View {
        List {
            LoadStateView(state: state, loadingID: TestIDs.catalogLoading, errorID: TestIDs.catalogError) { Task { await load() } }
            if let actionError { ErrorLabel(text: L10n.tr(actionError.messageKey), id: TestIDs.catalogActionError) }
            ForEach(state.value ?? []) { product in
                HStack {
                    NavigationLink {
                        ProductDetailView(productId: product.id)
                    } label: {
                        VStack(alignment: .leading) {
                            Text(product.name).font(.headline)
                            Text("\(product.category) · \(PriceFormatter.format(product.priceCents))").font(.caption)
                        }
                    }
                    .accessibilityIdentifier(TestIDs.product(product.id))
                    Button(L10n.tr("catalog_add")) { Task { actionError = await env.cart.add(product) } }
                        .buttonStyle(.bordered)
                        .accessibilityIdentifier(TestIDs.productAddToCart(product.id))
                }
            }
        }
        .accessibilityIdentifier(TestIDs.catalogList)
        .navigationTitle(L10n.tr("nav_catalog"))
        .task { await load() }
        .screenRoot(TestIDs.catalogScreen)
    }

    @MainActor
    private func load() async {
        state = .loading
        state = Loadable(await env.catalog.products())
    }
}

struct ProductDetailView: View {
    @Environment(AppEnvironment.self) private var env
    let productId: String
    @State private var state: Loadable<Product> = .loading
    @State private var actionError: FailureReason?

    var body: some View {
        Form {
            if case .failed(.notFound) = state {
                Text(L10n.tr("product_not_found")).accessibilityIdentifier(TestIDs.productNotFound)
            } else if let product = state.value {
                Text(product.name).font(.title2).accessibilityIdentifier(TestIDs.productName)
                ValueRow(label: L10n.tr("product_price"), value: PriceFormatter.format(product.priceCents), valueID: TestIDs.productPrice)
                ValueRow(label: L10n.tr("product_category"), value: product.category, valueID: TestIDs.productCategory)
                PrimaryButton(title: L10n.tr("catalog_add_to_cart"), id: TestIDs.productAddToCart) {
                    Task { actionError = await env.cart.add(product) }
                }
                if let actionError { ErrorLabel(text: L10n.tr(actionError.messageKey), id: TestIDs.catalogActionError) }
            } else {
                LoadStateView(state: state, loadingID: TestIDs.productLoading, errorID: TestIDs.productError) { Task { await load() } }
            }
        }
        .navigationTitle(state.value?.name ?? L10n.tr("nav_catalog"))
        .task { await load() }
        .screenRoot(TestIDs.productDetailScreen)
    }

    @MainActor
    private func load() async {
        state = .loading
        state = Loadable(await env.catalog.product(id: productId))
    }
}

struct CartView: View {
    @Environment(AppEnvironment.self) private var env
    @State private var order: (number: String, total: Int)?
    @State private var error: FailureReason?

    var body: some View {
        Form {
            if let order {
                Section(L10n.tr("cart_order_placed")) {
                    Text(order.number).accessibilityIdentifier(TestIDs.orderNumber)
                    Text("\(L10n.tr("cart_total")): \(PriceFormatter.format(order.total))")
                }
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier(TestIDs.orderConfirmation)
            }
            if let error { ErrorLabel(text: L10n.tr(error.messageKey), id: TestIDs.cartError) }
            if env.cart.lines.isEmpty {
                Text(L10n.tr("cart_empty")).accessibilityIdentifier(TestIDs.cartEmpty)
            } else {
                Section {
                    ForEach(env.cart.lines) { line in
                        HStack {
                            Text(line.product.name)
                            Spacer()
                            Text("× \(line.quantity)").accessibilityIdentifier(TestIDs.cartItemQuantity(line.product.id))
                            Button(role: .destructive) {
                                Task { error = await env.cart.remove(line.product.id) }
                            } label: { Image(systemName: "trash") }
                                .buttonStyle(.borderless)
                                .accessibilityLabel(L10n.tr("cart_remove", line.product.name))
                                .accessibilityIdentifier(TestIDs.cartRemove(line.product.id))
                        }
                        .accessibilityElement(children: .contain)
                        .accessibilityIdentifier(TestIDs.cartItem(line.product.id))
                    }
                    ValueRow(label: L10n.tr("cart_total"), value: PriceFormatter.format(env.cart.totalCents),
                             valueID: TestIDs.cartTotal)
                }
                PrimaryButton(title: L10n.tr("cart_checkout"), id: TestIDs.cartCheckoutButton) {
                    Task { @MainActor in
                        switch await env.cart.checkout() {
                        case .success(let placed):
                            order = (placed.0, placed.1)
                            error = nil
                        case .failure(let reason):
                            error = reason
                        }
                    }
                }
            }
        }
        .navigationTitle(L10n.tr("nav_cart"))
        // In backend mode the cart lives on the server: fetch it whenever the screen appears.
        .task { error = await env.cart.refresh() }
        .screenRoot(TestIDs.cartScreen)
    }
}

struct ProfileView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(Router.self) private var router

    var body: some View {
        Form {
            if let user = env.auth.session?.user {
                ValueRow(label: L10n.tr("profile_name"), value: user.displayName, valueID: TestIDs.profileName)
                ValueRow(label: L10n.tr("login_email"), value: user.email, valueID: TestIDs.profileEmail)
                ValueRow(label: L10n.tr("profile_role"), value: user.role.rawValue, valueID: TestIDs.profileRole)
                if user.role == .admin {
                    Button(L10n.tr("admin_title")) { router.push(.admin) }.accessibilityIdentifier(TestIDs.profileAdmin)
                }
            } else {
                Text(L10n.tr("profile_guest")).accessibilityIdentifier(TestIDs.profileName)
            }
            Button(L10n.tr("settings_title")) { router.push(.settings) }.accessibilityIdentifier(TestIDs.profileSettings)
            if env.auth.session != nil {
                Button(L10n.tr("profile_logout"), role: .destructive) { Task { await env.auth.endSession() } }
                    .accessibilityIdentifier(TestIDs.profileLogout)
            }
        }
        .navigationTitle(L10n.tr("nav_profile"))
        .screenRoot(TestIDs.profileScreen)
    }
}

struct AdminDashboardView: View {
    @Environment(Router.self) private var router

    var body: some View {
        Form {
            Text(L10n.tr("admin_intro"))
            Button(L10n.tr("admin_products")) { router.homePath.append(.adminProducts) }.accessibilityIdentifier(TestIDs.adminOpenProducts)
            Button(L10n.tr("admin_users")) { router.homePath.append(.adminUsers) }.accessibilityIdentifier(TestIDs.adminOpenUsers)
        }
        .navigationTitle(L10n.tr("admin_title"))
        .screenRoot(TestIDs.adminDashboard)
    }
}

struct AdminProductsView: View {
    var body: some View {
        List(SeedData.products) { product in
            ValueRow(label: product.name, value: PriceFormatter.format(product.priceCents), valueID: TestIDs.adminProduct(product.id))
        }
        .navigationTitle(L10n.tr("admin_products"))
        .screenRoot(TestIDs.adminProducts)
    }
}

struct AdminUsersView: View {
    var body: some View {
        List(SeedData.accounts, id: \.user.id) { account in
            ValueRow(label: account.user.email, value: account.locked ? L10n.tr("admin_user_locked") : account.user.role.rawValue,
                     valueID: TestIDs.adminUser(account.user.id))
        }
        .navigationTitle(L10n.tr("admin_users"))
        .screenRoot(TestIDs.adminUsers)
    }
}

struct AccessDeniedView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "nosign").font(.largeTitle).accessibilityHidden(true)
            Text(L10n.tr("access_denied_title")).font(.title2).accessibilityAddTraits(.isHeader)
            Text(L10n.tr("access_denied_message")).foregroundStyle(.red).accessibilityIdentifier(TestIDs.accessDeniedMessage)
            SecondaryButton(title: L10n.tr("action_go_back"), id: TestIDs.accessDeniedBack) { dismiss() }
        }
        .padding()
        .navigationTitle(L10n.tr("access_denied_title"))
        .screenRoot(TestIDs.accessDeniedScreen)
    }
}

struct SettingsView: View {
    @Environment(AppEnvironment.self) private var env

    var body: some View {
        Form {
            Section(L10n.tr("settings_theme")) {
                let theme = Binding(get: { env.preferences.theme }, set: { env.preferences.setTheme($0) })
                Picker(L10n.tr("settings_theme"), selection: theme) {
                    ForEach(ThemeMode.allCases) { mode in
                        Text(L10n.tr("settings_theme_\(mode.rawValue)")).tag(mode)
                            .accessibilityIdentifier(TestIDs.settingsTheme(mode.rawValue))
                    }
                }
                .pickerStyle(.inline)
                .labelsHidden()
            }
            Section(L10n.tr("scenario_test_mode")) {
                if AppEnvironment.testModeEnabled {
                    NavigationLink(L10n.tr("settings_open_test_mode")) { TestModeView() }.accessibilityIdentifier(TestIDs.settingsTestMode)
                } else {
                    Text(L10n.tr("test_mode_disabled")).accessibilityIdentifier(TestIDs.settingsTestModeUnavailable)
                }
            }
            ValueRow(label: L10n.tr("settings_data_source"),
                     value: env.testMode.settings.useBackend
                        ? "\(L10n.tr("data_source_backend")) · \(env.backend.baseURL)" : L10n.tr("data_source_offline"),
                     valueID: TestIDs.settingsDataSource)
            ValueRow(label: L10n.tr("settings_version"),
                     value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "",
                     valueID: TestIDs.settingsVersion)
        }
        .navigationTitle(L10n.tr("settings_title"))
        .screenRoot(TestIDs.settingsScreen)
    }
}

/// Backend versus offline/test mode, and the backend URL (a physical device needs the Mac's LAN address).
struct BackendSection: View {
    @Environment(AppEnvironment.self) private var env
    @State private var url = ""

    var body: some View {
        Section {
            Toggle(L10n.tr("test_mode_use_backend"), isOn: Binding(
                get: { env.testMode.settings.useBackend },
                set: { value in Task { await env.setUseBackend(value) } }
            ))
            .accessibilityIdentifier(TestIDs.testModeUseBackend)
            TextField(L10n.tr("test_mode_backend_url"), text: $url)
                .keyboardType(.URL)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .accessibilityIdentifier(TestIDs.testModeBackendUrl)
            Button(L10n.tr("action_save")) {
                let trimmed = url.trimmingCharacters(in: .whitespaces)
                env.updateTestMode { $0.backendURLOverride = trimmed.isEmpty ? nil : trimmed }
            }
            .accessibilityIdentifier(TestIDs.testModeBackendUrlSave)
            ValueRow(label: L10n.tr("test_mode_backend_active_url"), value: env.backend.baseURL, valueID: TestIDs.testModeBackendActiveUrl)
        } header: {
            Text(L10n.tr("test_mode_backend"))
        } footer: {
            Text(L10n.tr("test_mode_backend_hint") + " " + L10n.tr("test_mode_backend_url_hint"))
        }
        .onAppear { url = env.testMode.settings.backendURLOverride ?? "" }
    }
}

struct TestModeView: View {
    @Environment(AppEnvironment.self) private var env
    @State private var status: String?

    var body: some View {
        Form {
            if AppEnvironment.testModeEnabled {
                Text(L10n.tr("test_mode_warning")).font(.footnote)
                BackendSection()
                Section(L10n.tr("test_mode_simulation")) {
                    toggle("test_mode_network_error", TestIDs.testModeNetworkError, \.simulateNetworkError)
                    toggle("test_mode_slow_network", TestIDs.testModeSlowNetwork, \.simulateSlowNetwork)
                    toggle("test_mode_auth_required", TestIDs.testModeAuthRequired, \.authenticationRequired)
                }
                Section(L10n.tr("test_mode_data")) {
                    action("test_mode_generate_data", TestIDs.testModeGenerateData, done: "test_mode_done_generate_data") {
                        env.dynamicItems.generate()
                    }
                    action("test_mode_reset_data", TestIDs.testModeResetData, done: "test_mode_done_reset_data") {
                        env.dynamicItems.reset()
                        env.cart.resetToSeed()
                    }
                    action("test_mode_clear_storage", TestIDs.testModeClearStorage,
                           done: "test_mode_done_clear_storage", role: .destructive) {
                        env.clearLocalStorage()
                    }
                    action("test_mode_reset_app", TestIDs.testModeResetApp, done: "test_mode_done_reset_app", role: .destructive) {
                        env.resetApplicationState()
                    }
                }
                if let status { Text(status).accessibilityIdentifier(TestIDs.testModeStatus) }
            } else {
                Text(L10n.tr("test_mode_disabled")).accessibilityIdentifier(TestIDs.testModeDisabled)
            }
        }
        .navigationTitle(L10n.tr("scenario_test_mode"))
        .screenRoot(TestIDs.testModeScreen)
    }

    private func toggle(_ key: String, _ id: String, _ path: WritableKeyPath<TestModeSettings, Bool>) -> some View {
        Toggle(L10n.tr(key), isOn: Binding(get: { env.testMode.settings[keyPath: path] },
                                           set: { value in env.updateTestMode { $0[keyPath: path] = value } }))
            .accessibilityIdentifier(id)
    }

    private func action(_ key: String, _ id: String, done: String, role: ButtonRole? = nil, perform: @escaping () -> Void) -> some View {
        Button(L10n.tr(key), role: role) {
            perform()
            status = L10n.tr(done)
        }
        .accessibilityIdentifier(id)
    }
}
