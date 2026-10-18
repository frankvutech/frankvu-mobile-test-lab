package com.mobiletestplayground.core.data.repository

import com.mobiletestplayground.core.data.remote.BackendClientProvider
import com.mobiletestplayground.core.data.remote.BackendErrors
import com.mobiletestplayground.core.data.remote.LoginJson
import com.mobiletestplayground.core.data.remote.PasswordResetJson
import com.mobiletestplayground.core.data.remote.RegisterJson
import com.mobiletestplayground.core.data.storage.AppStorage
import com.mobiletestplayground.core.domain.auth.AuthRepository
import com.mobiletestplayground.core.domain.auth.CredentialCheck
import com.mobiletestplayground.core.domain.auth.PasswordResetOutcome
import com.mobiletestplayground.core.domain.auth.RegistrationOutcome
import com.mobiletestplayground.core.domain.model.Role
import com.mobiletestplayground.core.domain.model.Session
import com.mobiletestplayground.core.domain.model.User
import com.mobiletestplayground.core.domain.seed.SeedData
import com.mobiletestplayground.core.domain.testmode.ResettableStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException

/**
 * Where credentials are checked. Two implementations: the backend API (the real implementation)
 * and a local account directory (the explicit offline/test mode).
 */
interface AuthDataSource {
    /** Checks credentials; on success also returns the backend token, if any. */
    suspend fun login(email: String, password: String): Pair<CredentialCheck, String?>

    suspend fun register(displayName: String, email: String, password: String): Pair<RegistrationOutcome, String?>

    suspend fun resetPassword(email: String, code: String, newPassword: String): PasswordResetOutcome

    suspend fun logout(token: String?)
}

/** Offline/test mode: the seed accounts plus anything registered during this process. */
class LocalAccountDirectory : AuthDataSource {
    private val mutex = Mutex()
    private val directory = seed()

    override suspend fun login(email: String, password: String): Pair<CredentialCheck, String?> = mutex.withLock {
        val account = directory[email.lowercase()] ?: return@withLock CredentialCheck.UnknownUser to null
        val check = when {
            account.locked -> CredentialCheck.Locked
            account.password != password -> CredentialCheck.IncorrectPassword
            else -> CredentialCheck.Valid(account.user)
        }
        check to null
    }

    override suspend fun register(displayName: String, email: String, password: String): Pair<RegistrationOutcome, String?> =
        mutex.withLock {
            val normalized = email.lowercase()
            if (directory.containsKey(normalized)) return@withLock RegistrationOutcome.EmailTaken to null
            val user = User("user_registered_${directory.size + 1}", normalized, displayName, Role.CUSTOMER)
            directory[normalized] = SeedData.SeedAccount(user, password)
            RegistrationOutcome.Registered(user) to null
        }

    override suspend fun resetPassword(email: String, code: String, newPassword: String): PasswordResetOutcome = mutex.withLock {
        val key = email.lowercase()
        val account = directory[key] ?: return@withLock PasswordResetOutcome.UnknownUser
        if (code != SeedData.PASSWORD_RESET_CODE) return@withLock PasswordResetOutcome.InvalidCode
        directory[key] = account.copy(password = newPassword)
        PasswordResetOutcome.Done
    }

    override suspend fun logout(token: String?) = Unit

    suspend fun reset() = mutex.withLock {
        directory.clear()
        directory.putAll(seed())
    }

    private fun seed() = SeedData.accounts.associateBy { it.user.email.lowercase() }.toMutableMap()
}

/** Backend mode: `POST /api/v1/auth/...`. Error codes from the API map onto the domain outcomes. */
class RemoteAuthDataSource(private val client: BackendClientProvider) : AuthDataSource {
    override suspend fun login(email: String, password: String): Pair<CredentialCheck, String?> = try {
        val auth = client.api().login(LoginJson(email, password))
        CredentialCheck.Valid(auth.user.toDomain()) to auth.token
    } catch (e: HttpException) {
        val check = when (BackendErrors.code(e)) {
            "UNKNOWN_USER" -> CredentialCheck.UnknownUser
            "ACCOUNT_LOCKED" -> CredentialCheck.Locked
            else -> CredentialCheck.IncorrectPassword
        }
        check to null
    } catch (e: IOException) {
        CredentialCheck.Unavailable to null
    }

