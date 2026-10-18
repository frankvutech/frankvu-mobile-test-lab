import Foundation

// MARK: - Authorization

enum AccessLevel { case publicAccess, authenticated, admin }

enum AccessDecision: Equatable { case granted, requiresLogin, denied }

/// The single authorization rule, shared by navigation and deep links. Mirrors Android's AccessPolicy.
enum AccessPolicy {
    static func decide(_ level: AccessLevel, session: Session?, authenticationRequired: Bool = true) -> AccessDecision {
        switch level {
        case .publicAccess: return .granted
        case .authenticated: return (session != nil || !authenticationRequired) ? .granted : .requiresLogin
        case .admin:
            guard let session else { return .requiresLogin }
            return session.user.role == .admin ? .granted : .denied
        }
    }
}

// MARK: - Forms

enum FormField: CaseIterable { case fullName, email, password, age, country, plan, date, comments, terms }

enum FieldError: Equatable { case required, invalidEmail, tooShort, tooLong, notANumber, outOfRange, mustAccept }

struct FormInput: Equatable {
    var fullName = ""
    var email = ""
    var password = ""
    var age = ""
    var country: String?
    var plan: String?
    var acceptTerms = false
    var newsletter = false
    var satisfaction = 5
    var date: String?
    var time: String?
    var comments = ""
}

enum FormValidator {
    static let nameMin = 2, nameMax = 40, passwordMin = 8, passwordMax = 20, ageMin = 18, ageMax = 120, commentsMax = 200

    static func validate(_ input: FormInput) -> [FormField: FieldError] {
        var errors: [FormField: FieldError] = [:]
        errors[.fullName] = lengthError(input.fullName.trimmingCharacters(in: .whitespaces), nameMin, nameMax)
        if input.email.trimmingCharacters(in: .whitespaces).isEmpty {
            errors[.email] = .required
        } else if !EmailValidator.isValid(input.email) {
            errors[.email] = .invalidEmail
        }
        errors[.password] = lengthError(input.password, passwordMin, passwordMax)
        errors[.age] = ageError(input.age)
        if (input.country ?? "").isEmpty { errors[.country] = .required }
        if (input.plan ?? "").isEmpty { errors[.plan] = .required }
        if (input.date ?? "").isEmpty { errors[.date] = .required }
        if input.comments.count > commentsMax { errors[.comments] = .tooLong }
        if !input.acceptTerms { errors[.terms] = .mustAccept }
        return errors
    }

    private static func lengthError(_ value: String, _ min: Int, _ max: Int) -> FieldError? {
        if value.isEmpty { return .required }
        if value.count < min { return .tooShort }
        if value.count > max { return .tooLong }
        return nil
    }

    private static func ageError(_ value: String) -> FieldError? {
        let trimmed = value.trimmingCharacters(in: .whitespaces)
        if trimmed.isEmpty { return .required }
        guard let number = Int(trimmed) else { return .notANumber }
        return (ageMin...ageMax).contains(number) ? nil : .outOfRange
    }
}

// MARK: - Deep links

enum DeepLink: Equatable {
    case home, cart, profile, admin, testMode
    case scenario(String), product(String), detail(Int)
}

/// Parses `mtp://playground/...` links with exactly the rules of Android's DeepLinkParser.
enum DeepLinkParser {
    static func parse(_ link: String) -> DeepLink? {
        let prefix = "mtp://playground"
        guard link.lowercased().hasPrefix(prefix) else { return nil }
        var path = String(link.dropFirst(prefix.count))
        if let cut = path.firstIndex(where: { $0 == "?" || $0 == "#" }) { path = String(path[..<cut]) }
        let segments = path.split(separator: "/").map(String.init)
        switch segments.count {
        case 0: return .home
        case 1: return single(segments[0])
        case 2: return withArgument(segments[0], segments[1])
        default: return nil
        }
    }

    private static let singles: [String: DeepLink] = [
        "home": .home, "cart": .cart, "profile": .profile, "admin": .admin, "test-mode": .testMode
    ]

    private static func single(_ segment: String) -> DeepLink? { singles[segment] }

    private static func withArgument(_ segment: String, _ argument: String) -> DeepLink? {
        guard argument.range(of: "^[a-z0-9_]+$", options: .regularExpression) != nil else { return nil }
        switch segment {
        case "scenario": return .scenario(argument)
        case "product": return .product(argument)
        case "detail":
            guard let number = Int(argument), number > 0 else { return nil }
            return .detail(number)
        default: return nil
        }
    }
}

// MARK: - Network

enum NetworkScenario: String, CaseIterable, Identifiable {
    case success, slow, timeout
    case badRequest = "http_400", unauthorized = "http_401", forbidden = "http_403", notFound = "http_404", serverError = "http_500"
    case empty, malformed
    var id: String { rawValue }
}

enum NetworkError: Error, Equatable {
    case timeout, badRequest, unauthorized, forbidden, notFound, server(Int), malformedResponse, connectivity, unexpectedStatus(Int)
}

enum NetworkResult<T: Equatable>: Equatable {
    case success(T), empty, failure(NetworkError)
}

