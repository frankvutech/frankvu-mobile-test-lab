import AVFoundation
import CoreLocation
import Photos
import PhotosUI
import SwiftUI
import UniformTypeIdentifiers
import UserNotifications
import WebKit

// MARK: - Dialogs

struct DialogsView: View {
    private enum Surface: String { case none, confirmation, information, warning, error, actionSheet, bottomSheet }

    @State private var open = Surface.none
    @State private var result: String?
    @State private var snackbarVisible = false

    var body: some View {
        Form {
            ValueRow(label: L10n.tr("dialogs_result"), value: result ?? L10n.tr("value_none"), valueID: TestIDs.dialogsResult)
            Button(L10n.tr("dialogs_confirmation")) { open = .confirmation }.accessibilityIdentifier(TestIDs.dialogsOpenConfirmation)
            Button(L10n.tr("dialogs_information")) { open = .information }.accessibilityIdentifier(TestIDs.dialogsOpenInformation)
            Button(L10n.tr("dialogs_warning")) { open = .warning }.accessibilityIdentifier(TestIDs.dialogsOpenWarning)
            Button(L10n.tr("dialogs_error")) { open = .error }.accessibilityIdentifier(TestIDs.dialogsOpenError)
            Button(L10n.tr("dialogs_action_sheet")) { open = .actionSheet }.accessibilityIdentifier(TestIDs.dialogsOpenActionSheet)
            Button(L10n.tr("dialogs_bottom_sheet")) { open = .bottomSheet }.accessibilityIdentifier(TestIDs.dialogsOpenBottomSheet)
            Button(L10n.tr("dialogs_snackbar")) {
                snackbarVisible = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 4) {
                    if snackbarVisible {
                        snackbarVisible = false
                        result = "snackbar_dismissed"
                    }
                }
            }
            .accessibilityIdentifier(TestIDs.dialogsShowSnackbar)
        }
        .overlay(alignment: .bottom) {
            if snackbarVisible {
                HStack {
                    Text(L10n.tr("dialogs_snackbar_message"))
                    Spacer()
                    Button(L10n.tr("action_undo")) {
                        snackbarVisible = false
                        result = "snackbar_undo"
                    }
                }
                .padding()
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 8))
                .padding()
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier(TestIDs.snackbar)
            }
        }
        .alert(L10n.tr("dialog_delete_title"), isPresented: binding(.confirmation)) {
            Button(L10n.tr("action_delete"), role: .destructive) { result = "confirmed" }.accessibilityIdentifier(TestIDs.dialogConfirm)
            Button(L10n.tr("action_cancel"), role: .cancel) { result = "cancelled" }.accessibilityIdentifier(TestIDs.dialogCancel)
        } message: {
            Text(L10n.tr("dialog_delete_message", "Item 001"))
        }
        .alert(L10n.tr("dialogs_information"), isPresented: binding(.information)) {
            Button(L10n.tr("action_ok")) { result = "information_ok" }.accessibilityIdentifier(TestIDs.dialogOk)
        } message: { Text(L10n.tr("dialogs_information_body")) }
        .alert(L10n.tr("dialogs_warning"), isPresented: binding(.warning)) {
            Button(L10n.tr("action_ok")) { result = "warning_ok" }.accessibilityIdentifier(TestIDs.dialogOk)
        } message: { Text(L10n.tr("dialogs_warning_body")) }
        .alert(L10n.tr("dialogs_error"), isPresented: binding(.error)) {
            Button(L10n.tr("action_ok")) { result = "error_ok" }.accessibilityIdentifier(TestIDs.dialogOk)
        } message: { Text(L10n.tr("dialogs_error_body")) }
        .confirmationDialog(L10n.tr("dialogs_action_sheet"), isPresented: binding(.actionSheet), titleVisibility: .visible) {
            ForEach(["share", "duplicate", "archive"], id: \.self) { option in
                Button(L10n.tr("dialogs_action_\(option)")) { result = "action_\(option)" }
                    .accessibilityIdentifier(TestIDs.actionSheetOption(option))
            }
            Button(L10n.tr("action_cancel"), role: .cancel) { result = "action_cancelled" }
                .accessibilityIdentifier(TestIDs.actionSheetCancel)
        }
        .sheet(isPresented: binding(.bottomSheet)) {
            VStack(alignment: .leading, spacing: 12) {
                Text(L10n.tr("dialogs_bottom_sheet")).font(.headline)
                Text(L10n.tr("dialogs_bottom_sheet_body"))
                PrimaryButton(title: L10n.tr("action_close"), id: TestIDs.bottomSheetClose) {
                    result = "bottom_sheet_closed"
                    open = .none
                }
            }
            .padding()
            .presentationDetents([.medium])
            .screenRoot(TestIDs.bottomSheet)
        }
        .navigationTitle(L10n.tr("scenario_dialogs"))
        .screenRoot(TestIDs.dialogsScreen)
    }

    private func binding(_ surface: Surface) -> Binding<Bool> {
        Binding(get: { open == surface }, set: { if !$0 { open = .none } })
    }
}

