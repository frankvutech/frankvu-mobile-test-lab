package com.mobiletestplayground.core.domain.form

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormValidatorTest {
    private val valid = FormInput(
        fullName = "Ada Lovelace",
        email = "ada@example.com",
        password = "secret123",
        age = "36",
        country = "de",
        plan = "pro",
        acceptTerms = true,
        date = "2025-01-15",
    )

    private fun errorFor(input: FormInput, field: FormField) = FormValidator.validate(input).errors[field]

    @Test
    fun `a complete form is valid`() {
        assertTrue(FormValidator.validate(valid).isValid)
    }

    @Test
    fun `an empty form reports every required field`() {
        val errors = FormValidator.validate(FormInput()).errors
        assertEquals(
            mapOf(
                FormField.FULL_NAME to FieldError.REQUIRED,
                FormField.EMAIL to FieldError.REQUIRED,
                FormField.PASSWORD to FieldError.REQUIRED,
                FormField.AGE to FieldError.REQUIRED,
                FormField.COUNTRY to FieldError.REQUIRED,
                FormField.PLAN to FieldError.REQUIRED,
                FormField.DATE to FieldError.REQUIRED,
                FormField.TERMS to FieldError.MUST_ACCEPT,
            ),
            errors,
        )
    }

    @Test
    fun `length boundaries`() {
        assertEquals(FieldError.TOO_SHORT, errorFor(valid.copy(fullName = "A"), FormField.FULL_NAME))
        assertEquals(null, errorFor(valid.copy(fullName = "Al"), FormField.FULL_NAME))
        assertEquals(null, errorFor(valid.copy(fullName = "a".repeat(40)), FormField.FULL_NAME))
        assertEquals(FieldError.TOO_LONG, errorFor(valid.copy(fullName = "a".repeat(41)), FormField.FULL_NAME))
        assertEquals(FieldError.TOO_SHORT, errorFor(valid.copy(password = "1234567"), FormField.PASSWORD))
        assertEquals(FieldError.TOO_LONG, errorFor(valid.copy(password = "x".repeat(21)), FormField.PASSWORD))
        assertEquals(FieldError.TOO_LONG, errorFor(valid.copy(comments = "x".repeat(201)), FormField.COMMENTS))
    }

    @Test
    fun `numeric and email rules`() {
        assertEquals(FieldError.NOT_A_NUMBER, errorFor(valid.copy(age = "abc"), FormField.AGE))
        assertEquals(FieldError.NOT_A_NUMBER, errorFor(valid.copy(age = "12.5"), FormField.AGE))
        assertEquals(FieldError.OUT_OF_RANGE, errorFor(valid.copy(age = "17"), FormField.AGE))
        assertEquals(null, errorFor(valid.copy(age = "18"), FormField.AGE))
        assertEquals(FieldError.OUT_OF_RANGE, errorFor(valid.copy(age = "121"), FormField.AGE))
        assertEquals(FieldError.INVALID_EMAIL, errorFor(valid.copy(email = "ada@"), FormField.EMAIL))
    }
}