    override suspend fun register(displayName: String, email: String, password: String): Pair<RegistrationOutcome, String?> = try {
        val auth = client.api().register(RegisterJson(displayName, email, password))
        RegistrationOutcome.Registered(auth.user.toDomain()) to auth.token
    } catch (e: HttpException) {
        (if (e.code() == HTTP_CONFLICT) RegistrationOutcome.EmailTaken else RegistrationOutcome.Unavailable) to null
    } catch (e: IOException) {
        RegistrationOutcome.Unavailable to null
    }

    override suspend fun resetPassword(email: String, code: String, newPassword: String): PasswordResetOutcome = try {
        val response = client.api().resetPassword(PasswordResetJson(email, code, newPassword))
        if (response.isSuccessful) {
            PasswordResetOutcome.Done
        } else {
            when (BackendErrors.code(HttpException(response))) {
                "INVALID_RESET_CODE" -> PasswordResetOutcome.InvalidCode
                "UNKNOWN_USER" -> PasswordResetOutcome.UnknownUser
                else -> PasswordResetOutcome.Unavailable
            }
        }
    } catch (e: IOException) {
        PasswordResetOutcome.Unavailable
    }

    override suspend fun logout(token: String?) {
        // Best effort: the local session ends whether or not the server could revoke the token.
        if (token != null) runCatching { client.api().logout("Bearer $token") }
    }

    private companion object {
        const val HTTP_CONFLICT = 409
    }
}

/**
 * The session, with Remember Me semantics shared by both modes:
 *
 * - Remember Me on: the user (and backend token) are persisted and restored after a process restart.
 * - Remember Me off: the session exists only in memory.
 * - Logout clears the stored session but keeps the remembered email for prefilling the form.
 * - Clearing app data (or Test Mode "Clear local storage") removes everything.
 */
class LocalAuthRepository(
    private val storage: AppStorage,
    private val local: LocalAccountDirectory,
    private val remote: AuthDataSource,
    private val useBackend: () -> Boolean,
) : AuthRepository,
    ResettableStore {

    private val mutableSession = MutableStateFlow<Session?>(null)
    override val session: StateFlow<Session?> = mutableSession.asStateFlow()
    private var pendingToken: String? = null

    /** The bearer token for backend calls, or null in offline mode / when signed out. */
    @Volatile
    var token: String? = null
        private set

    private val source: AuthDataSource get() = if (useBackend()) remote else local

    override suspend fun restoreSession() {
        val remembered = storage.readRememberedUser() ?: return
        if (mutableSession.value == null) {
            token = storage.readSessionToken()
            mutableSession.value = Session(remembered, rememberMe = true)
        }
    }

    override suspend fun checkCredentials(email: String, password: String): CredentialCheck {
        val (check, issuedToken) = source.login(email, password)
        pendingToken = issuedToken
        return check
    }

    override suspend fun startSession(user: User, rememberMe: Boolean) {
        token = pendingToken
        pendingToken = null
        storage.writeRememberedUser(if (rememberMe) user else null)
        storage.writeSessionToken(if (rememberMe) token else null)
        storage.writeRememberedEmail(if (rememberMe) user.email else null)
        mutableSession.value = Session(user, rememberMe)
    }

    override suspend fun endSession() {
        source.logout(token)
        token = null
        storage.writeRememberedUser(null)
        storage.writeSessionToken(null)
        mutableSession.value = null
    }

    override suspend fun register(displayName: String, email: String, password: String): RegistrationOutcome =
        source.register(displayName, email, password).first

    override suspend fun resetPassword(email: String, code: String, newPassword: String): PasswordResetOutcome =
        source.resetPassword(email, code, newPassword)

    override suspend fun rememberedEmail(): String? = storage.readRememberedEmail()

    override suspend fun resetToSeed() {
        local.reset()
        token = null
        storage.writeRememberedUser(null)
        storage.writeSessionToken(null)
        storage.writeRememberedEmail(null)
        mutableSession.value = null
    }
}
