package com.mobiletestplayground.app.di

import com.mobiletestplayground.app.BuildConfig
import com.mobiletestplayground.app.UiTiming
import com.mobiletestplayground.core.data.remote.BackendConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    const val TEST_MODE_ENABLED = "testModeEnabled"

    @Provides
    fun uiTiming() = UiTiming()

    @Provides
    fun backendConfig() = BackendConfig(defaultBaseUrl = BuildConfig.API_BASE_URL, useBackendByDefault = BuildConfig.USE_BACKEND_BY_DEFAULT)

    /** Build-time gate for destructive Test Mode actions: true in debug, false in release by default. */
    @Provides
    @Named(TEST_MODE_ENABLED)
    fun testModeEnabled(): Boolean = BuildConfig.ENABLE_TEST_MODE
}
