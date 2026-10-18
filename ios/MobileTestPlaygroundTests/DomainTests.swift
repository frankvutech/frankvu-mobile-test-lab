import XCTest
@testable import MobileTestPlayground

/// Mirrors the Android domain tests so both platforms are held to identical rules.
final class AuthTests: XCTestCase {
    private var storage: InMemoryStorage!
    private var auth: LocalAuthRepository!

    override func setUp() {
        storage = InMemoryStorage()
        auth = LocalAuthRepository(storage: storage)
    }

    private func loginError(_ email: String, _ password: String) async -> LoginError? {
        let result = await LoginUseCase(repository: auth)(email: email, password: password, rememberMe: false)
        if case .failure(let error) = result { return error }
        return nil
    }

    func testInputErrorsInFixedOrder() async {
        let empty = await loginError("", "")
        let invalid = await loginError("not-an-email", "")
        let noPassword = await loginError("test@example.com", "")
        XCTAssertEqual(empty, .emptyEmail)
        XCTAssertEqual(invalid, .invalidEmail)
        XCTAssertEqual(noPassword, .emptyPassword)
    }

    func testDirectoryErrors() async {
        let unknown = await loginError("nobody@example.com", "Password123!")
        let wrong = await loginError("test@example.com", "wrong")
        let locked = await loginError("locked@example.com", "Locked123!")
        XCTAssertEqual(unknown, .unknownUser)
        XCTAssertEqual(wrong, .incorrectPassword)
        XCTAssertEqual(locked, .accountLocked)
        XCTAssertNil(auth.session)
    }

    func testSuccessfulLoginCarriesRole() async {
        _ = await LoginUseCase(repository: auth)(email: " admin@example.com ", password: "Admin123!", rememberMe: false)
        XCTAssertEqual(auth.session?.user.role, .admin)
    }

    func testRememberedSessionSurvivesRestart() async {
        _ = await LoginUseCase(repository: auth)(email: "test@example.com", password: "Password123!", rememberMe: true)
        let restarted = LocalAuthRepository(storage: storage)
        restarted.restoreSession()
        XCTAssertEqual(restarted.session?.user.email, "test@example.com")
        XCTAssertEqual(restarted.rememberedEmail, "test@example.com")
    }

    func testNonRememberedSessionDoesNotSurviveRestart() async {
        _ = await LoginUseCase(repository: auth)(email: "test@example.com", password: "Password123!", rememberMe: false)
        let restarted = LocalAuthRepository(storage: storage)
        restarted.restoreSession()
        XCTAssertNil(restarted.session)
        XCTAssertNil(restarted.rememberedEmail)
    }

    func testLogoutKeepsRememberedEmailButClearingStorageRemovesIt() async {
        _ = await LoginUseCase(repository: auth)(email: "qa@example.com", password: "QaPassword123!", rememberMe: true)
        await auth.endSession()
        XCTAssertEqual(LocalAuthRepository(storage: storage).rememberedEmail, "qa@example.com")
        storage.clear()
        XCTAssertNil(LocalAuthRepository(storage: storage).rememberedEmail)
    }

    func testRegisterAndResetPassword() async {
        let register = RegisterUseCase(repository: auth)
        let taken = await register(name: "Ann", email: "test@example.com", password: "Passw0rd!", confirm: "Passw0rd!")
        if case .failure(let error) = taken { XCTAssertEqual(error, .emailAlreadyRegistered) } else { XCTFail("expected failure") }
        let created = await register(name: "Ann", email: "ann@example.com", password: "Passw0rd!", confirm: "Passw0rd!")
        XCTAssertNoThrow(try created.get())

        let reset = ResetPasswordUseCase(repository: auth)
        let badCode = await reset(email: "ann@example.com", code: "000000", password: "NewPass1!", confirm: "NewPass1!")
        if case .failure(let error) = badCode { XCTAssertEqual(error, .invalidCode) } else { XCTFail("expected failure") }
        let done = await reset(email: "ann@example.com", code: "123456", password: "NewPass1!", confirm: "NewPass1!")
        XCTAssertNoThrow(try done.get())
        let login = await loginError("ann@example.com", "NewPass1!")
        XCTAssertNil(login)
    }

    func testPasswordPolicyAndSeedPasswords() {
        XCTAssertEqual(PasswordPolicy.firstViolation("Ab1!"), .tooShort)
        XCTAssertEqual(PasswordPolicy.firstViolation("password1!"), .missingUppercase)
        XCTAssertEqual(PasswordPolicy.firstViolation("Password!"), .missingDigit)
        XCTAssertEqual(PasswordPolicy.firstViolation("Password1"), .missingSpecial)
        SeedData.accounts.forEach { XCTAssertNil(PasswordPolicy.firstViolation($0.password), $0.user.email) }
    }
}

