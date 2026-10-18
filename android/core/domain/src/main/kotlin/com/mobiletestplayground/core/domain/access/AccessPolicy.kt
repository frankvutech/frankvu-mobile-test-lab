package com.mobiletestplayground.core.domain.access

import com.mobiletestplayground.core.domain.model.Role
import com.mobiletestplayground.core.domain.model.Session

/** What a destination requires before it may be shown. */
enum class AccessLevel { PUBLIC, AUTHENTICATED, ADMIN }

sealed interface AccessDecision {
    data object Granted : AccessDecision

    /** Not signed in: redirect to login, then continue to the requested destination. */
    data object RequiresLogin : AccessDecision

    /** Signed in but lacking the role: show the access-denied state. */
    data object Denied : AccessDecision
}

/**
 * The single authorization rule for both navigation and deep links.
 *
 * [authenticationRequired] comes from Test Mode. Turning it off lets guests reach
 * [AccessLevel.AUTHENTICATED] screens for scenario testing; admin screens always need an admin.
 */
object AccessPolicy {
    fun decide(level: AccessLevel, session: Session?, authenticationRequired: Boolean = true): AccessDecision = when (level) {
        AccessLevel.PUBLIC -> AccessDecision.Granted
        AccessLevel.AUTHENTICATED -> when {
            session != null || !authenticationRequired -> AccessDecision.Granted
            else -> AccessDecision.RequiresLogin
        }
        AccessLevel.ADMIN -> when {
            session == null -> AccessDecision.RequiresLogin
            session.user.role == Role.ADMIN -> AccessDecision.Granted
            else -> AccessDecision.Denied
        }
    }
}
