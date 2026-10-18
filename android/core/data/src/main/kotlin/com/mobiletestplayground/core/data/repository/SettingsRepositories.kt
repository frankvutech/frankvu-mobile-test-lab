package com.mobiletestplayground.core.data.repository

import com.mobiletestplayground.core.data.storage.AppStorage
import com.mobiletestplayground.core.domain.preferences.PreferencesRepository
import com.mobiletestplayground.core.domain.preferences.ThemeMode
import com.mobiletestplayground.core.domain.preferences.UserPreferences
import com.mobiletestplayground.core.domain.testmode.ResettableStore
import com.mobiletestplayground.core.domain.testmode.TestModeRepository
import com.mobiletestplayground.core.domain.testmode.TestModeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Test Mode switches, persisted so a test can set them once and relaunch. Values are held in a
 * StateFlow for synchronous reads; [load] fills it from storage at startup.
 */
class StoredTestModeRepository(
    private val storage: AppStorage,
    /** Fresh-install values; `useBackend` comes from the build (BackendConfig). */
    private val defaults: TestModeSettings = TestModeSettings(),
) : TestModeRepository,
    ResettableStore {

    private val state = MutableStateFlow(defaults)
    override val settings: StateFlow<TestModeSettings> = state.asStateFlow()

    suspend fun load() {
        state.value = storage.readTestMode(defaults)
    }

    override suspend fun update(transform: (TestModeSettings) -> TestModeSettings) {
        state.update(transform)
        storage.writeTestMode(state.value)
    }

    override suspend fun reset() {
        state.value = defaults
        storage.writeTestMode(state.value)
    }

    override suspend fun resetToSeed() = reset()
}

class StoredPreferencesRepository(
    private val storage: AppStorage,
) : PreferencesRepository,
    ResettableStore {

    private val state = MutableStateFlow(UserPreferences())
    override val preferences: StateFlow<UserPreferences> = state.asStateFlow()

    suspend fun load() {
        state.value = storage.readPreferences()
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.update { it.copy(themeMode = mode) }
        storage.writePreferences(state.value)
    }

    override suspend fun resetToSeed() {
        state.value = UserPreferences()
        storage.writePreferences(state.value)
    }
}
