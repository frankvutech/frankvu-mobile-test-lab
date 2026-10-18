package com.mobiletestplayground.core.data

import com.mobiletestplayground.core.data.network.NetworkClientFactory
import com.mobiletestplayground.core.data.network.NetworkDelays
import com.mobiletestplayground.core.data.network.RetrofitNetworkPlaygroundRepository
import com.mobiletestplayground.core.data.network.ScenarioInterceptor
import com.mobiletestplayground.core.data.repository.InMemoryCartRepository
import com.mobiletestplayground.core.data.repository.LocalAccountDirectory
import com.mobiletestplayground.core.data.repository.LocalAuthRepository
import com.mobiletestplayground.core.data.repository.SequentialOrderNumberGenerator
import com.mobiletestplayground.core.data.repository.StoredTestModeRepository
import com.mobiletestplayground.core.data.storage.InMemoryAppStorage
import com.mobiletestplayground.core.domain.auth.LoginUseCase
import com.mobiletestplayground.core.domain.auth.RegistrationOutcome
import com.mobiletestplayground.core.domain.catalog.CheckoutResult
import com.mobiletestplayground.core.domain.catalog.CheckoutUseCase
import com.mobiletestplayground.core.domain.network.NetworkError
import com.mobiletestplayground.core.domain.network.NetworkResult
import com.mobiletestplayground.core.domain.network.NetworkScenario
import com.mobiletestplayground.core.domain.seed.SeedData
import com.mobiletestplayground.core.domain.testmode.ResetApplicationStateUseCase
import com.mobiletestplayground.core.domain.testmode.TestModeSettings
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** An offline-mode auth repository: local accounts, no backend. */
fun offlineAuth(storage: InMemoryAppStorage) = LocalAuthRepository(storage, LocalAccountDirectory(), LocalAccountDirectory()) { false }

class RememberMeTest {
    private val storage = InMemoryAppStorage()

    /** A new repository over the same storage models a process restart. */
    private fun restartProcess() = offlineAuth(storage)

    @Test
    fun `remembered session survives a process restart`() = runTest {
        LoginUseCase(offlineAuth(storage))("test@example.com", "Password123!", rememberMe = true)

        val restarted = restartProcess().apply { restoreSession() }

        assertEquals("test@example.com", restarted.session.value?.user?.email)
        assertEquals("test@example.com", restarted.rememberedEmail())
    }

    @Test
    fun `non remembered session does not survive a process restart`() = runTest {
        val repository = offlineAuth(storage)
        LoginUseCase(repository)("test@example.com", "Password123!", rememberMe = false)
        assertNotNull(repository.session.value)

        val restarted = restartProcess().apply { restoreSession() }

        assertNull(restarted.session.value)
        assertNull(restarted.rememberedEmail())
    }

    @Test
    fun `logout forgets the session but keeps the remembered email`() = runTest {
        val repository = offlineAuth(storage)
        LoginUseCase(repository)("qa@example.com", "QaPassword123!", rememberMe = true)
        repository.endSession()

        val restarted = restartProcess().apply { restoreSession() }

        assertNull(restarted.session.value)
        assertEquals("qa@example.com", restarted.rememberedEmail())
    }

    @Test
    fun `clearing storage removes everything`() = runTest {
        LoginUseCase(offlineAuth(storage))("test@example.com", "Password123!", rememberMe = true)
        storage.clear()

        val restarted = restartProcess().apply { restoreSession() }

        assertNull(restarted.session.value)
        assertNull(restarted.rememberedEmail())
    }

    @Test
    fun `registered accounts are not persisted across restarts`() = runTest {
        offlineAuth(storage).register("Ann", "ann@example.com", "Passw0rd!")
        val check = restartProcess().checkCredentials("ann@example.com", "Passw0rd!")
        assertEquals(com.mobiletestplayground.core.domain.auth.CredentialCheck.UnknownUser, check)
    }
}

