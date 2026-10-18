package com.mobiletestplayground.core.domain.auth

import com.mobiletestplayground.core.domain.model.Role
import com.mobiletestplayground.core.domain.validation.PasswordPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class LoginUseCaseTest {
    private val repository = FakeAuthRepository()
    private val login = LoginUseCase(repository)

    private suspend fun errorFor(email: String, password: String) =
        (login(email, password, rememberMe = false) as LoginResult.Failure).error

    @Test
    fun `input errors are reported in a fixed order`() = runTest {
        assertEquals(LoginError.EMPTY_EMAIL, errorFor("", ""))
        assertEquals(LoginError.EMPTY_EMAIL, errorFor("   ", "x"))
        assertEquals(LoginError.INVALID_EMAIL, errorFor("not-an-email", ""))
        assertEquals(LoginError.EMPTY_PASSWORD, errorFor("test@example.com", ""))
    }

    @Test
    fun `directory errors distinguish unknown, locked and wrong password`() = runTest {
        assertEquals(LoginError.UNKNOWN_USER, errorFor("nobody@example.com", "Password123!"))
        assertEquals(LoginError.INCORRECT_PASSWORD, errorFor("test@example.com", "wrong"))
        assertEquals(LoginError.ACCOUNT_LOCKED, errorFor("locked@example.com", "Locked123!"))
    }

    @Test
    fun `successful login starts a session with the account role`() = runTest {
        val result = login("  admin@example.com ", "Admin123!", rememberMe = true)

        assertIs<LoginResult.Success>(result)
        assertEquals(Role.ADMIN, repository.session.value?.user?.role)
        assertEquals(true, repository.session.value?.rememberMe)
        assertEquals("admin@example.com", repository.remembered)
    }

    @Test
    fun `failed login does not start a session`() = runTest {
        login("test@example.com", "wrong", rememberMe = true)
        assertNull(repository.session.value)
    }

    @Test
    fun `logout ends the session`() = runTest {
        login("test@example.com", "Password123!", rememberMe = false)
        LogoutUseCase(repository)()
        assertNull(repository.session.value)
    }
}

class RegisterUseCaseTest {
    private val repository = FakeAuthRepository()
    private val register = RegisterUseCase(repository)

    @Test
    fun `validation errors`() = runTest {
        assertEquals(RegisterError.EmptyName, (register(" ", "a@b.co", "Passw0rd!", "Passw0rd!") as RegisterResult.Failure).error)
        assertEquals(RegisterError.InvalidEmail, (register("Ann", "a@b", "Passw0rd!", "Passw0rd!") as RegisterResult.Failure).error)
        assertEquals(
            RegisterError.WeakPassword(PasswordPolicy.Violation.TOO_SHORT),
            (register("Ann", "a@b.co", "Pa1!", "Pa1!") as RegisterResult.Failure).error,
        )
        assertEquals(
            RegisterError.PasswordsDoNotMatch,
            (register("Ann", "a@b.co", "Passw0rd!", "Passw0rd?") as RegisterResult.Failure).error,
        )
        assertEquals(
            RegisterError.EmailAlreadyRegistered,
            (register("Ann", "test@example.com", "Passw0rd!", "Passw0rd!") as RegisterResult.Failure).error,
        )
    }

    @Test
    fun `registered user can log in as a customer`() = runTest {
        assertIs<RegisterResult.Success>(register("Ann", "ann@example.com", "Passw0rd!", "Passw0rd!"))
        val result = LoginUseCase(repository)("ann@example.com", "Passw0rd!", rememberMe = false)
        assertEquals(Role.CUSTOMER, (result as LoginResult.Success).user.role)
    }
}

class PasswordResetTest {
    private val repository = FakeAuthRepository()
    private val reset = ResetPasswordUseCase(repository)

    @Test
    fun `forgot password accepts any well formed email`() {
        val request = RequestPasswordResetUseCase()
        assertEquals(true, request("unknown@example.com"))
        assertEquals(false, request("bad"))
    }

    @Test
    fun `reset requires the fixed code`() = runTest {
        val result = reset("test@example.com", "000000", "NewPass1!", "NewPass1!")
        assertEquals(ResetPasswordError.InvalidCode, (result as ResetPasswordResult.Failure).error)
    }

    @Test
    fun `reset for unknown user fails`() = runTest {
        val result = reset("ghost@example.com", "123456", "NewPass1!", "NewPass1!")
        assertEquals(ResetPasswordError.UnknownUser, (result as ResetPasswordResult.Failure).error)
    }

    @Test
    fun `successful reset changes the password`() = runTest {
        assertEquals(ResetPasswordResult.Success, reset("test@example.com", "123456", "NewPass1!", "NewPass1!"))
        val login = LoginUseCase(repository)
        assertIs<LoginResult.Failure>(login("test@example.com", "Password123!", false))
        assertIs<LoginResult.Success>(login("test@example.com", "NewPass1!", false))
    }
}
