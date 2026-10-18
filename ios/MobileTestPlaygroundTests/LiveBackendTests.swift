import XCTest
@testable import MobileTestPlayground

/// Runs the iOS data layer against a real backend. Skipped unless PLAYGROUND_BACKEND_URL is set
/// (in the simulator: TEST_RUNNER_PLAYGROUND_BACKEND_URL), e.g.
/// `PLAYGROUND_BACKEND_URL=http://localhost:8080 make ios-host-test` with the backend running.
final class LiveBackendTests: XCTestCase {
    private var environment: AppEnvironment!

    override func setUp() async throws {
        guard let url = ProcessInfo.processInfo.environment["PLAYGROUND_BACKEND_URL"] else {
            throw XCTSkip("Set PLAYGROUND_BACKEND_URL to run against a live backend")
        }
        environment = AppEnvironment(storage: InMemoryStorage(), arguments: [],
                                     backendConfig: BackendConfig(defaultBaseURL: url, useBackendByDefault: true))
        do {
            try await environment.backend.raw("POST", "/api/v1/test/reset")
        } catch {
            throw XCTSkip("Backend not reachable at \(url): \(error)")
        }
    }

    @MainActor
    func testLoginCatalogCartAndCheckoutAgainstTheBackend() async throws {
        let wrong = await environment.login(email: "test@example.com", password: "wrong", rememberMe: false)
        if case .failure(let error) = wrong { XCTAssertEqual(error, .incorrectPassword) } else { XCTFail("expected failure") }

        let login = await environment.login(email: "test@example.com", password: "Password123!", rememberMe: false)
        XCTAssertEqual(try login.get().role, .customer)
        XCTAssertNotNil(environment.auth.token)

        let products = try await environment.catalog.products().get()
        XCTAssertEqual(products.count, 20)
        XCTAssertEqual(products[2], SeedData.products[2])

        let added = await environment.cart.add(products[0])
        XCTAssertNil(added)
        let soldOut = await environment.cart.add(products[19])
        XCTAssertEqual(soldOut, .conflict)
        let order = try await environment.cart.checkout().get()
        XCTAssertEqual(order.0, "ORDER-0001")
        XCTAssertEqual(order.1, 749)

        await environment.auth.endSession()
        XCTAssertNil(environment.auth.session)
    }
}
