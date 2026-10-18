import Foundation
import Observation

/// Anything "Reset application state" must restore to the seed.
protocol ResettableStore: AnyObject {
    func resetToSeed()
}

/// Offline/test mode: the seed accounts plus anything registered during this launch.
final class LocalAccountDirectory: AuthDataSource {
    private var directory = LocalAccountDirectory.seed()

    func login(email: String, password: String) async -> (CredentialCheck, String?) {
        guard let account = directory[email.lowercased()] else { return (.unknownUser, nil) }
        if account.locked { return (.locked, nil) }
        if account.password != password { return (.incorrectPassword, nil) }
        return (.valid(account.user), nil)
    }

    func register(displayName: String, email: String, password: String) async -> (RegistrationOutcome, String?) {
        let key = email.lowercased()
        if directory[key] != nil { return (.emailTaken, nil) }
        let user = User(id: "user_registered_\(directory.count + 1)", email: key, displayName: displayName, role: .customer)
        directory[key] = SeedData.SeedAccount(user: user, password: password)
        return (.registered(user), nil)
    }

    func resetPassword(email: String, code: String, newPassword: String) async -> PasswordResetOutcome {
        let key = email.lowercased()
        guard directory[key] != nil else { return .unknownUser }
        guard code == SeedData.passwordResetCode else { return .invalidCode }
        directory[key]?.password = newPassword
        return .done
    }

    func logout(token: String?) async {}

    func reset() { directory = Self.seed() }

    private static func seed() -> [String: SeedData.SeedAccount] {
        Dictionary(uniqueKeysWithValues: SeedData.accounts.map { ($0.user.email.lowercased(), $0) })
    }
}

/// The session, with the same Remember Me rules as Android in both modes: remembered sessions
/// (and the backend token) are persisted; others live in memory; logout keeps the remembered email;
/// clearing storage removes everything.
@Observable
final class LocalAuthRepository: AuthRepository, ResettableStore {
    private let storage: KeyValueStorage
    private let local: LocalAccountDirectory
    private let remote: AuthDataSource
    private let useBackend: () -> Bool
    private var pendingToken: String?
    private(set) var session: Session?
    /// The bearer token for backend calls; nil in offline mode or when signed out.
    private(set) var token: String?

    init(storage: KeyValueStorage, local: LocalAccountDirectory = LocalAccountDirectory(),
         remote: AuthDataSource? = nil, useBackend: @escaping () -> Bool = { false }) {
        self.storage = storage
        self.local = local
        self.remote = remote ?? local
        self.useBackend = useBackend
    }

    private var source: AuthDataSource { useBackend() ? remote : local }

    /// Loads a remembered session; called once at startup.
    func restoreSession() {
        if session == nil, let user = storage.decode(User.self, forKey: StorageKeys.rememberedUser) {
            token = storage.string(forKey: StorageKeys.sessionToken)
            session = Session(user: user, rememberMe: true)
        }
    }

    func checkCredentials(email: String, password: String) async -> CredentialCheck {
        let (check, issued) = await source.login(email: email, password: password)
        pendingToken = issued
        return check
    }

    func startSession(user: User, rememberMe: Bool) {
        token = pendingToken
        pendingToken = nil
        storage.encode(rememberMe ? user : nil, forKey: StorageKeys.rememberedUser)
        storage.set(rememberMe ? token : nil, forKey: StorageKeys.sessionToken)
        storage.set(rememberMe ? user.email : nil, forKey: StorageKeys.rememberedEmail)
        session = Session(user: user, rememberMe: rememberMe)
    }

    func endSession() async {
        await source.logout(token: token)
        clearSession()
    }

    func register(displayName: String, email: String, password: String) async -> RegistrationOutcome {
        await source.register(displayName: displayName, email: email, password: password).0
    }

    func resetPassword(email: String, code: String, newPassword: String) async -> PasswordResetOutcome {
        await source.resetPassword(email: email, code: code, newPassword: newPassword)
    }

    var rememberedEmail: String? { storage.string(forKey: StorageKeys.rememberedEmail) }

    func resetToSeed() {
        local.reset()
        clearSession()
        storage.set(String?.none, forKey: StorageKeys.rememberedEmail)
    }

    private func clearSession() {
        token = nil
        storage.encode(User?.none, forKey: StorageKeys.rememberedUser)
        storage.set(String?.none, forKey: StorageKeys.sessionToken)
        session = nil
    }
}

@Observable
final class TestModeRepository: ResettableStore {
    private let storage: KeyValueStorage
    private let defaults: TestModeSettings
    private(set) var settings: TestModeSettings

    /// `defaults` are the fresh-install values; `useBackend` comes from the build (BackendConfig).
    init(storage: KeyValueStorage, defaults: TestModeSettings = TestModeSettings()) {
        self.storage = storage
        self.defaults = defaults
        settings = storage.decode(TestModeSettings.self, forKey: StorageKeys.testMode) ?? defaults
    }

