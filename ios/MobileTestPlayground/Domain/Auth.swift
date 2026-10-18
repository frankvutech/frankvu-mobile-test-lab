import Foundation

enum EmailValidator {
    private static let pattern = #"^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$"#

    static func isValid(_ email: String) -> Bool {
        let trimmed = email.trimmingCharacters(in: .whitespaces)
        return trimmed.range(of: pattern, options: .regularExpression) != nil
    }
}

enum PasswordPolicy {
    static let minLength = 8
    static let maxLength = 64

    enum Violation: Equatable { case tooShort, tooLong, missingUppercase, missingDigit, missingSpecial }

    static func firstViolation(_ password: String) -> Violation? {
        if password.count < minLength { return .tooShort }
        if password.count > maxLength { return .tooLong }
        if !password.contains(where: { $0.isUppercase }) { return .missingUppercase }
        if !password.contains(where: { $0.isNumber }) { return .missingDigit }
        if password.allSatisfy({ $0.isLetter || $0.isNumber }) { return .missingSpecial }
        return nil
    }
}

/// Every way a login attempt can fail, in the order checks are applied.
enum LoginError: Error, Equatable {
    case emptyEmail, invalidEmail, emptyPassword, unknownUser, incorrectPassword, accountLocked
    /// Backend mode only: the API could not be reached.
    case serviceUnavailable
}

enum CredentialCheck: Equatable {
    case valid(User), unknownUser, incorrectPassword, locked, unavailable
}

enum RegistrationOutcome: Equatable { case registered(User), emailTaken, unavailable }

enum PasswordResetOutcome: Equatable { case done, invalidCode, unknownUser, unavailable }

enum RegisterError: Error, Equatable {
    case emptyName, emptyEmail, invalidEmail, weakPassword(PasswordPolicy.Violation), passwordsDoNotMatch, emailAlreadyRegistered
    case serviceUnavailable
}

enum ResetPasswordError: Error, Equatable {
    case invalidEmail, invalidCode, weakPassword(PasswordPolicy.Violation), passwordsDoNotMatch, unknownUser, serviceUnavailable
}

/// Authentication and the current session. The production implementation talks to the backend;
/// the offline/test mode uses a local deterministic directory. Input validation happens first, in
/// the use cases (and again on the backend).
protocol AuthRepository: AnyObject {
    var session: Session? { get }
    var rememberedEmail: String? { get }
    func checkCredentials(email: String, password: String) async -> CredentialCheck
    func startSession(user: User, rememberMe: Bool)
    func endSession() async
    func register(displayName: String, email: String, password: String) async -> RegistrationOutcome
    func resetPassword(email: String, code: String, newPassword: String) async -> PasswordResetOutcome
}

/// Validates input in a fixed order, then checks credentials. Mirrors Android's LoginUseCase.
struct LoginUseCase {
    let repository: AuthRepository

    func callAsFunction(email: String, password: String, rememberMe: Bool) async -> Result<User, LoginError> {
        let trimmed = email.trimmingCharacters(in: .whitespaces)
        if trimmed.isEmpty { return .failure(.emptyEmail) }
        if !EmailValidator.isValid(trimmed) { return .failure(.invalidEmail) }
        if password.isEmpty { return .failure(.emptyPassword) }
        switch await repository.checkCredentials(email: trimmed, password: password) {
        case .valid(let user):
            repository.startSession(user: user, rememberMe: rememberMe)
            return .success(user)
        case .unknownUser: return .failure(.unknownUser)
        case .incorrectPassword: return .failure(.incorrectPassword)
        case .locked: return .failure(.accountLocked)
        case .unavailable: return .failure(.serviceUnavailable)
        }
    }
}

struct RegisterUseCase {
    let repository: AuthRepository

    func callAsFunction(name: String, email: String, password: String, confirm: String) async -> Result<User, RegisterError> {
        let trimmed = email.trimmingCharacters(in: .whitespaces)
        if name.trimmingCharacters(in: .whitespaces).isEmpty { return .failure(.emptyName) }
        if trimmed.isEmpty { return .failure(.emptyEmail) }
        if !EmailValidator.isValid(trimmed) { return .failure(.invalidEmail) }
        if let violation = PasswordPolicy.firstViolation(password) { return .failure(.weakPassword(violation)) }
        if password != confirm { return .failure(.passwordsDoNotMatch) }
        switch await repository.register(displayName: name.trimmingCharacters(in: .whitespaces), email: trimmed, password: password) {
        case .registered(let user): return .success(user)
        case .emailTaken: return .failure(.emailAlreadyRegistered)
        case .unavailable: return .failure(.serviceUnavailable)
        }
    }
}

struct ResetPasswordUseCase {
    let repository: AuthRepository

    func callAsFunction(email: String, code: String, password: String, confirm: String) async -> Result<Void, ResetPasswordError> {
        let trimmed = email.trimmingCharacters(in: .whitespaces)
        if !EmailValidator.isValid(trimmed) { return .failure(.invalidEmail) }
        if code.trimmingCharacters(in: .whitespaces) != SeedData.passwordResetCode { return .failure(.invalidCode) }
        if let violation = PasswordPolicy.firstViolation(password) { return .failure(.weakPassword(violation)) }
        if password != confirm { return .failure(.passwordsDoNotMatch) }
        switch await repository.resetPassword(email: trimmed, code: code.trimmingCharacters(in: .whitespaces), newPassword: password) {
        case .done: return .success(())
        case .invalidCode: return .failure(.invalidCode)
        case .unknownUser: return .failure(.unknownUser)
        case .unavailable: return .failure(.serviceUnavailable)
        }
    }
}

/// Why a data operation failed, whichever source served it.
enum FailureReason: Error, Equatable { case unavailable, unauthorized, notFound, conflict, unknown }
