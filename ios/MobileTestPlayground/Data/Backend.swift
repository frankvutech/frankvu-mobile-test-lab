import Foundation

/// Build-time backend settings, read from Info.plist (`API_BASE_URL`, `USE_BACKEND_BY_DEFAULT`).
///
/// The simulator shares the Mac's network, so `http://localhost:8080` reaches a backend running on
/// the host. A physical device needs the Mac's LAN address, set at runtime in Test Mode.
struct BackendConfig {
    var defaultBaseURL: String
    var useBackendByDefault: Bool

    static func fromBundle(_ bundle: Bundle = .main) -> BackendConfig {
        let url = bundle.object(forInfoDictionaryKey: "API_BASE_URL") as? String
        let useBackend = bundle.object(forInfoDictionaryKey: "USE_BACKEND_BY_DEFAULT") as? Bool
        return BackendConfig(defaultBaseURL: url ?? "http://localhost:8080", useBackendByDefault: useBackend ?? true)
    }
}

// MARK: - Wire format (backend/src/main/resources/openapi/openapi.yaml)

struct UserJSON: Decodable {
    let id: String, email: String, displayName: String, role: String
    var domain: User { User(id: id, email: email, displayName: displayName, role: role == "ADMIN" ? .admin : .customer) }
}

struct AuthJSON: Decodable {
    let token: String
    let user: UserJSON
}

struct ProductJSON: Decodable {
    let id: String, name: String, category: String, priceCents: Int
    var domain: Product { Product(id: id, name: name, priceCents: priceCents, category: category) }
}

struct ProductPageJSON: Decodable { let items: [ProductJSON] }

struct CartJSON: Decodable {
    struct Line: Decodable {
        let product: ProductJSON
        let quantity: Int
    }

    let items: [Line]
    var lines: [CartLine] { items.map { CartLine(product: $0.product.domain, quantity: $0.quantity) } }
}

struct OrderJSON: Decodable {
    let orderNumber: String
    let totalCents: Int
}

struct ErrorJSON: Decodable {
    struct Body: Decodable { let code: String }
    let error: Body
}

/// An HTTP failure with the backend's stable error `code`.
struct BackendHTTPError: Error {
    let status: Int
    let code: String?

    var reason: FailureReason {
        switch status {
        case 401: return .unauthorized
        case 404: return .notFound
        case 409: return .conflict
        default: return .unknown
        }
    }
}

/// A small URLSession client for the backend. Test Mode's slow/error switches apply here too.
final class BackendAPIClient {
    private let config: BackendConfig
    private let settings: () -> TestModeSettings
    private let session: URLSession

    init(config: BackendConfig, settings: @escaping () -> TestModeSettings, session: URLSession = .shared) {
        self.config = config
        self.settings = settings
        self.session = session
    }

    var baseURL: String {
        let override = settings().backendURLOverride?.trimmingCharacters(in: .whitespaces)
        let url = (override?.isEmpty == false ? override! : config.defaultBaseURL)
        return url.hasSuffix("/") ? String(url.dropLast()) : url
    }

    func send<T: Decodable>(_ method: String, _ path: String, body: [String: Any]? = nil, token: String? = nil,
                            as type: T.Type) async throws -> T {
        let data = try await raw(method, path, body: body, token: token)
        return try JSONDecoder().decode(type, from: data)
    }

    @discardableResult
    func raw(_ method: String, _ path: String, body: [String: Any]? = nil, token: String? = nil) async throws -> Data {
        let current = settings()
        if current.simulateSlowNetwork { try await Task.sleep(nanoseconds: 2_000_000_000) }
        if current.simulateNetworkError { throw URLError(.notConnectedToInternet) }
        guard let url = URL(string: baseURL + path) else { throw URLError(.badURL) }
        var request = URLRequest(url: url, timeoutInterval: 10)
        request.httpMethod = method
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        let (data, response) = try await session.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            throw BackendHTTPError(status: status, code: (try? JSONDecoder().decode(ErrorJSON.self, from: data))?.error.code)
        }
        return data
    }

    /// Runs a call and maps every failure to a `FailureReason`.
    func outcome<T>(_ block: () async throws -> T) async -> Result<T, FailureReason> {
        do {
            return .success(try await block())
        } catch let error as BackendHTTPError {
            return .failure(error.reason)
        } catch is URLError {
            return .failure(.unavailable)
        } catch {
            return .failure(.unknown)
        }
    }
}