class ResetApplicationStateTest {
    @Test
    fun `reset restores seed state in every store`() = runTest {
        val storage = InMemoryAppStorage()
        val auth = offlineAuth(storage)
        val testMode = StoredTestModeRepository(storage)
        val orders = SequentialOrderNumberGenerator()
        val cart = InMemoryCartRepository(orders)

        LoginUseCase(auth)("test@example.com", "Password123!", rememberMe = true)
        auth.register("Ann", "ann@example.com", "Passw0rd!")
        testMode.update { it.copy(simulateNetworkError = true) }
        cart.add(SeedData.products.first())
        cart.checkout()
        cart.add(SeedData.products.first())

        ResetApplicationStateUseCase(setOf(auth, testMode, cart, orders))()

        assertNull(auth.session.value)
        assertNull(auth.rememberedEmail())
        assertEquals(RegistrationOutcome.Registered::class, auth.register("Ann", "ann@example.com", "Passw0rd!")::class)
        assertEquals(TestModeSettings(), testMode.settings.value)
        assertEquals(emptyList(), cart.lines.value)
        assertEquals("ORDER-0001", orders.next())
    }
}

class CheckoutTest {
    @Test
    fun `checkout totals the cart, empties it and numbers orders sequentially`() = runTest {
        val cart = InMemoryCartRepository(SequentialOrderNumberGenerator())
        val checkout = CheckoutUseCase(cart)
        assertEquals(CheckoutResult.EmptyCart, checkout())

        cart.add(SeedData.products[0])
        cart.add(SeedData.products[0])
        cart.add(SeedData.products[1])
        assertEquals(2, cart.lines.value.first().quantity)

        val expected = SeedData.products[0].priceCents * 2 + SeedData.products[1].priceCents
        assertEquals(CheckoutResult.Placed("ORDER-0001", expected), checkout())
        assertEquals(emptyList(), cart.lines.value)
        cart.add(SeedData.products[2])
        assertEquals("ORDER-0002", (checkout() as CheckoutResult.Placed).orderNumber)
    }
}

class NetworkRepositoryTest {
    private val testMode = StoredTestModeRepository(InMemoryAppStorage())
    private val repository = RetrofitNetworkPlaygroundRepository(
        NetworkClientFactory.create(ScenarioInterceptor(testMode, NetworkDelays(0, 0, 0), NetworkClientFactory.json)),
    )

    private suspend fun fetch(scenario: NetworkScenario) = repository.fetchProducts(scenario)

    @Test
    fun `each scenario maps to a predictable result`() = runTest {
        val success = fetch(NetworkScenario.SUCCESS)
        assertIs<NetworkResult.Success<*>>(success)
        assertEquals(5, (success as NetworkResult.Success).data.size)
        assertIs<NetworkResult.Success<*>>(fetch(NetworkScenario.SLOW))
        assertEquals(NetworkResult.Empty, fetch(NetworkScenario.EMPTY))
        assertEquals(NetworkResult.Failure(NetworkError.Timeout), fetch(NetworkScenario.TIMEOUT))
        assertEquals(NetworkResult.Failure(NetworkError.BadRequest), fetch(NetworkScenario.BAD_REQUEST))
        assertEquals(NetworkResult.Failure(NetworkError.Unauthorized), fetch(NetworkScenario.UNAUTHORIZED))
        assertEquals(NetworkResult.Failure(NetworkError.Forbidden), fetch(NetworkScenario.FORBIDDEN))
        assertEquals(NetworkResult.Failure(NetworkError.NotFound), fetch(NetworkScenario.NOT_FOUND))
        assertEquals(NetworkResult.Failure(NetworkError.Server(500)), fetch(NetworkScenario.SERVER_ERROR))
        assertEquals(NetworkResult.Failure(NetworkError.MalformedResponse), fetch(NetworkScenario.MALFORMED))
    }

    @Test
    fun `test mode network error overrides every scenario`() = runTest {
        testMode.update { it.copy(simulateNetworkError = true) }
        assertEquals(NetworkResult.Failure(NetworkError.Connectivity), fetch(NetworkScenario.SUCCESS))
    }
}