final class RulesTests: XCTestCase {
    func testAccessPolicy() {
        let customer = Session(user: SeedData.accounts[0].user, rememberMe: false)
        let admin = Session(user: SeedData.accounts[2].user, rememberMe: false)
        XCTAssertEqual(AccessPolicy.decide(.authenticated, session: nil), .requiresLogin)
        XCTAssertEqual(AccessPolicy.decide(.admin, session: customer), .denied)
        XCTAssertEqual(AccessPolicy.decide(.admin, session: admin), .granted)
        XCTAssertEqual(AccessPolicy.decide(.authenticated, session: nil, authenticationRequired: false), .granted)
        XCTAssertEqual(AccessPolicy.decide(.admin, session: nil, authenticationRequired: false), .requiresLogin)
    }

    func testEmailValidator() {
        ["test@example.com", "a.b+c@sub.domain.org"].forEach { XCTAssertTrue(EmailValidator.isValid($0), $0) }
        ["", "plain", "a@b", "a@b.c", "a b@c.de", "@example.com"].forEach { XCTAssertFalse(EmailValidator.isValid($0), $0) }
    }

    func testFormValidator() {
        let empty = FormValidator.validate(FormInput())
        XCTAssertEqual(empty[.fullName], .required)
        XCTAssertEqual(empty[.terms], .mustAccept)
        XCTAssertNil(empty[.comments])
        var input = FormInput(fullName: "Ada", email: "ada@example.com", password: "password1", age: "36",
                              country: "de", plan: "pro", acceptTerms: true, date: "2025-01-15")
        XCTAssertTrue(FormValidator.validate(input).isEmpty)
        input.age = "17"
        XCTAssertEqual(FormValidator.validate(input)[.age], .outOfRange)
        input.age = "abc"
        XCTAssertEqual(FormValidator.validate(input)[.age], .notANumber)
        input.fullName = String(repeating: "a", count: 41)
        XCTAssertEqual(FormValidator.validate(input)[.fullName], .tooLong)
    }

    func testDeepLinkParserMatchesAndroid() {
        XCTAssertEqual(DeepLinkParser.parse("mtp://playground"), .home)
        XCTAssertEqual(DeepLinkParser.parse("mtp://playground/scenario/forms"), .scenario("forms"))
        XCTAssertEqual(DeepLinkParser.parse("mtp://playground/product/product_003?ref=x"), .product("product_003"))
        XCTAssertEqual(DeepLinkParser.parse("mtp://playground/detail/3"), .detail(3))
        XCTAssertEqual(DeepLinkParser.parse("mtp://playground/test-mode/"), .testMode)
        ["https://playground/home", "mtp://other/home", "mtp://playground/unknown", "mtp://playground/detail/0",
         "mtp://playground/scenario/../admin", "mtp://playground/a/b/c"].forEach { XCTAssertNil(DeepLinkParser.parse($0), $0) }
    }

    func testListsAndPaging() {
        let items = SeedData.dynamicItems + [DynamicItem(id: 6, title: "apricot")]
        XCTAssertEqual(DynamicListQuery.apply(items, query: "AP", sort: .byId).map(\.title), ["Apple", "apricot"])
        XCTAssertEqual(DynamicListQuery.apply(items, query: "ap", sort: .titleDescending).map(\.title), ["apricot", "Apple"])
        XCTAssertEqual(PagedItems.page(0).items.first?.title, "Row 001")
        XCTAssertFalse(PagedItems.page(4).hasMore)
        XCTAssertEqual(PagedItems.page(4).items.last?.title, "Row 100")
    }

    func testMiscRules() {
        XCTAssertEqual(HttpErrorMapper.map(404), .notFound)
        XCTAssertEqual(HttpErrorMapper.map(503), .server(503))
        XCTAssertEqual(ZoomRules.label(9), "4.0x")
        XCTAssertEqual(SwipeClassifier.classify(dx: -200, dy: 30, threshold: 50), .swipeLeft)
        XCTAssertEqual(DateMath.daysFromReference("2025-02-01"), 17)
        XCTAssertEqual(PriceFormatter.format(1249), "12.49")
        XCTAssertEqual(SeedData.products[2].name, "Product 003")
        XCTAssertEqual(SeedData.products[0].priceCents, 749)
    }
}

