package com.mobiletestplayground.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobiletestplayground.app.navigation.DeepLinkRouter
import com.mobiletestplayground.core.data.AppInitializer
import com.mobiletestplayground.core.domain.auth.AuthRepository
import com.mobiletestplayground.core.domain.auth.LogoutUseCase
import com.mobiletestplayground.core.domain.catalog.CartRepository
import com.mobiletestplayground.core.domain.model.Session
import com.mobiletestplayground.core.domain.preferences.PreferencesRepository
import com.mobiletestplayground.core.domain.preferences.UserPreferences
import com.mobiletestplayground.core.domain.testmode.TestModeRepository
import com.mobiletestplayground.core.domain.testmode.TestModeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-wide state: startup readiness, the session, Test Mode switches, preferences and any deep
 * link waiting to be routed. Scoped to the activity, so it survives configuration changes.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    initializer: AppInitializer,
    authRepository: AuthRepository,
    testModeRepository: TestModeRepository,
    preferencesRepository: PreferencesRepository,
    cartRepository: CartRepository,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    private val mutableReady = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = mutableReady.asStateFlow()

    val session: StateFlow<Session?> = authRepository.session
    val testMode: StateFlow<TestModeSettings> = testModeRepository.settings
    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
    val cartCount: StateFlow<Int> = cartRepository.lines
        .map { lines -> lines.sumOf { it.quantity } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val mutablePendingRoute = MutableStateFlow<String?>(null)

    /** A route requested by a deep link, consumed by the navigation host once ready. */
    val pendingRoute: StateFlow<String?> = mutablePendingRoute.asStateFlow()

    init {
        viewModelScope.launch {
            initializer.initialize()
            mutableReady.value = true
        }
    }

    fun onDeepLink(link: String) {
        DeepLinkRouter.routeFor(link)?.let { mutablePendingRoute.value = it }
    }

    fun consumePendingRoute() {
        mutablePendingRoute.value = null
    }

    fun logout() {
        viewModelScope.launch { logoutUseCase() }
    }
}
