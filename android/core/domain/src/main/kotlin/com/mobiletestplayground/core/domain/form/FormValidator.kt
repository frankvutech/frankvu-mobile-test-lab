package com.mobiletestplayground.core.domain.form

import com.mobiletestplayground.core.domain.validation.EmailValidator

enum class FormField { FULL_NAME, EMAIL, PASSWORD, AGE, COUNTRY, PLAN, DATE, COMMENTS, TERMS }

enum class FieldError { REQUIRED, INVALID_EMAIL, TOO_SHORT, TOO_LONG, NOT_A_NUMBER, OUT_OF_RANGE, MUST_ACCEPT }

/** Raw values as typed or selected in the Forms scenario. */
data class FormInput(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val age: String = "",
    val country: String? = null,
    val plan: String? = null,
    val acceptTerms: Boolean = false,
    val newsletter: Boolean = false,
    val satisfaction: Int = 5,
    val date: String? = null,
    val time: String? = null,
    val comments: String = "",
)

data class FormValidation(val errors: Map<FormField, FieldError>) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Deterministic rules for the Forms scenario. Each field reports at most one error, and the
 * limits are constants so tests can target the exact boundary.
 */
object FormValidator {
    const val NAME_MIN = 2
    const val NAME_MAX = 40
    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 20
    const val AGE_MIN = 18
    const val AGE_MAX = 120
    const val COMMENTS_MAX = 200

    fun validate(input: FormInput): FormValidation {
        val errors = buildMap {
            lengthError(input.fullName.trim(), NAME_MIN, NAME_MAX)?.let { put(FormField.FULL_NAME, it) }
            emailError(input.email)?.let { put(FormField.EMAIL, it) }
            lengthError(input.password, PASSWORD_MIN, PASSWORD_MAX)?.let { put(FormField.PASSWORD, it) }
            ageError(input.age)?.let { put(FormField.AGE, it) }
            if (input.country.isNullOrEmpty()) put(FormField.COUNTRY, FieldError.REQUIRED)
            if (input.plan.isNullOrEmpty()) put(FormField.PLAN, FieldError.REQUIRED)
            if (input.date.isNullOrEmpty()) put(FormField.DATE, FieldError.REQUIRED)
            if (input.comments.length > COMMENTS_MAX) put(FormField.COMMENTS, FieldError.TOO_LONG)
            if (!input.acceptTerms) put(FormField.TERMS, FieldError.MUST_ACCEPT)
        }
        return FormValidation(errors)
    }

    private fun lengthError(value: String, min: Int, max: Int): FieldError? = when {
        value.isEmpty() -> FieldError.REQUIRED
        value.length < min -> FieldError.TOO_SHORT
        value.length > max -> FieldError.TOO_LONG
        else -> null
    }

    private fun emailError(value: String): FieldError? = when {
        value.isBlank() -> FieldError.REQUIRED
        !EmailValidator.isValid(value) -> FieldError.INVALID_EMAIL
        else -> null
    }

    private fun ageError(value: String): FieldError? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return FieldError.REQUIRED
        val number = trimmed.toIntOrNull() ?: return FieldError.NOT_A_NUMBER
        return if (number in AGE_MIN..AGE_MAX) null else FieldError.OUT_OF_RANGE
    }
}
