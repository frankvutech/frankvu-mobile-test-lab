package com.mobiletestplayground.app

/**
 * Every artificial delay the UI uses, in one injectable place. The values are fixed so loading
 * states are observable and identical on every run. Provided by [di.AppModule].
 */
data class UiTiming(
    val loginDelayMillis: Long = 600,
    val dynamicLoadMillis: Long = 1_500,
    val delayedEnableMillis: Long = 3_000,
    val pageLoadMillis: Long = 700,
    val notificationDelayMillis: Long = 5_000,
)
