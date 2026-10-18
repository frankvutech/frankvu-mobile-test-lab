package com.mobiletestplayground.core.data.di

import android.content.Context
import androidx.room.Room
import com.mobiletestplayground.core.data.local.PlaygroundDatabase
import com.mobiletestplayground.core.data.network.NetworkClientFactory
import com.mobiletestplayground.core.data.network.NetworkDelays
import com.mobiletestplayground.core.data.network.PlaygroundApi
import com.mobiletestplayground.core.data.network.RetrofitNetworkPlaygroundRepository
import com.mobiletestplayground.core.data.network.ScenarioInterceptor
import com.mobiletestplayground.core.data.remote.BackendClientProvider
import com.mobiletestplayground.core.data.remote.BackendConfig
import com.mobiletestplayground.core.data.repository.InMemoryCartRepository
import com.mobiletestplayground.core.data.repository.LocalAccountDirectory
import com.mobiletestplayground.core.data.repository.LocalAuthRepository
import com.mobiletestplayground.core.data.repository.RemoteAuthDataSource
import com.mobiletestplayground.core.data.repository.RemoteCartRepository
import com.mobiletestplayground.core.data.repository.RemoteProductRepository
import com.mobiletestplayground.core.data.repository.RoomDynamicItemsRepository
import com.mobiletestplayground.core.data.repository.SeedProductRepository
import com.mobiletestplayground.core.data.repository.SequentialOrderNumberGenerator
import com.mobiletestplayground.core.data.repository.StoredPreferencesRepository
import com.mobiletestplayground.core.data.repository.StoredTestModeRepository
import com.mobiletestplayground.core.data.repository.SwitchingCartRepository
import com.mobiletestplayground.core.data.repository.SwitchingProductRepository
import com.mobiletestplayground.core.data.storage.AppStorage
import com.mobiletestplayground.core.data.storage.DataStoreAppStorage
import com.mobiletestplayground.core.domain.auth.AuthRepository
import com.mobiletestplayground.core.domain.catalog.CartRepository
import com.mobiletestplayground.core.domain.catalog.OrderNumberGenerator
import com.mobiletestplayground.core.domain.catalog.ProductRepository
import com.mobiletestplayground.core.domain.list.DynamicItemsRepository
import com.mobiletestplayground.core.domain.network.NetworkPlaygroundRepository
import com.mobiletestplayground.core.domain.preferences.PreferencesRepository
import com.mobiletestplayground.core.domain.testmode.ResettableStore
import com.mobiletestplayground.core.domain.testmode.TestModeRepository
import com.mobiletestplayground.core.domain.testmode.TestModeSettings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Data-layer wiring. Authentication, catalog and cart use the backend API in backend mode and the
 * local deterministic data in the explicit offline/test mode; Test Mode switches between them.
 * BackendConfig is provided by the app module (from BuildConfig).
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun appStorage(@ApplicationContext context: Context): AppStorage = DataStoreAppStorage(context)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): PlaygroundDatabase =
        Room.databaseBuilder(context, PlaygroundDatabase::class.java, "playground.db").build()

    @Provides
    @Singleton
    fun testMode(storage: AppStorage, backend: BackendConfig) =
        StoredTestModeRepository(storage, TestModeSettings(useBackend = backend.useBackendByDefault))

    @Provides
    @Singleton
    fun backendClient(backend: BackendConfig, testMode: TestModeRepository) =
        BackendClientProvider(backend, testMode, NetworkClientFactory.json)

    @Provides
    @Singleton
    fun localAccounts() = LocalAccountDirectory()

    @Provides
    @Singleton
    fun auth(storage: AppStorage, local: LocalAccountDirectory, client: BackendClientProvider, testMode: TestModeRepository) =
        LocalAuthRepository(storage, local, RemoteAuthDataSource(client)) { testMode.settings.value.useBackend }

    @Provides
    @Singleton
    fun preferences(storage: AppStorage) = StoredPreferencesRepository(storage)

    @Provides
    @Singleton
    fun dynamicItems(database: PlaygroundDatabase) = RoomDynamicItemsRepository(database.dynamicItems())

    @Provides
    @Singleton
    fun orderNumbers() = SequentialOrderNumberGenerator()

    @Provides
    @Singleton
    fun localCart(orderNumbers: SequentialOrderNumberGenerator) = InMemoryCartRepository(orderNumbers)

    @Provides
    @Singleton
    fun remoteCart(client: BackendClientProvider, auth: LocalAuthRepository) = RemoteCartRepository(client) { auth.token }

    @Provides
    @Singleton
    fun cart(
        local: InMemoryCartRepository,
        remote: RemoteCartRepository,
        testMode: TestModeRepository,
        @ApplicationScope scope: CoroutineScope,
    ): CartRepository {
        val useBackend = testMode.settings.map { it.useBackend }.stateIn(scope, SharingStarted.Eagerly, testMode.settings.value.useBackend)
        return SwitchingCartRepository(local, remote, useBackend, scope)
    }

    @Provides
    @Singleton
    fun products(client: BackendClientProvider, testMode: TestModeRepository): ProductRepository =
        SwitchingProductRepository(SeedProductRepository(), RemoteProductRepository(client)) { testMode.settings.value.useBackend }

    @Provides
    fun networkDelays() = NetworkDelays()

    @Provides
    @Singleton
    fun playgroundApi(testMode: TestModeRepository, delays: NetworkDelays): PlaygroundApi =
        NetworkClientFactory.create(ScenarioInterceptor(testMode, delays, NetworkClientFactory.json))

    @Provides
    @Singleton
    fun networkRepository(api: PlaygroundApi): NetworkPlaygroundRepository = RetrofitNetworkPlaygroundRepository(api)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindings {
    @Binds abstract fun auth(impl: LocalAuthRepository): AuthRepository

    @Binds abstract fun testMode(impl: StoredTestModeRepository): TestModeRepository

    @Binds abstract fun preferences(impl: StoredPreferencesRepository): PreferencesRepository

    @Binds abstract fun dynamicItems(impl: RoomDynamicItemsRepository): DynamicItemsRepository

    @Binds abstract fun orderNumbers(impl: SequentialOrderNumberGenerator): OrderNumberGenerator

    // Every store "Reset application state" restores.
    @Binds @IntoSet
    abstract fun resettableAuth(impl: LocalAuthRepository): ResettableStore

    @Binds @IntoSet
    abstract fun resettableTestMode(impl: StoredTestModeRepository): ResettableStore

    @Binds @IntoSet
    abstract fun resettablePreferences(impl: StoredPreferencesRepository): ResettableStore

    @Binds @IntoSet
    abstract fun resettableItems(impl: RoomDynamicItemsRepository): ResettableStore

    @Binds @IntoSet
    abstract fun resettableCart(impl: InMemoryCartRepository): ResettableStore

    @Binds @IntoSet
    abstract fun resettableRemoteCart(impl: RemoteCartRepository): ResettableStore

    @Binds @IntoSet
    abstract fun resettableOrders(impl: SequentialOrderNumberGenerator): ResettableStore
}
