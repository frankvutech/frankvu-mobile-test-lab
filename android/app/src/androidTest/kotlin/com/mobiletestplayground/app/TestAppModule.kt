package com.mobiletestplayground.app

import com.mobiletestplayground.app.di.AppModule
import com.mobiletestplayground.core.data.remote.BackendConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Named

/**
 * Instrumented tests run in the explicit offline/test mode by default, so the UI suites are
 * deterministic without a running backend. Tests that exercise the real backend switch modes in
 * Test Mode (see BackendIntegrationTest), exactly as a tester would.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppModule::class])
object TestAppModule {
    @Provides
    fun uiTiming() = UiTiming()

    @Provides
    fun backendConfig() = BackendConfig(defaultBaseUrl = BuildConfig.API_BASE_URL, useBackendByDefault = false)

    @Provides
    @Named(AppModule.TEST_MODE_ENABLED)
    fun testModeEnabled(): Boolean = true
}
