package com.mobiletestplayground.core.domain.preferences

import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

/** Persisted user preferences. Survive process restart; cleared by "Clear local storage". */
interface PreferencesRepository {
    val preferences: StateFlow<UserPreferences>

    suspend fun setThemeMode(mode: ThemeMode)
}
