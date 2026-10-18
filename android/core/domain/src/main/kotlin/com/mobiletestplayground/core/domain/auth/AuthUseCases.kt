package com.mobiletestplayground.core.domain.auth

import com.mobiletestplayground.core.domain.seed.SeedData
import com.mobiletestplayground.core.domain.validation.EmailValidator
import com.mobiletestplayground.core.domain.validation.PasswordPolicy
import javax.inject.Inject

/**
 * Validates login input, then checks credentials. Checks run in a fixed order so a given input
 * always yields the same single error: empty email, invalid email, empty password, then the
 * directory lookup (unknown user, locked, incorrect password).
 */
class LoginUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String, rememberMe: Boolean): LoginResult {
        val trimmedEmail = email.trim()
        val inputError = when {
            trimmedEmail.isEmpty() -> LoginError.EMPTY_EMAIL
            !EmailValidator.isValid(trimmedEmail) -> LoginError.INVALID_EMAIL
            password.isEmpty() -> LoginError.EMPTY_PASSWORD
            else -> null
        }
        if (inputError != null) return LoginResult.Failure(inputError)

        return when (val check = repository.checkCredentials(trimmedEmail, password)) {
            is CredentialCheck.Valid -> {
                repository.startSession(check.user, rememberMe)
                LoginResult.Success(check.user)
            }
            CredentialCheck.UnknownUser -> LoginResult.Failure(LoginError.UNKNOWN_USER)
            CredentialCheck.IncorrectPassword -> LoginResult.Failure(LoginError.INCORRECT_PASSWORD)
            CredentialCheck.Locked -> LoginResult.Failure(LoginError.ACCOUNT_LOCKED)
            CredentialCheck.Unavailable -> LoginResult.Failure(LoginError.SERVICE_UNAVAILABLE)
        }
    }
}

class LogoutUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke() = repository.endSession()
}

class RegisterUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(
        displayName: String,
        email: String,
        password: String,
        confirmPassword: String,
    ): RegisterResult {
        val trimmedEmail = email.trim()
        val violation = PasswordPolicy.firstViolation(password)
        val error = when {
            displayName.isBlank() -> RegisterError.EmptyName
            trimmedEmail.isEmpty() -> RegisterError.EmptyEmail
            !EmailValidator.isValid(trimmedEmail) -> RegisterError.InvalidEmail
            violation != null -> RegisterError.WeakPassword(violation)
            password != confirmPassword -> RegisterError.PasswordsDoNotMatch
            else -> null
        }
        if (error != null) return RegisterResult.Failure(error)
        return when (val outcome = repository.register(displayName.trim(), trimmedEmail, password)) {
            is RegistrationOutcome.Registered -> RegisterResult.Success(outcome.user)
            RegistrationOutcome.EmailTaken -> RegisterResult.Failure(RegisterError.EmailAlreadyRegistered)
            RegistrationOutcome.Unavailable -> RegisterResult.Failure(RegisterError.ServiceUnavailable)
        }
    }
}

/**
 * Forgot password always reports success for a well-formed email, whether or not an account
 * exists, so the screen does not reveal which emails are registered. The reset code is the fixed
 * [SeedData.PASSWORD_RESET_CODE].
 */
class RequestPasswordResetUseCase @Inject constructor() {
    operator fun invoke(email: String): Boolean = EmailValidator.isValid(email.trim())
}

class ResetPasswordUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(
        email: String,
        code: String,
        newPassword: String,
        confirmPassword: String,
    ): ResetPasswordResult {
        val trimmedEmail = email.trim()
        val violation = PasswordPolicy.firstViolation(newPassword)
        val error = when {
            !EmailValidator.isValid(trimmedEmail) -> ResetPasswordError.InvalidEmail
            code.trim() != SeedData.PASSWORD_RESET_CODE -> ResetPasswordError.InvalidCode
            violation != null -> ResetPasswordError.WeakPassword(violation)
            newPassword != confirmPassword -> ResetPasswordError.PasswordsDoNotMatch
            else -> null
        }
        if (error != null) return ResetPasswordResult.Failure(error)
        return when (repository.resetPassword(trimmedEmail, code.trim(), newPassword)) {
            PasswordResetOutcome.Done -> ResetPasswordResult.Success
            PasswordResetOutcome.InvalidCode -> ResetPasswordResult.Failure(ResetPasswordError.InvalidCode)
            PasswordResetOutcome.UnknownUser -> ResetPasswordResult.Failure(ResetPasswordError.UnknownUser)
            PasswordResetOutcome.Unavailable -> ResetPasswordResult.Failure(ResetPasswordError.ServiceUnavailable)
        }
    }
}
