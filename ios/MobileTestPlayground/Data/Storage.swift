import Foundation

/// Everything the app persists, behind a protocol so repositories are unit tested with
/// `InMemoryStorage`. The real implementation uses a dedicated UserDefaults suite, which
/// "Clear local storage" and the `-uiTestReset` launch argument wipe.
protocol KeyValueStorage: AnyObject {
    func data(forKey key: String) -> Data?
    func set(_ data: Data?, forKey key: String)
    func string(forKey key: String) -> String?
    func set(_ string: String?, forKey key: String)
    func bool(forKey key: String) -> Bool
    func set(_ value: Bool, forKey key: String)
    func clear()
}

enum StorageKeys {
    static let rememberedUser = "session_user"
    static let rememberedEmail = "remembered_email"
    static let sessionToken = "session_token"
    static let testMode = "test_mode"
    static let theme = "preference_theme"
    static let dynamicItems = "dynamic_items"
    static func permissionRequested(_ name: String) -> String { "permission_requested_\(name)" }
}

final class UserDefaultsStorage: KeyValueStorage {
    static let suiteName = "com.mobiletestplayground.storage"
    private let defaults: UserDefaults

    init(suiteName: String = UserDefaultsStorage.suiteName) {
        defaults = UserDefaults(suiteName: suiteName) ?? .standard
    }

    func data(forKey key: String) -> Data? { defaults.data(forKey: key) }
    func set(_ data: Data?, forKey key: String) { defaults.set(data, forKey: key) }
    func string(forKey key: String) -> String? { defaults.string(forKey: key) }
    func set(_ string: String?, forKey key: String) { defaults.set(string, forKey: key) }
    func bool(forKey key: String) -> Bool { defaults.bool(forKey: key) }
    func set(_ value: Bool, forKey key: String) { defaults.set(value, forKey: key) }

    func clear() {
        defaults.dictionaryRepresentation().keys.forEach { defaults.removeObject(forKey: $0) }
    }
}

final class InMemoryStorage: KeyValueStorage {
    private var values: [String: Any] = [:]

    func data(forKey key: String) -> Data? { values[key] as? Data }
    func set(_ data: Data?, forKey key: String) { values[key] = data }
    func string(forKey key: String) -> String? { values[key] as? String }
    func set(_ string: String?, forKey key: String) { values[key] = string }
    func bool(forKey key: String) -> Bool { values[key] as? Bool ?? false }
    func set(_ value: Bool, forKey key: String) { values[key] = value }
    func clear() { values.removeAll() }
}

extension KeyValueStorage {
    func decode<T: Decodable>(_ type: T.Type, forKey key: String) -> T? {
        data(forKey: key).flatMap { try? JSONDecoder().decode(type, from: $0) }
    }

    func encode<T: Encodable>(_ value: T?, forKey key: String) {
        set(value.flatMap { try? JSONEncoder().encode($0) }, forKey: key)
    }
}
