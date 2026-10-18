package com.mobiletestplayground.core.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mobiletestplayground.core.domain.model.Role
import com.mobiletestplayground.core.domain.model.User
import com.mobiletestplayground.core.domain.preferences.ThemeMode
import com.mobiletestplayground.core.domain.preferences.UserPreferences
import com.mobiletestplayground.core.domain.testmode.TestModeSettings
import kotlinx.coroutines.flow.first

/**
 * Everything the app persists as key/value pairs. An interface so repositories are unit tested
 * against [InMemoryAppStorage] without Android.
 */
interface AppStorage {
    suspend fun readRememberedUser(): User?

    suspend fun writeRememberedUser(user: User?)

    /** The backend bearer token of a remembered session (backend mode only). */
    suspend fun readSessionToken(): String?

    suspend fun writeSessionToken(token: String?)

    suspend fun readRememberedEmail(): String?

    suspend fun writeRememberedEmail(email: String?)

    suspend fun readTestMode(defaults: TestModeSettings): TestModeSettings

    suspend fun writeTestMode(settings: TestModeSettings)

    suspend fun readPreferences(): UserPreferences

    suspend fun writePreferences(preferences: UserPreferences)

    suspend fun clear()
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "playground")

class DataStoreAppStorage(context: Context) : AppStorage {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val userId = stringPreferencesKey("session_user_id")
        val userEmail = stringPreferencesKey("session_user_email")
        val userName = stringPreferencesKey("session_user_name")
        val userRole = stringPreferencesKey("session_user_role")
        val rememberedEmail = stringPreferencesKey("remembered_email")
        val sessionToken = stringPreferencesKey("session_token")
        val useBackend = booleanPreferencesKey("test_mode_use_backend")
        val backendUrl = stringPreferencesKey("test_mode_backend_url")
        val authRequired = booleanPreferencesKey("test_mode_auth_required")
        val networkError = booleanPreferencesKey("test_mode_network_error")
        val slowNetwork = booleanPreferencesKey("test_mode_slow_network")
        val theme = stringPreferencesKey("preference_theme")
    }

    private suspend fun snapshot(): Preferences = store.data.first()

    override suspend fun readRememberedUser(): User? {
        val prefs = snapshot()
        val id = prefs[Keys.userId] ?: return null
        val email = prefs[Keys.userEmail] ?: return null
        val role = prefs[Keys.userRole]?.let { runCatching { Role.valueOf(it) }.getOrNull() } ?: return null
        return User(id, email, prefs[Keys.userName].orEmpty(), role)
    }

    override suspend fun writeRememberedUser(user: User?) {
        store.edit { prefs ->
            if (user == null) {
                prefs -= Keys.userId
                prefs -= Keys.userEmail
                prefs -= Keys.userName
                prefs -= Keys.userRole
            } else {
                prefs[Keys.userId] = user.id
                prefs[Keys.userEmail] = user.email
                prefs[Keys.userName] = user.displayName
                prefs[Keys.userRole] = user.role.name
            }
        }
    }

    override suspend fun readRememberedEmail(): String? = snapshot()[Keys.rememberedEmail]

    override suspend fun readSessionToken(): String? = snapshot()[Keys.sessionToken]

    override suspend fun writeSessionToken(token: String?) {
        store.edit { prefs -> if (token == null) prefs -= Keys.sessionToken else prefs[Keys.sessionToken] = token }
    }

    override suspend fun writeRememberedEmail(email: String?) {
        store.edit { prefs -> if (email == null) prefs -= Keys.rememberedEmail else prefs[Keys.rememberedEmail] = email }
    }

    override suspend fun readTestMode(defaults: TestModeSettings): TestModeSettings {
        val prefs = snapshot()
        return TestModeSettings(
            authenticationRequired = prefs[Keys.authRequired] ?: defaults.authenticationRequired,
            simulateNetworkError = prefs[Keys.networkError] ?: defaults.simulateNetworkError,
            simulateSlowNetwork = prefs[Keys.slowNetwork] ?: defaults.simulateSlowNetwork,
            useBackend = prefs[Keys.useBackend] ?: defaults.useBackend,
            backendUrlOverride = prefs[Keys.backendUrl] ?: defaults.backendUrlOverride,
        )
    }

    override suspend fun writeTestMode(settings: TestModeSettings) {
        store.edit { prefs ->
            prefs[Keys.authRequired] = settings.authenticationRequired
            prefs[Keys.networkError] = settings.simulateNetworkError
            prefs[Keys.slowNetwork] = settings.simulateSlowNetwork
            prefs[Keys.useBackend] = settings.useBackend
            val url = settings.backendUrlOverride
            if (url.isNullOrBlank()) prefs -= Keys.backendUrl else prefs[Keys.backendUrl] = url
        }
    }

    override suspend fun readPreferences(): UserPreferences {
        val theme = snapshot()[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
        return UserPreferences(themeMode = theme ?: ThemeMode.SYSTEM)
    }

    override suspend fun writePreferences(preferences: UserPreferences) {
        store.edit { prefs -> prefs[Keys.theme] = preferences.themeMode.name }
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }
}

/** Process-local storage for unit tests and previews. */
class InMemoryAppStorage : AppStorage {
    private var user: User? = null
    private var email: String? = null
    private var token: String? = null
    private var testMode: TestModeSettings? = null
    private var preferences = UserPreferences()

    override suspend fun readRememberedUser() = user
    override suspend fun writeRememberedUser(user: User?) {
        this.user = user
    }
    override suspend fun readRememberedEmail() = email
    override suspend fun readSessionToken() = token
    override suspend fun writeSessionToken(token: String?) {
        this.token = token
    }
    override suspend fun writeRememberedEmail(email: String?) {
        this.email = email
    }
    override suspend fun readTestMode(defaults: TestModeSettings) = testMode ?: defaults
    override suspend fun writeTestMode(settings: TestModeSettings) {
        testMode = settings
    }
    override suspend fun readPreferences() = preferences
    override suspend fun writePreferences(preferences: UserPreferences) {
        this.preferences = preferences
    }
    override suspend fun clear() {
        user = null
        email = null
        token = null
        testMode = null
        preferences = UserPreferences()
    }
}
