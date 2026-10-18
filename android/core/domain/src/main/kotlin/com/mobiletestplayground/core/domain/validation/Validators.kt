package com.mobiletestplayground.core.domain.validation

/**
 * A deliberately simple, documented email rule: `local@domain.tld`, no whitespace, a dot in the
 * domain and a TLD of at least two letters. Tests can rely on exactly this behaviour.
 */
object EmailValidator {
    private val pattern = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")

    fun isValid(email: String): Boolean = pattern.matches(email.trim())
}

/** Password strength rules used by registration and password reset. */
object PasswordPolicy {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64

    enum class Violation { TOO_SHORT, TOO_LONG, MISSING_UPPERCASE, MISSING_DIGIT, MISSING_SPECIAL }

    /** Returns the first violated rule, or null when the password is acceptable. */
    fun firstViolation(password: String): Violation? = when {
        password.length < MIN_LENGTH -> Violation.TOO_SHORT
        password.length > MAX_LENGTH -> Violation.TOO_LONG
        password.none { it.isUpperCase() } -> Violation.MISSING_UPPERCASE
        password.none { it.isDigit() } -> Violation.MISSING_DIGIT
        password.all { it.isLetterOrDigit() } -> Violation.MISSING_SPECIAL
        else -> null
    }
}
