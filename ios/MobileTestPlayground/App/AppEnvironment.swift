import Foundation
import Observation

/// Every artificial delay the UI uses. UI tests pass `-uiTestFastTiming` to shorten them.
struct UITiming {
    var login: TimeInterval = 0.6
    var dynamicLoad: TimeInterval = 1.5
    var delayedEnable: TimeInterval = 3.0
    var pageLoad: TimeInterval = 0.7

    static let fast = UITiming(login: 0.3, dynamicLoad: 0.5, delayedEnable: 1.0, pageLoad: 0.2)
}

/// The dependency container: builds repositories once and hands them to view models.
///
/// Authentication, catalog, cart and orders use the backend API unless the explicit offline/test
/// mode is selected (Test Mode, or `USE_BACKEND_BY_DEFAULT` = NO in Info.plist).
///
/// Launch arguments used by XCUITest:
/// - `-uiTestReset`: wipe local storage before anything loads (a clean install, every launch).
/// - `-uiTestFastTiming`: shorter fixed delays.
/// - `-offlineMode`: start in the offline/test mode (local deterministic data, no backend needed).
/// - `-backendURL <url>`: start against a specific backend.
@Observable
final class AppEnvironment {
    let storage: KeyValueStorage
    let auth: LocalAuthRepository
    let testMode: TestModeRepository
    let preferences: PreferencesRepository
    let dynamicItems: DynamicItemsRepository
    let cart: CartRepository
    let catalog: ProductDataSource
    let backend: BackendAPIClient
    let network: NetworkClient
    let timing: UITiming

    /// Test Mode's destructive tools exist only in builds compiled with TEST_MODE (Debug).
    static var testModeEnabled: Bool {
        #if TEST_MODE
        return true
        #else
        return false
        #endif
    }

    init(storage: KeyValueStorage = UserDefaultsStorage(), arguments: [String] = ProcessInfo.processInfo.arguments,
         network: NetworkClient = URLSessionNetworkClient(), backendConfig: BackendConfig = .fromBundle()) {
        if arguments.contains("-uiTestReset") { storage.clear() }
        self.storage = storage
        var defaults = TestModeSettings(useBackend: backendConfig.useBackendByDefault && !arguments.contains("-offlineMode"))
        if let index = arguments.firstIndex(of: "-backendURL"), index + 1 < arguments.count {
            defaults.backendURLOverride = arguments[index + 1]
        }
        let testMode = TestModeRepository(storage: storage, defaults: defaults)
        if arguments.contains("-offlineMode") { testMode.update { $0.useBackend = false } }
        self.testMode = testMode
        let useBackend: () -> Bool = { testMode.settings.useBackend }
        let backend = BackendAPIClient(config: backendConfig, settings: { testMode.settings })
        self.backend = backend
        let auth = LocalAuthRepository(storage: storage, remote: RemoteAuthDataSource(client: backend), useBackend: useBackend)
        self.auth = auth
        catalog = ProductCatalog(local: LocalProductDataSource(), remote: RemoteProductDataSource(client: backend), useBackend: useBackend)
        cart = CartRepository(client: backend, token: { auth.token }, useBackend: useBackend)
        preferences = PreferencesRepository(storage: storage)
        dynamicItems = DynamicItemsRepository(storage: storage)
        self.network = network
        timing = arguments.contains("-uiTestFastTiming") ? .fast : UITiming()
        if arguments.contains("-uiTestFastTiming") {
            ScenarioTransportConfig.shared.delays = NetworkDelays(slow: 0.5, timeout: 0.3, testModeSlow: 0.5)
        }
        auth.restoreSession()
        syncTransport()
    }

    /// Switching between backend and offline mode signs out: a session from one source means nothing to the other.
    @MainActor
    func setUseBackend(_ value: Bool) async {
        updateTestMode { $0.useBackend = value }
        await auth.endSession()
    }

    var resettableStores: [ResettableStore] { [auth, testMode, preferences, dynamicItems, cart] }

    /// Restores the first-launch state without restarting the app.
    func resetApplicationState() {
        resettableStores.forEach { $0.resetToSeed() }
        syncTransport()
    }

    /// "Clear local storage": wipe persistence, then bring in-memory state in line with it.
    func clearLocalStorage() {
        resetApplicationState()
        storage.clear()
    }

    func updateTestMode(_ transform: (inout TestModeSettings) -> Void) {
        testMode.update(transform)
        syncTransport()
    }

    private func syncTransport() {
        ScenarioTransportConfig.shared.settings = testMode.settings
    }

    var login: LoginUseCase { LoginUseCase(repository: auth) }
    var register: RegisterUseCase { RegisterUseCase(repository: auth) }
    var resetPassword: ResetPasswordUseCase { ResetPasswordUseCase(repository: auth) }
}
