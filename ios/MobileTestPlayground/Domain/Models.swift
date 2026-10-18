import Foundation

/// Roles drive authorization. Every account has exactly one.
enum Role: String, Codable, CaseIterable {
    case customer = "CUSTOMER"
    case admin = "ADMIN"
}

struct User: Codable, Equatable, Identifiable {
    let id: String
    let email: String
    let displayName: String
    let role: Role
}

/// An authenticated session. A remembered session survives app restart; others live in memory only.
struct Session: Equatable {
    let user: User
    let rememberMe: Bool
}

struct Product: Codable, Equatable, Identifiable {
    let id: String
    let name: String
    let priceCents: Int
    let category: String
}

struct CartLine: Equatable, Identifiable {
    let product: Product
    var quantity: Int
    var id: String { product.id }
    var totalCents: Int { product.priceCents * quantity }
}

/// An editable row in the Dynamic List scenario. Ids are stable and never reused.
struct DynamicItem: Codable, Equatable, Identifiable {
    let id: Int
    var title: String
}

struct PagedItem: Equatable, Identifiable {
    let index: Int
    let title: String
    var id: Int { index }
}

/// The deterministic data set every run starts from. Mirrors Android's `SeedData.kt`; keep both in step.
enum SeedData {
    struct SeedAccount {
        let user: User
        var password: String
        var locked = false
    }

    static let accounts: [SeedAccount] = [
        SeedAccount(user: User(id: "user_customer", email: "test@example.com", displayName: "Test Customer", role: .customer),
                    password: "Password123!"),
        SeedAccount(user: User(id: "user_qa", email: "qa@example.com", displayName: "QA User", role: .customer),
                    password: "QaPassword123!"),
        SeedAccount(user: User(id: "user_admin", email: "admin@example.com", displayName: "Admin User", role: .admin),
                    password: "Admin123!"),
        SeedAccount(user: User(id: "user_locked", email: "locked@example.com", displayName: "Locked User", role: .customer),
                    password: "Locked123!", locked: true)
    ]

    private static let categories = ["Books", "Electronics", "Garden", "Toys"]

    /// Product 001 … Product 020. Price is a fixed function of the index.
    static let products: [Product] = (1...20).map { index in
        let number = String(format: "%03d", index)
        return Product(id: "product_\(number)", name: "Product \(number)", priceCents: 499 + index * 250,
                       category: categories[(index - 1) % categories.count])
    }

    /// Item 001 … Item 030 for the static list scenario.
    static let staticListItems: [String] = (1...30).map { String(format: "Item %03d", $0) }

    static let dynamicItems: [DynamicItem] = [
        DynamicItem(id: 1, title: "Apple"),
        DynamicItem(id: 2, title: "Banana"),
        DynamicItem(id: 3, title: "Cherry"),
        DynamicItem(id: 4, title: "Date"),
        DynamicItem(id: 5, title: "Elderberry")
    ]

    static let pagedItemCount = 100
    static let pageSize = 20
    static let passwordResetCode = "123456"
}