enum HttpErrorMapper {
    static func map(_ code: Int) -> NetworkError {
        switch code {
        case 400: return .badRequest
        case 401: return .unauthorized
        case 403: return .forbidden
        case 404: return .notFound
        case 500...599: return .server(code)
        default: return .unexpectedStatus(code)
        }
    }
}

// MARK: - Lists

enum SortOrder: String, CaseIterable, Identifiable {
    case byId = "id", titleAscending = "asc", titleDescending = "desc"
    var id: String { rawValue }
}

enum DynamicListQuery {
    static func apply(_ items: [DynamicItem], query: String, sort: SortOrder) -> [DynamicItem] {
        let needle = query.trimmingCharacters(in: .whitespaces)
        let filtered = needle.isEmpty ? items : items.filter { $0.title.localizedCaseInsensitiveContains(needle) }
        switch sort {
        case .byId: return filtered.sorted { $0.id < $1.id }
        case .titleAscending: return filtered.sorted { $0.title.caseInsensitiveCompare($1.title) == .orderedAscending }
        case .titleDescending: return filtered.sorted { $0.title.caseInsensitiveCompare($1.title) == .orderedDescending }
        }
    }
}

struct Page: Equatable {
    let items: [PagedItem]
    let hasMore: Bool
}

enum PagedItems {
    static func page(_ index: Int, size: Int = SeedData.pageSize, total: Int = SeedData.pagedItemCount) -> Page {
        let start = index * size
        let end = min(start + size, total)
        guard start < end else { return Page(items: [], hasMore: false) }
        let items = (start..<end).map { PagedItem(index: $0 + 1, title: String(format: "Row %03d", $0 + 1)) }
        return Page(items: items, hasMore: end < total)
    }
}

// MARK: - Misc rules

enum PermissionStatus: Equatable { case notRequested, granted, denied, permanentlyDenied }

enum GestureType: String { case tap = "TAP", doubleTap = "DOUBLE_TAP", longPress = "LONG_PRESS"
    case swipeLeft = "SWIPE_LEFT", swipeRight = "SWIPE_RIGHT", swipeUp = "SWIPE_UP", swipeDown = "SWIPE_DOWN"
    case dragAndDrop = "DRAG_AND_DROP", pinch = "PINCH", pan = "PAN"
}

enum SwipeClassifier {
    static func classify(dx: Double, dy: Double, threshold: Double) -> GestureType? {
        guard max(abs(dx), abs(dy)) >= threshold else { return nil }
        if abs(dx) >= abs(dy) { return dx > 0 ? .swipeRight : .swipeLeft }
        return dy > 0 ? .swipeDown : .swipeUp
    }
}

enum ZoomRules {
    static let min = 1.0, max = 4.0
    static func clamp(_ value: Double) -> Double { Swift.min(Swift.max(value, min), max) }
    static func label(_ value: Double) -> String { String(format: "%.1fx", locale: Locale(identifier: "en_US_POSIX"), clamp(value)) }
}

enum DateMath {
    /// The fixed "today" used by the Date & Time scenario, so results never depend on the clock.
    static let referenceDate = "2025-01-15"

    static let isoFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    static func daysFromReference(_ iso: String) -> Int? {
        guard let date = isoFormatter.date(from: iso), let reference = isoFormatter.date(from: referenceDate) else { return nil }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar.dateComponents([.day], from: reference, to: date).day
    }
}

enum PriceFormatter {
    static func format(_ cents: Int) -> String { String(format: "%d.%02d", cents / 100, cents % 100) }
}

struct TestModeSettings: Codable, Equatable {
    var authenticationRequired = true
    var simulateNetworkError = false
    var simulateSlowNetwork = false
    /// True: authentication, catalog, cart and orders use the backend API. False: the explicit
    /// offline/test mode with local deterministic data.
    var useBackend = true
    /// Overrides the build's backend URL, e.g. `http://192.168.1.20:8080` for a physical device.
    var backendURLOverride: String?

    init(authenticationRequired: Bool = true, simulateNetworkError: Bool = false, simulateSlowNetwork: Bool = false,
         useBackend: Bool = true, backendURLOverride: String? = nil) {
        self.authenticationRequired = authenticationRequired
        self.simulateNetworkError = simulateNetworkError
        self.simulateSlowNetwork = simulateSlowNetwork
        self.useBackend = useBackend
        self.backendURLOverride = backendURLOverride
    }

    /// Tolerates settings stored by older builds that lacked the backend fields.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        authenticationRequired = try container.decodeIfPresent(Bool.self, forKey: .authenticationRequired) ?? true
        simulateNetworkError = try container.decodeIfPresent(Bool.self, forKey: .simulateNetworkError) ?? false
        simulateSlowNetwork = try container.decodeIfPresent(Bool.self, forKey: .simulateSlowNetwork) ?? false
        useBackend = try container.decodeIfPresent(Bool.self, forKey: .useBackend) ?? true
        backendURLOverride = try container.decodeIfPresent(String.self, forKey: .backendURLOverride)
    }
}

enum ThemeMode: String, CaseIterable, Codable, Identifiable {
    case system, light, dark
    var id: String { rawValue }
}
