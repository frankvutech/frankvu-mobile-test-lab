package com.mobiletestplayground.core.domain.testmode

import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class TestModeSettings(
    val authenticationRequired: Boolean = true,
    val simulateNetworkError: Boolean = false,
    val simulateSlowNetwork: Boolean = false,
    /**
     * True: authentication, catalog, cart and orders use the backend API. False: the explicit
     * offline/test mode with local deterministic data. The default comes from the build.
     */
    val useBackend: Boolean = true,
    /** Overrides the build's backend base URL, e.g. `http://192.168.1.20:8080/` for a physical device. */
    val backendUrlOverride: String? = null,
)

interface TestModeRepository {
    val settings: StateFlow<TestModeSettings>

    suspend fun update(transform: (TestModeSettings) -> TestModeSettings)

    suspend fun reset()
}

/** Anything holding state that "Reset application state" must restore to the seed. */
interface ResettableStore {
    suspend fun resetToSeed()
}

/**
 * Restores the app to its first-launch state: signs out, forgets the remembered email, restores
 * seed data and Test Mode defaults. Equivalent to clearing app data, without leaving the app.
 */
class ResetApplicationStateUseCase @Inject constructor(
    private val stores: Set<@JvmSuppressWildcards ResettableStore>,
) {
    suspend operator fun invoke() {
        stores.forEach { it.resetToSeed() }
    }
}