final class DataTests: XCTestCase {
    @MainActor
    func testOfflineCartCheckoutIsDeterministic() async {
        let cart = CartRepository()
        if case .failure(let reason) = await cart.checkout() { XCTAssertEqual(reason, .conflict) } else { XCTFail("empty cart") }
        await cart.add(SeedData.products[0])
        await cart.add(SeedData.products[0])
        await cart.add(SeedData.products[1])
        XCTAssertEqual(cart.count, 3)
        let order = try? await cart.checkout().get()
        XCTAssertEqual(order?.0, "ORDER-0001")
        XCTAssertEqual(order?.1, 2497)
        XCTAssertTrue(cart.lines.isEmpty)
    }

    func testDynamicItemsPersistAndReset() {
        let storage = InMemoryStorage()
        let items = DynamicItemsRepository(storage: storage)
        XCTAssertEqual(items.add("Fig")?.id, 6)
        items.delete(id: 1)
        XCTAssertEqual(DynamicItemsRepository(storage: storage).items.map(\.id), [2, 3, 4, 5, 6])
        items.generate()
        XCTAssertEqual(items.items.count, 15)
        XCTAssertEqual(items.items.last?.title, "Generated 10")
    }

    func testResetApplicationState() async {
        let environment = AppEnvironment(storage: InMemoryStorage(), arguments: ["-offlineMode"])
        _ = await environment.login(email: "test@example.com", password: "Password123!", rememberMe: true)
        environment.updateTestMode { $0.simulateNetworkError = true }
        environment.dynamicItems.add("Extra")
        environment.resetApplicationState()
        XCTAssertNil(environment.auth.session)
        XCTAssertFalse(environment.testMode.settings.simulateNetworkError)
        XCTAssertEqual(environment.dynamicItems.items, SeedData.dynamicItems)
    }

    func testOfflineModeArgumentAndBackendDefault() {
        let backend = AppEnvironment(storage: InMemoryStorage(), arguments: [],
                                     backendConfig: BackendConfig(defaultBaseURL: "http://localhost:8080", useBackendByDefault: true))
        XCTAssertTrue(backend.testMode.settings.useBackend)
        let offline = AppEnvironment(storage: InMemoryStorage(), arguments: ["-offlineMode"])
        XCTAssertFalse(offline.testMode.settings.useBackend)
        let custom = AppEnvironment(storage: InMemoryStorage(), arguments: ["-backendURL", "http://192.168.1.20:8080/"])
        XCTAssertEqual(custom.backend.baseURL, "http://192.168.1.20:8080")
    }
}

/// Serves scripted responses in the backend's real JSON shapes (see backend/.../openapi.yaml).
final class MockBackendProtocol: URLProtocol {
    static var handler: ((URLRequest) -> (Int, String))?
    static var requests: [URLRequest] = []