    func update(_ transform: (inout TestModeSettings) -> Void) {
        transform(&settings)
        storage.encode(settings, forKey: StorageKeys.testMode)
    }

    func resetToSeed() {
        settings = defaults
        storage.encode(settings, forKey: StorageKeys.testMode)
    }
}

@Observable
final class PreferencesRepository: ResettableStore {
    private let storage: KeyValueStorage
    private(set) var theme: ThemeMode

    init(storage: KeyValueStorage) {
        self.storage = storage
        theme = storage.string(forKey: StorageKeys.theme).flatMap(ThemeMode.init(rawValue:)) ?? .system
    }

    func setTheme(_ mode: ThemeMode) {
        theme = mode
        storage.set(mode.rawValue, forKey: StorageKeys.theme)
    }

    func resetToSeed() { setTheme(.system) }
}

/// Dynamic list items persisted as JSON, so edits survive restart until reset. New id = max + 1.
@Observable
final class DynamicItemsRepository: ResettableStore {
    private let storage: KeyValueStorage
    private(set) var items: [DynamicItem]

    init(storage: KeyValueStorage) {
        self.storage = storage
        items = storage.decode([DynamicItem].self, forKey: StorageKeys.dynamicItems) ?? SeedData.dynamicItems
    }

    @discardableResult
    func add(_ title: String) -> DynamicItem? {
        let trimmed = title.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return nil }
        let item = DynamicItem(id: (items.map(\.id).max() ?? 0) + 1, title: trimmed)
        items.append(item)
        save()
        return item
    }

    func rename(id: Int, title: String) {
        let trimmed = title.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty, let index = items.firstIndex(where: { $0.id == id }) else { return }
        items[index].title = trimmed
        save()
    }

    func delete(id: Int) {
        items.removeAll { $0.id == id }
        save()
    }

    func reset() {
        items = SeedData.dynamicItems
        save()
    }

    /// Seed plus "Generated 01" … "Generated 10", identical on every run.
    func generate() {
        reset()
        for number in 1...10 { add(String(format: "Generated %02d", number)) }
    }

    func resetToSeed() { reset() }

    private func save() { storage.encode(items, forKey: StorageKeys.dynamicItems) }
}

/// The signed-in user's cart. Offline mode keeps it in memory (empty after restart); backend mode
/// mirrors the server cart (`/api/v1/cart`). Every operation reports failures as `FailureReason`.
@Observable
final class CartRepository: ResettableStore {
    private(set) var lines: [CartLine] = []
    private var orderCounter = 0
    private let client: BackendAPIClient?
    private let token: () -> String?
    private let useBackend: () -> Bool

    init(client: BackendAPIClient? = nil, token: @escaping () -> String? = { nil }, useBackend: @escaping () -> Bool = { false }) {
        self.client = client
        self.token = token
        self.useBackend = useBackend
    }

    var count: Int { lines.reduce(0) { $0 + $1.quantity } }
    var totalCents: Int { lines.reduce(0) { $0 + $1.totalCents } }

    private var remote: BackendAPIClient? { useBackend() ? client : nil }

    @MainActor
    private func server(_ method: String, _ path: String, body: [String: Any]? = nil) async -> FailureReason? {
        guard let client = remote else { return nil }
        guard let token = token() else { return .unauthorized }
        switch await client.outcome({ try await client.send(method, path, body: body, token: token, as: CartJSON.self) }) {
        case .success(let cart):
            lines = cart.lines
            return nil
        case .failure(let reason):
            return reason
        }
    }

    @MainActor @discardableResult
    func refresh() async -> FailureReason? { await server("GET", "/api/v1/cart") }

    @MainActor @discardableResult
    func add(_ product: Product) async -> FailureReason? {
        if remote != nil { return await server("POST", "/api/v1/cart/items", body: ["productId": product.id]) }
        if let index = lines.firstIndex(where: { $0.product.id == product.id }) {
            lines[index].quantity += 1
        } else {
            lines.append(CartLine(product: product, quantity: 1))
        }
        return nil
    }

    @MainActor @discardableResult
    func remove(_ productId: String) async -> FailureReason? {
        if remote != nil { return await server("DELETE", "/api/v1/cart/items/\(productId)") }
        lines.removeAll { $0.product.id == productId }
        return nil
    }

    /// Returns (order number, total) or the failure. Order numbers are ORDER-0001, … after a reset, in both modes.
    @MainActor
    func checkout() async -> Result<(String, Int), FailureReason> {
        guard !lines.isEmpty else { return .failure(.conflict) }
        if let client = remote {
            guard let token = token() else { return .failure(.unauthorized) }
            let result = await client.outcome { try await client.send("POST", "/api/v1/orders", token: token, as: OrderJSON.self) }
            if case .success = result { lines = [] }
            return result.map { ($0.orderNumber, $0.totalCents) }
        }
        let total = totalCents
        lines = []
        orderCounter += 1
        return .success((String(format: "ORDER-%04d", orderCounter), total))
    }

    func resetToSeed() {
        lines = []
        orderCounter = 0
    }
}