// MARK: - Auth

/// Where credentials are checked: the backend (real implementation) or the local directory (offline mode).
protocol AuthDataSource: AnyObject {
    func login(email: String, password: String) async -> (CredentialCheck, String?)
    func register(displayName: String, email: String, password: String) async -> (RegistrationOutcome, String?)
    func resetPassword(email: String, code: String, newPassword: String) async -> PasswordResetOutcome
    func logout(token: String?) async
}

final class RemoteAuthDataSource: AuthDataSource {
    private let client: BackendAPIClient

    init(client: BackendAPIClient) { self.client = client }

    func login(email: String, password: String) async -> (CredentialCheck, String?) {
        do {
            let auth = try await client.send("POST", "/api/v1/auth/login", body: ["email": email, "password": password], as: AuthJSON.self)
            return (.valid(auth.user.domain), auth.token)
        } catch let error as BackendHTTPError {
            switch error.code {
            case "UNKNOWN_USER": return (.unknownUser, nil)
            case "ACCOUNT_LOCKED": return (.locked, nil)
            default: return (.incorrectPassword, nil)
            }
        } catch {
            return (.unavailable, nil)
        }
    }

    func register(displayName: String, email: String, password: String) async -> (RegistrationOutcome, String?) {
        do {
            let body = ["displayName": displayName, "email": email, "password": password]
            let auth = try await client.send("POST", "/api/v1/auth/register", body: body, as: AuthJSON.self)
            return (.registered(auth.user.domain), auth.token)
        } catch let error as BackendHTTPError where error.status == 409 {
            return (.emailTaken, nil)
        } catch {
            return (.unavailable, nil)
        }
    }

    func resetPassword(email: String, code: String, newPassword: String) async -> PasswordResetOutcome {
        do {
            try await client.raw("POST", "/api/v1/auth/password-reset", body: ["email": email, "code": code, "newPassword": newPassword])
            return .done
        } catch let error as BackendHTTPError {
            switch error.code {
            case "INVALID_RESET_CODE": return .invalidCode
            case "UNKNOWN_USER": return .unknownUser
            default: return .unavailable
            }
        } catch {
            return .unavailable
        }
    }

    func logout(token: String?) async {
        // Best effort: the local session ends whether or not the server could revoke the token.
        guard let token else { return }
        _ = try? await client.raw("POST", "/api/v1/auth/logout", token: token)
    }
}

// MARK: - Catalog

protocol ProductDataSource {
    func products() async -> Result<[Product], FailureReason>
    func product(id: String) async -> Result<Product, FailureReason>
}

struct LocalProductDataSource: ProductDataSource {
    func products() async -> Result<[Product], FailureReason> { .success(SeedData.products) }

    func product(id: String) async -> Result<Product, FailureReason> {
        SeedData.products.first { $0.id == id }.map { .success($0) } ?? .failure(.notFound)
    }
}

struct RemoteProductDataSource: ProductDataSource {
    let client: BackendAPIClient

    func products() async -> Result<[Product], FailureReason> {
        await client.outcome { try await client.send("GET", "/api/v1/products?size=50", as: ProductPageJSON.self).items.map(\.domain) }
    }

    func product(id: String) async -> Result<Product, FailureReason> {
        await client.outcome { try await client.send("GET", "/api/v1/products/\(id)", as: ProductJSON.self).domain }
    }
}

/// Chooses the data source per call, so switching modes in Test Mode takes effect immediately.
struct ProductCatalog: ProductDataSource {
    let local: ProductDataSource
    let remote: ProductDataSource
    let useBackend: () -> Bool

    private var active: ProductDataSource { useBackend() ? remote : local }

    func products() async -> Result<[Product], FailureReason> { await active.products() }
    func product(id: String) async -> Result<Product, FailureReason> { await active.product(id: id) }
}