// MARK: - Permissions

/// Maps each system API's authorization status onto the four playground states. iOS asks only once;
/// after a denial the status is "denied" and only Settings can change it, so denied is reported as
/// permanently denied (see docs/automation.md).
enum PermissionKind: String, CaseIterable, Identifiable {
    case camera, photos, notifications, location
    var id: String { rawValue }
}

@Observable
final class PermissionsModel: NSObject, CLLocationManagerDelegate {
    private(set) var statuses: [PermissionKind: PermissionStatus] = [:]
    private let locationManager = CLLocationManager()

    override init() {
        super.init()
        locationManager.delegate = self
    }

    @MainActor
    func refresh() async {
        statuses[.camera] = Self.map(AVCaptureDevice.authorizationStatus(for: .video))
        statuses[.photos] = Self.map(PHPhotoLibrary.authorizationStatus(for: .readWrite))
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        statuses[.notifications] = Self.map(settings.authorizationStatus)
        statuses[.location] = Self.map(locationManager.authorizationStatus)
    }

    @MainActor
    func request(_ kind: PermissionKind) async {
        switch kind {
        case .camera: _ = await AVCaptureDevice.requestAccess(for: .video)
        case .photos: _ = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        case .notifications: _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
        case .location: locationManager.requestWhenInUseAuthorization()
        }
        await refresh()
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in await refresh() }
    }

    static func map(_ status: AVAuthorizationStatus) -> PermissionStatus {
        switch status {
        case .authorized: return .granted
        case .notDetermined: return .notRequested
        default: return .permanentlyDenied
        }
    }

    static func map(_ status: PHAuthorizationStatus) -> PermissionStatus {
        switch status {
        case .authorized, .limited: return .granted
        case .notDetermined: return .notRequested
        default: return .permanentlyDenied
        }
    }

    static func map(_ status: UNAuthorizationStatus) -> PermissionStatus {
        switch status {
        case .authorized, .provisional, .ephemeral: return .granted
        case .notDetermined: return .notRequested
        default: return .permanentlyDenied
        }
    }

    static func map(_ status: CLAuthorizationStatus) -> PermissionStatus {
        switch status {
        case .authorizedAlways, .authorizedWhenInUse: return .granted
        case .notDetermined: return .notRequested
        default: return .permanentlyDenied
        }
    }
}

extension PermissionStatus {
    var labelKey: String {
        switch self {
        case .notRequested: return "permission_status_not_requested"
        case .granted: return "permission_status_granted"
        case .denied: return "permission_status_denied"
        case .permanentlyDenied: return "permission_status_permanently_denied"
        }
    }
}

struct PermissionsView: View {
    @State private var model = PermissionsModel()
    @Environment(\.openURL) private var openURL

    var body: some View {
        Form {
            Text(L10n.tr("permissions_intro"))
            ForEach(PermissionKind.allCases) { kind in
                let status = model.statuses[kind] ?? .notRequested
                Section(L10n.tr("permission_\(kind.rawValue)")) {
                    Text(L10n.tr(status.labelKey)).accessibilityIdentifier(TestIDs.permissionStatus(kind.rawValue))
                    Button(L10n.tr(status == .notRequested ? "permissions_request" : "permissions_request_again")) {
                        Task { await model.request(kind) }
                    }
                    .disabled(status == .granted)
                    .accessibilityIdentifier(TestIDs.permissionRequest(kind.rawValue))
                    if status == .permanentlyDenied { Text(L10n.tr("permissions_permanent_hint")).font(.footnote) }
                }
            }
            Button(L10n.tr("permissions_open_settings")) {
                if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
            }
            .accessibilityIdentifier(TestIDs.permissionOpenSettings)
        }
        .navigationTitle(L10n.tr("scenario_permissions"))
        .task { await model.refresh() }
        .screenRoot(TestIDs.permissionsScreen)
    }
}

// MARK: - WebView

/// Hosts the shared local page (shared/web/playground.html). WKWebView has no SwiftUI equivalent,
/// so it is wrapped with UIViewRepresentable; everything around it is SwiftUI.
struct PlaygroundWebView: UIViewRepresentable {
    @Binding var message: String?
    let controller: WebViewController

    func makeCoordinator() -> Coordinator { Coordinator(message: $message) }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.userContentController.add(context.coordinator, name: "bridge")
        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        webView.accessibilityIdentifier = TestIDs.webview
        #if DEBUG
        if #available(iOS 16.4, *) { webView.isInspectable = true }
        #endif
        controller.webView = webView
        if let url = Bundle.main.url(forResource: "playground", withExtension: "html") {
            webView.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
        }
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKScriptMessageHandler, WKNavigationDelegate {
        @Binding var message: String?
        init(message: Binding<String?>) { _message = message }

        func userContentController(_ controller: WKUserContentController, didReceive scriptMessage: WKScriptMessage) {
            message = scriptMessage.body as? String
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            let language = Locale.current.language.languageCode?.identifier ?? "en"
            webView.evaluateJavaScript("setLanguage('\(language)')")
        }
    }
}