    override static func canInit(with request: URLRequest) -> Bool { true }
    override static func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.requests.append(request)
        let (status, body) = Self.handler?(request) ?? (500, "{}")
        if status == 0 {
            client?.urlProtocol(self, didFailWithError: URLError(.cannotConnectToHost))
            return
        }
        let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(body.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

/// Response bodies in the backend's documented shapes.
enum Fixtures {
    static let adminLogin = #"{"token":"tkn-1","tokenType":"Bearer","expiresAt":1,"user":"#
        + #"{"id":"user_admin","email":"admin@example.com","displayName":"Admin User","role":"ADMIN"}}"#
    static let cart = #"{"items":[{"product":{"id":"product_001","name":"Product 001","description":"d","#
        + #""category":"Books","priceCents":749,"stock":10},"quantity":2,"lineTotalCents":1498}],"totalQuantity":2,"totalCents":1498}"#
    static let productPage = #"{"items":[{"id":"product_003","name":"Product 003","description":"d","category":"Garden","#
        + #""priceCents":1249,"stock":10}],"page":1,"size":50,"totalItems":1,"totalPages":1}"#
}

final class BackendDataSourceTests: XCTestCase {
    private var client: BackendAPIClient!

    override func setUp() {
        MockBackendProtocol.requests = []
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockBackendProtocol.self]
        client = BackendAPIClient(config: BackendConfig(defaultBaseURL: "http://backend.test", useBackendByDefault: true),
                                  settings: { TestModeSettings() }, session: URLSession(configuration: configuration))
    }

    private func error(_ code: String) -> String { #"{"error":{"code":"\#(code)","message":"m","details":[]}}"# }

    func testLoginMapsBackendErrorCodes() async {
        let auth = LocalAuthRepository(storage: InMemoryStorage(), remote: RemoteAuthDataSource(client: client), useBackend: { true })
        let login = LoginUseCase(repository: auth)
        func attempt() async -> LoginError? {
            if case .failure(let error) = await login(email: "x@example.com", password: "p", rememberMe: false) { return error }
            return nil
        }
        MockBackendProtocol.handler = { _ in (401, self.error("UNKNOWN_USER")) }
        let unknown = await attempt()
        XCTAssertEqual(unknown, .unknownUser)
        MockBackendProtocol.handler = { _ in (403, self.error("ACCOUNT_LOCKED")) }
        let locked = await attempt()
        XCTAssertEqual(locked, .accountLocked)
        MockBackendProtocol.handler = { _ in (0, "") }
        let unavailable = await attempt()
        XCTAssertEqual(unavailable, .serviceUnavailable)
    }

    func testSuccessfulLoginStoresTheTokenAndLogoutSendsIt() async {
        let storage = InMemoryStorage()
        let auth = LocalAuthRepository(storage: storage, remote: RemoteAuthDataSource(client: client), useBackend: { true })
        MockBackendProtocol.handler = { request in
            request.url!.path.hasSuffix("/logout") ? (204, "") :
                (200, Fixtures.adminLogin)
        }
        _ = await LoginUseCase(repository: auth)(email: "admin@example.com", password: "Admin123!", rememberMe: true)
        XCTAssertEqual(auth.session?.user.role, .admin)
        XCTAssertEqual(auth.token, "tkn-1")
        XCTAssertEqual(storage.string(forKey: StorageKeys.sessionToken), "tkn-1")
        await auth.endSession()
        XCTAssertEqual(MockBackendProtocol.requests.last?.value(forHTTPHeaderField: "Authorization"), "Bearer tkn-1")
        XCTAssertNil(auth.token)
    }

    @MainActor
    func testCartAndCheckoutUseTheServer() async {
        let cart = CartRepository(client: client, token: { "tkn-9" }, useBackend: { true })
        let cartJSON = Fixtures.cart
        MockBackendProtocol.handler = { request in
            request.url!.path == "/api/v1/orders" ?
                (201, #"{"id":1,"orderNumber":"ORDER-0001","userId":"u","status":"PLACED","totalCents":1498,"items":[]}"#) : (201, cartJSON)
        }
        let added = await cart.add(SeedData.products[0])
        XCTAssertNil(added)
        XCTAssertEqual(MockBackendProtocol.requests.last?.value(forHTTPHeaderField: "Authorization"), "Bearer tkn-9")
        XCTAssertEqual(cart.lines.first?.quantity, 2)
        let order = try? await cart.checkout().get()
        XCTAssertEqual(order?.0, "ORDER-0001")
        XCTAssertTrue(cart.lines.isEmpty)

        MockBackendProtocol.handler = { _ in (409, self.error("INSUFFICIENT_STOCK")) }
        let soldOut = await cart.add(SeedData.products[19])
        XCTAssertEqual(soldOut, .conflict)
    }

    func testProductsComeFromTheBackend() async {
        MockBackendProtocol.handler = { _ in
            (200, Fixtures.productPage)
        }
        let products = try? await RemoteProductDataSource(client: client).products().get()
        XCTAssertEqual(products, [SeedData.products[2]])
        MockBackendProtocol.handler = { _ in (404, self.error("PRODUCT_NOT_FOUND")) }
        if case .failure(let reason) = await RemoteProductDataSource(client: client).product(id: "x") { XCTAssertEqual(reason, .notFound) }
    }
}

final class NetworkClientTests: XCTestCase {
    override func setUp() {
        ScenarioTransportConfig.shared.delays = NetworkDelays(slow: 0, timeout: 0, testModeSlow: 0)
        ScenarioTransportConfig.shared.settings = TestModeSettings()
    }

    override func tearDown() {
        ScenarioTransportConfig.shared.delays = NetworkDelays()
        ScenarioTransportConfig.shared.settings = TestModeSettings()
    }

    func testEveryScenarioMapsPredictably() async {
        let client = URLSessionNetworkClient()
        let success = await client.fetchProducts(.success)
        if case .success(let products) = success { XCTAssertEqual(products.count, 5) } else { XCTFail("\(success)") }
        let empty = await client.fetchProducts(.empty)
        XCTAssertEqual(empty, .empty)
        let timeout = await client.fetchProducts(.timeout)
        XCTAssertEqual(timeout, .failure(.timeout))
        let unauthorized = await client.fetchProducts(.unauthorized)
        XCTAssertEqual(unauthorized, .failure(.unauthorized))
        let server = await client.fetchProducts(.serverError)
        XCTAssertEqual(server, .failure(.server(500)))
        let malformed = await client.fetchProducts(.malformed)
        XCTAssertEqual(malformed, .failure(.malformedResponse))
    }

    func testTestModeNetworkErrorOverridesScenarios() async {
        ScenarioTransportConfig.shared.settings.simulateNetworkError = true
        let result = await URLSessionNetworkClient().fetchProducts(.success)
        XCTAssertEqual(result, .failure(.connectivity))
    }
}
