package com.mobiletestplayground.app.scenarios.forms

import androidx.lifecycle.ViewModel
import com.mobiletestplayground.core.domain.form.FieldError
import com.mobiletestplayground.core.domain.form.FormField
import com.mobiletestplayground.core.domain.form.FormInput
import com.mobiletestplayground.core.domain.form.FormValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class FormsUiState(
    val input: FormInput = FormInput(),
    /** Errors are shown only after the first submit, then kept live as the user corrects them. */
    val showErrors: Boolean = false,
    val errors: Map<FormField, FieldError> = emptyMap(),
    val submitted: Boolean = false,
)

@HiltViewModel
class FormsViewModel @Inject constructor() : ViewModel() {
    private val mutableState = MutableStateFlow(FormsUiState())
    val state: StateFlow<FormsUiState> = mutableState.asStateFlow()

    fun edit(transform: (FormInput) -> FormInput) = mutableState.update { current ->
        val input = transform(current.input)
        current.copy(
            input = input,
            errors = if (current.showErrors) FormValidator.validate(input).errors else emptyMap(),
            submitted = false,
        )
    }

    fun submit() = mutableState.update { current ->
        val validation = FormValidator.validate(current.input)
        current.copy(showErrors = true, errors = validation.errors, submitted = validation.isValid)
    }

    fun reset() {
        mutableState.value = FormsUiState()
    }

    companion object {
        val COUNTRIES = listOf("de", "fr", "gb", "us")
        val PLANS = listOf("basic", "pro", "enterprise")
    }
}