final class WebViewController {
    weak var webView: WKWebView?

    func send(_ text: String) {
        let escaped = text.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: "'", with: "\\'")
        webView?.evaluateJavaScript("receiveFromNative('\(escaped)')")
    }

    func reload() { webView?.reload() }
}

struct WebViewScreen: View {
    @State private var message: String?
    @State private var controller = WebViewController()

    var body: some View {
        VStack(spacing: 8) {
            ValueRow(label: L10n.tr("webview_message"), value: message ?? L10n.tr("value_none"), valueID: TestIDs.webviewMessage)
                .padding(.horizontal)
            HStack {
                Button(L10n.tr("webview_send")) { controller.send(L10n.tr("webview_native_greeting")) }
                    .accessibilityIdentifier(TestIDs.webviewSendToWeb)
                Button(L10n.tr("webview_reload")) { controller.reload() }.accessibilityIdentifier(TestIDs.webviewReload)
            }
            .buttonStyle(.bordered)
            PlaygroundWebView(message: $message, controller: controller)
        }
        .navigationTitle(L10n.tr("scenario_webview"))
        .screenRoot(TestIDs.webviewScreen)
    }
}

// MARK: - Network

@Observable
final class NetworkModel {
    enum State: Equatable { case idle, loading, success([Product]), empty, error(NetworkError) }

    private(set) var state = State.idle
    private var lastScenario: NetworkScenario?

    @MainActor
    func load(_ scenario: NetworkScenario, client: NetworkClient) async {
        lastScenario = scenario
        state = .loading
        switch await client.fetchProducts(scenario) {
        case .success(let products): state = .success(products)
        case .empty: state = .empty
        case .failure(let error): state = .error(error)
        }
    }

    @MainActor
    func retry(client: NetworkClient) async {
        if let lastScenario { await load(lastScenario, client: client) }
    }
}

extension NetworkError {
    var message: String {
        switch self {
        case .timeout: return L10n.tr("network_error_timeout")
        case .badRequest: return L10n.tr("network_error_400")
        case .unauthorized: return L10n.tr("network_error_401")
        case .forbidden: return L10n.tr("network_error_403")
        case .notFound: return L10n.tr("network_error_404")
        case .server(let code): return L10n.tr("network_error_server", code)
        case .malformedResponse: return L10n.tr("network_error_malformed")
        case .connectivity: return L10n.tr("network_error_connectivity")
        case .unexpectedStatus(let code): return L10n.tr("network_error_unexpected", code)
        }
    }
}

struct NetworkView: View {
    @Environment(AppEnvironment.self) private var env
    @State private var model = NetworkModel()

    private func labelKey(_ scenario: NetworkScenario) -> String {
        switch scenario {
        case .badRequest: return "network_scenario_400"
        case .unauthorized: return "network_scenario_401"
        case .forbidden: return "network_scenario_403"
        case .notFound: return "network_scenario_404"
        case .serverError: return "network_scenario_500"
        default: return "network_scenario_\(scenario.rawValue)"
        }
    }

    var body: some View {
        Form {
            Text(L10n.tr("network_intro"))
            Section {
                ForEach(NetworkScenario.allCases) { scenario in
                    Button(L10n.tr(labelKey(scenario))) { Task { await model.load(scenario, client: env.network) } }
                        .disabled(model.state == .loading)
                        .accessibilityIdentifier(TestIDs.networkScenario(scenario.rawValue))
                }
            }
            Section {
                switch model.state {
                case .idle: Text(L10n.tr("network_idle")).accessibilityIdentifier(TestIDs.networkIdle)
                case .loading: ProgressView().accessibilityIdentifier(TestIDs.networkLoading)
                case .empty: Text(L10n.tr("network_empty")).accessibilityIdentifier(TestIDs.networkEmpty)
                case .success(let products):
                    Text(L10n.tr("network_success", products.count))
                        .accessibilityIdentifier(TestIDs.networkSuccess)
                    ForEach(products) { product in
                        ValueRow(label: product.name, value: PriceFormatter.format(product.priceCents),
                            valueID: TestIDs.networkResultItem(product.id))
                    }
                case .error(let error):
                    VStack(alignment: .leading, spacing: 8) {
                        ErrorLabel(text: error.message, id: TestIDs.networkErrorMessage)
                        Button(L10n.tr("action_retry")) { Task { await model.retry(client: env.network) } }
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier(TestIDs.networkRetry)
                    }
                    .accessibilityElement(children: .contain)
                    .accessibilityIdentifier(TestIDs.networkError)
                }
            }
        }
        .navigationTitle(L10n.tr("scenario_network"))
        .screenRoot(TestIDs.networkScreen)
    }
}
