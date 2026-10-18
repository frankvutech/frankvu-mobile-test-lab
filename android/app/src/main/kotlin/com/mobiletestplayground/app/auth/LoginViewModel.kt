package com.mobiletestplayground.app.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobiletestplayground.app.UiTiming
import com.mobiletestplayground.core.domain.auth.AuthRepository
import com.mobiletestplayground.core.domain.auth.LoginError
import com.mobiletestplayground.core.domain.auth.LoginResult
import com.mobiletestplayground.core.domain.auth.LoginUseCase
import com.mobiletestplayground.core.domain.testmode.TestModeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val rememberMe: Boolean = false,
    val isLoading: Boolean = false,
    val error: LoginError? = null,
    val loggedIn: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val login: LoginUseCase,
    private val authRepository: AuthRepository,
    private val timing: UiTiming,
    private val savedState: SavedStateHandle,
    testModeRepository: TestModeRepository,
) : ViewModel() {

    /** False when Test Mode disabled authentication: the screen then offers "Continue as guest". */
    val authenticationRequired: StateFlow<Boolean> = testModeRepository.settings
        .map { it.authenticationRequired }
        .stateIn(viewModelScope, SharingStarted.Eagerly, testModeRepository.settings.value.authenticationRequired)

    // Email and Remember Me survive process death through SavedStateHandle; the password never does.
    private val mutableState = MutableStateFlow(
        LoginUiState(
            email = savedState[KEY_EMAIL] ?: "",
            rememberMe = savedState[KEY_REMEMBER] ?: false,
        ),
    )
    val state: StateFlow<LoginUiState> = mutableState.asStateFlow()

    init {
        if (savedState.get<String>(KEY_EMAIL) == null) {
            viewModelScope.launch {
                authRepository.rememberedEmail()?.let { remembered ->
                    if (mutableState.value.email.isEmpty()) {
                        onEmailChange(remembered)
                        onRememberMeChange(true)
                    }
                }
            }
        }
    }

    fun onEmailChange(value: String) {
        savedState[KEY_EMAIL] = value
        mutableState.update { it.copy(email = value, error = null) }
    }

    fun onPasswordChange(value: String) = mutableState.update { it.copy(password = value, error = null) }

    fun onTogglePasswordVisibility() = mutableState.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun onRememberMeChange(value: Boolean) {
        savedState[KEY_REMEMBER] = value
        mutableState.update { it.copy(rememberMe = value) }
    }

    fun submit() {
        val current = mutableState.value
        if (current.isLoading) return
        mutableState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // A fixed delay makes the loading state observable and identical on every run.
            delay(timing.loginDelayMillis)
            when (val result = login(current.email, current.password, current.rememberMe)) {
                is LoginResult.Success -> mutableState.update { it.copy(isLoading = false, loggedIn = true, password = "") }
                is LoginResult.Failure -> mutableState.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }

    fun onNavigated() = mutableState.update { it.copy(loggedIn = false) }

    private companion object {
        const val KEY_EMAIL = "email"
        const val KEY_REMEMBER = "remember"
    }
}
