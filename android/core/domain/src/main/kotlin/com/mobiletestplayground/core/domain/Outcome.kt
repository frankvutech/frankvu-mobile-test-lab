package com.mobiletestplayground.core.domain

/** Why a data operation failed, independent of whether the data came from the backend or local storage. */
enum class FailureReason {
    /** The backend could not be reached (connection refused, timeout). */
    UNAVAILABLE,

    /** The backend rejected the session (missing, expired or revoked token). */
    UNAUTHORIZED,
    NOT_FOUND,

    /** Stock, duplicates and similar business conflicts (HTTP 409). */
    CONFLICT,
    UNKNOWN,
}

sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val reason: FailureReason) : Outcome<Nothing>
}
