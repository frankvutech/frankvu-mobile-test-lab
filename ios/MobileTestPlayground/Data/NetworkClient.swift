import Foundation

/// The network abstraction views and view models depend on.
protocol NetworkClient {
    func fetchProducts(_ scenario: NetworkScenario) async -> NetworkResult<[Product]>
}

/// Delays used by the simulated transport. UI tests launch with `-uiTestFastTiming` to shorten them.
struct NetworkDelays {
    var slow: TimeInterval = 2.0
    var timeout: TimeInterval = 1.0
    var testModeSlow: TimeInterval = 2.0
}

/// Shared, thread-safe configuration read by `ScenarioURLProtocol` on URLSession's queue.
final class ScenarioTransportConfig: @unchecked Sendable {
    static let shared = ScenarioTransportConfig()
    private let lock = NSLock()
    private var settingsValue = TestModeSettings()
    private var delaysValue = NetworkDelays()

    var settings: TestModeSettings {
        get { lock.withLock { settingsValue } }
        set { lock.withLock { settingsValue = newValue } }
    }

    var delays: NetworkDelays {
        get { lock.withLock { delaysValue } }
        set { lock.withLock { delaysValue = newValue } }
    }
}

/// A local transport: every request to the playground host is answered here, never by a socket.
/// The real URLSession + JSONDecoder path runs, so malformed JSON fails exactly as in production.
final class ScenarioURLProtocol: URLProtocol {
    static let host = "playground.invalid"
    private var cancelled = false

    override static func canInit(with request: URLRequest) -> Bool { request.url?.host == host }
    override static func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let config = ScenarioTransportConfig.shared
        let scenarioId = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?
            .queryItems?.first { $0.name == "scenario" }?.value
        let scenario = NetworkScenario(rawValue: scenarioId ?? "") ?? .notFound
        DispatchQueue.global().async { [self] in
            if config.settings.simulateSlowNetwork { Thread.sleep(forTimeInterval: config.delays.testModeSlow) }
            guard !cancelled else { return }
            if config.settings.simulateNetworkError {
                client?.urlProtocol(self, didFailWithError: URLError(.notConnectedToInternet))
                return
            }
            respond(to: scenario, delays: config.delays)
        }
    }

    override func stopLoading() { cancelled = true }

    private func respond(to scenario: NetworkScenario, delays: NetworkDelays) {
        let body: String
        let status: Int
        switch scenario {
        case .success: (status, body) = (200, Self.productsJSON())
        case .slow:
            Thread.sleep(forTimeInterval: delays.slow)
            (status, body) = (200, Self.productsJSON())
        case .timeout:
            Thread.sleep(forTimeInterval: delays.timeout)
            client?.urlProtocol(self, didFailWithError: URLError(.timedOut))
            return
        case .badRequest: (status, body) = (400, #"{"error":"simulated"}"#)
        case .unauthorized: (status, body) = (401, #"{"error":"simulated"}"#)
        case .forbidden: (status, body) = (403, #"{"error":"simulated"}"#)
        case .notFound: (status, body) = (404, #"{"error":"simulated"}"#)
        case .serverError: (status, body) = (500, #"{"error":"simulated"}"#)
        case .empty: (status, body) = (200, "[]")
        case .malformed: (status, body) = (200, #"[{"id": "product_001", "name": "#)
        }
        let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: "HTTP/1.1",
                                       headerFields: ["Content-Type": "application/json"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(body.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }

    private static func productsJSON() -> String {
        let data = try? JSONEncoder().encode(Array(SeedData.products.prefix(5)))
        return data.flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
    }
}

/// URLSession-based client that maps every outcome to a `NetworkResult`.
struct URLSessionNetworkClient: NetworkClient {
    private let session: URLSession

    init() {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [ScenarioURLProtocol.self]
        session = URLSession(configuration: configuration)
    }

    func fetchProducts(_ scenario: NetworkScenario) async -> NetworkResult<[Product]> {
        var components = URLComponents()
        components.scheme = "https"
        components.host = ScenarioURLProtocol.host
        components.path = "/api/products"
        components.queryItems = [URLQueryItem(name: "scenario", value: scenario.rawValue)]
        do {
            let (data, response) = try await session.data(from: components.url!)
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            guard (200..<300).contains(status) else { return .failure(HttpErrorMapper.map(status)) }
            let products = try JSONDecoder().decode([Product].self, from: data)
            return products.isEmpty ? .empty : .success(products)
        } catch is DecodingError {
            return .failure(.malformedResponse)
        } catch let error as URLError where error.code == .timedOut {
            return .failure(.timeout)
        } catch {
            return .failure(.connectivity)
        }
    }
}
