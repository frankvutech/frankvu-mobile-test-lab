package com.mobiletestplayground.core.domain.auth

import com.mobiletestplayground.core.domain.model.Role
import com.mobiletestplayground.core.domain.model.Session
import com.mobiletestplayground.core.domain.model.User
import com.mobiletestplayground.core.domain.seed.SeedData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory repository seeded from [SeedData], so use case tests exercise real account data. */
class FakeAuthRepository : AuthRepository {
    private val accounts = SeedData.accounts.associateBy { it.user.email }.toMutableMap()
    private val mutableSession = MutableStateFlow<Session?>(null)
    override val session: StateFlow<Session?> = mutableSession
    var remembered: String? = null

    override suspend fun restoreSession() = Unit

    override suspend fun checkCredentials(email: String, password: String): CredentialCheck {
        val account = accounts[email.lowercase()] ?: return CredentialCheck.UnknownUser
        return when {
            account.locked -> CredentialCheck.Locked
            account.password != password -> CredentialCheck.IncorrectPassword
            else -> CredentialCheck.Valid(account.user)
        }
    }

    override suspend fun startSession(user: User, rememberMe: Boolean) {
        mutableSession.value = Session(user, rememberMe)
        remembered = if (rememberMe) user.email else null
    }

    override suspend fun endSession() {
        mutableSession.value = null
    }

    override suspend fun register(displayName: String, email: String, password: String): RegistrationOutcome {
        if (accounts.containsKey(email.lowercase())) return RegistrationOutcome.EmailTaken
        val user = User("user_${accounts.size + 1}", email.lowercase(), displayName, Role.CUSTOMER)
        accounts[user.email] = SeedData.SeedAccount(user, password)
        return RegistrationOutcome.Registered(user)
    }

    override suspend fun resetPassword(email: String, code: String, newPassword: String): PasswordResetOutcome {
        val key = email.lowercase()
        val account = accounts[key] ?: return PasswordResetOutcome.UnknownUser
        accounts[key] = account.copy(password = newPassword)
        return PasswordResetOutcome.Done
    }

    override suspend fun rememberedEmail(): String? = remembered
}
