package com.mobiletestplayground.core.domain.misc

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The four states the Permissions scenario displays. */
enum class PermissionStatus { NOT_REQUESTED, GRANTED, DENIED, PERMANENTLY_DENIED }

/**
 * Android reports permission state only indirectly: whether it is granted, and whether the system
 * would show a rationale. Combined with "have we asked before", that yields the four states.
 *
 * After a first denial `shouldShowRationale` is true (DENIED, can ask again). After a second
 * denial the system stops showing the dialog and `shouldShowRationale` becomes false
 * (PERMANENTLY_DENIED; only Settings can change it).
 */
object PermissionStatusResolver {
    fun resolve(granted: Boolean, shouldShowRationale: Boolean, requestedBefore: Boolean): PermissionStatus = when {
        granted -> PermissionStatus.GRANTED
        !requestedBefore -> PermissionStatus.NOT_REQUESTED
        shouldShowRationale -> PermissionStatus.DENIED
        else -> PermissionStatus.PERMANENTLY_DENIED
    }
}

/** Date arithmetic for the Date & Time scenario, against an injected reference date. */
object DateMath {
    /** The fixed "today" the Date & Time scenario uses so results never depend on the wall clock. */
    val REFERENCE_DATE: LocalDate = LocalDate.of(2025, 1, 15)

    fun daysFromReference(date: LocalDate, reference: LocalDate = REFERENCE_DATE): Long = ChronoUnit.DAYS.between(reference, date)

    fun ageOn(birthDate: LocalDate, onDate: LocalDate = REFERENCE_DATE): Int {
        require(!birthDate.isAfter(onDate)) { "birthDate must not be after onDate" }
        return ChronoUnit.YEARS.between(birthDate, onDate).toInt()
    }
}

/** Gesture names shown by the Gesture Playground, e.g. "Gesture detected: LONG_PRESS". */
enum class GestureType { TAP, DOUBLE_TAP, LONG_PRESS, SWIPE_LEFT, SWIPE_RIGHT, SWIPE_UP, SWIPE_DOWN, DRAG_AND_DROP, PINCH, PAN }

object ZoomRules {
    const val MIN = 1.0f
    const val MAX = 4.0f

    fun clamp(value: Float): Float = value.coerceIn(MIN, MAX)

    /** One-decimal, locale-independent label such as "2.0x". */
    fun label(value: Float): String = "%.1fx".format(java.util.Locale.ROOT, clamp(value))
}

/** Classifies a drag by its dominant axis. Returns null for movements below [threshold]. */
object SwipeClassifier {
    fun classify(dx: Float, dy: Float, threshold: Float): GestureType? {
        val absX = kotlin.math.abs(dx)
        val absY = kotlin.math.abs(dy)
        if (maxOf(absX, absY) < threshold) return null
        return if (absX >= absY) {
            if (dx > 0) GestureType.SWIPE_RIGHT else GestureType.SWIPE_LEFT
        } else {
            if (dy > 0) GestureType.SWIPE_DOWN else GestureType.SWIPE_UP
        }
    }
}
