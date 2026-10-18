package com.mobiletestplayground.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobiletestplayground.core.domain.auth.RegisterError
import com.mobiletestplayground.core.domain.auth.RegisterResult
import com.mobiletestplayground.core.domain.auth.RegisterUseCase
import com.mobiletestplayground.core.domain.auth.RequestPasswordResetUseCase
import com.mobiletestplayground.core.domain.auth.ResetPasswordError
import com.mobiletestplayground.core.domain.auth.ResetPasswordResult
import com.mobiletestplayground.core.domain.auth.ResetPasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val error: RegisterError? = null,
    val registered: Boolean = false,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val register: RegisterUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = mutableState.asStateFlow()

    fun onNameChange(value: String) = mutableState.update { it.copy(name = value, error = null) }
    fun onEmailChange(value: String) = mutableState.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = mutableState.update { it.copy(password = value, error = null) }
    fun onConfirmChange(value: String) = mutableState.update { it.copy(confirmPassword = value, error = null) }

    fun submit() {
        val current = mutableState.value
        viewModelScope.launch {
            when (val result = register(current.name, current.email, current.password, current.confirmPassword)) {
                is RegisterResult.Success -> mutableState.update { it.copy(registered = true) }
                is RegisterResult.Failure -> mutableState.update { it.copy(error = result.error) }
            }
        }
    }
}

data class ForgotPasswordUiState(
    val email: String = "",
    val invalidEmail: Boolean = false,
    val submitted: Boolean = false,
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val requestReset: RequestPasswordResetUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ForgotPasswordUiState())
    val state: StateFlow<ForgotPasswordUiState> = mutableState.asStateFlow()

    fun onEmailChange(value: String) = mutableState.update { it.copy(email = value, invalidEmail = false) }

    fun submit() = mutableState.update {
        val accepted = requestReset(it.email)
        it.copy(invalidEmail = !accepted, submitted = accepted)
    }
}

data class ResetPasswordUiState(
    val email: String = "",
    val code: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val error: ResetPasswordError? = null,
    val done: Boolean = false,
)

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val resetPassword: ResetPasswordUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ResetPasswordUiState())
    val state: StateFlow<ResetPasswordUiState> = mutableState.asStateFlow()

    fun prefill(email: String) {
        if (mutableState.value.email.isEmpty()) mutableState.update { it.copy(email = email) }
    }

    fun onEmailChange(value: String) = mutableState.update { it.copy(email = value, error = null) }
    fun onCodeChange(value: String) = mutableState.update { it.copy(code = value, error = null) }
    fun onPasswordChange(value: String) = mutableState.update { it.copy(password = value, error = null) }
    fun onConfirmChange(value: String) = mutableState.update { it.copy(confirmPassword = value, error = null) }

    fun submit() {
        val current = mutableState.value
        viewModelScope.launch {
            when (val result = resetPassword(current.email, current.code, current.password, current.confirmPassword)) {
                ResetPasswordResult.Success -> mutableState.update { it.copy(done = true) }
                is ResetPasswordResult.Failure -> mutableState.update { it.copy(error = result.error) }
            }
        }
    }
}
