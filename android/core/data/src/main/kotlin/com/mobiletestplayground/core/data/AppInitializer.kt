package com.mobiletestplayground.core.data

import com.mobiletestplayground.core.data.repository.LocalAuthRepository
import com.mobiletestplayground.core.data.repository.RoomDynamicItemsRepository
import com.mobiletestplayground.core.data.repository.StoredPreferencesRepository
import com.mobiletestplayground.core.data.repository.StoredTestModeRepository
import com.mobiletestplayground.core.data.storage.AppStorage
import com.mobiletestplayground.core.domain.testmode.ResetApplicationStateUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads persisted state before the first screen is chosen, so routing never flashes the login
 * screen for a remembered user.
 */
@Singleton
class AppInitializer @Inject constructor(
    private val auth: LocalAuthRepository,
    private val testMode: StoredTestModeRepository,
    private val preferences: StoredPreferencesRepository,
    private val dynamicItems: RoomDynamicItemsRepository,
) {
    suspend fun initialize() {
        testMode.load()
        preferences.load()
        auth.restoreSession()
        dynamicItems.ensureSeeded()
    }
}

/**
 * "Clear local storage": wipes DataStore, then resets every in-memory store to match. After this
 * the app behaves as after `adb shell pm clear`, without being restarted.
 */
class ClearLocalStorageUseCase @Inject constructor(
    private val storage: AppStorage,
    private val resetState: ResetApplicationStateUseCase,
) {
    suspend operator fun invoke() {
        resetState()
        storage.clear()
    }
}
