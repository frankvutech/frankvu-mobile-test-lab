package com.mobiletestplayground.core.domain.auth

import com.mobiletestplayground.core.domain.model.Session
import com.mobiletestplayground.core.domain.model.User
import com.mobiletestplayground.core.domain.validation.PasswordPolicy
import kotlinx.coroutines.flow.StateFlow

/** Every way a login attempt can fail, in the order the checks are applied. */
enum class LoginError {
    EMPTY_EMAIL,
    INVALID_EMAIL,
    EMPTY_PASSWORD,
    UNKNOWN_USER,
    INCORRECT_PASSWORD,
    ACCOUNT_LOCKED,

    /** Backend mode only: the API could not be reached. */
    SERVICE_UNAVAILABLE,
}

sealed interface LoginResult {
    data class Success(val user: User) : LoginResult
    data class Failure(val error: LoginError) : LoginResult
}

sealed interface RegisterError {
    data object EmptyName : RegisterError
    data object EmptyEmail : RegisterError
    data object InvalidEmail : RegisterError
    data class WeakPassword(val violation: PasswordPolicy.Violation) : RegisterError
    data object PasswordsDoNotMatch : RegisterError
    data object EmailAlreadyRegistered : RegisterError
    data object ServiceUnavailable : RegisterError
}

sealed interface RegisterResult {
    data class Success(val user: User) : RegisterResult
    data class Failure(val error: RegisterError) : RegisterResult
}

sealed interface ResetPasswordError {
    data object InvalidEmail : ResetPasswordError
    data object InvalidCode : ResetPasswordError
    data class WeakPassword(val violation: PasswordPolicy.Violation) : ResetPasswordError
    data object PasswordsDoNotMatch : ResetPasswordError
    data object UnknownUser : ResetPasswordError
    data object ServiceUnavailable : ResetPasswordError
}

sealed interface ResetPasswordResult {
    data object Success : ResetPasswordResult
    data class Failure(val error: ResetPasswordError) : ResetPasswordResult
}

/** Outcome of checking credentials against the account directory, before input validation. */
sealed interface CredentialCheck {
    data class Valid(val user: User) : CredentialCheck
    data object UnknownUser : CredentialCheck
    data object IncorrectPassword : CredentialCheck
    data object Locked : CredentialCheck
    data object Unavailable : CredentialCheck
}

sealed interface RegistrationOutcome {
    data class Registered(val user: com.mobiletestplayground.core.domain.model.User) : RegistrationOutcome
    data object EmailTaken : RegistrationOutcome
    data object Unavailable : RegistrationOutcome
}

sealed interface PasswordResetOutcome {
    data object Done : PasswordResetOutcome
    data object InvalidCode : PasswordResetOutcome
    data object UnknownUser : PasswordResetOutcome
    data object Unavailable : PasswordResetOutcome
}

/**
 * Authentication and the current session.
 *
 * The production implementation talks to the backend API; the offline/test mode uses a local,
 * deterministic account directory. Input validation is *not* the repository's job: use cases do
 * that first, and the backend validates again.
 */
interface AuthRepository {
    val session: StateFlow<Session?>

    /** Loads a remembered session from storage. Called once at startup before routing. */
    suspend fun restoreSession()

    suspend fun checkCredentials(email: String, password: String): CredentialCheck

    suspend fun startSession(user: User, rememberMe: Boolean)

    suspend fun endSession()

    suspend fun register(displayName: String, email: String, password: String): RegistrationOutcome

    suspend fun resetPassword(email: String, code: String, newPassword: String): PasswordResetOutcome

    /** The email of the last remembered login, used to prefill the login form. */
    suspend fun rememberedEmail(): String?
}
