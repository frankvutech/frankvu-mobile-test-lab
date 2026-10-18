package com.mobiletestplayground.app

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.mobiletestplayground.app.auth.LoginViewModel
import com.mobiletestplayground.app.home.ScenarioFilter
import com.mobiletestplayground.app.navigation.DeepLinkRouter
import com.mobiletestplayground.app.navigation.Routes
import com.mobiletestplayground.app.navigation.Scenario
import com.mobiletestplayground.app.scenarios.controls.ControlsViewModel
import com.mobiletestplayground.app.scenarios.errors.inlineError
import com.mobiletestplayground.app.scenarios.forms.FormsViewModel
import com.mobiletestplayground.app.scenarios.lists.InfiniteListViewModel
import com.mobiletestplayground.app.scenarios.network.NetworkUiState
import com.mobiletestplayground.app.scenarios.network.NetworkViewModel
import com.mobiletestplayground.core.data.repository.LocalAccountDirectory
import com.mobiletestplayground.core.data.repository.LocalAuthRepository
import com.mobiletestplayground.core.data.repository.StoredTestModeRepository
import com.mobiletestplayground.core.data.storage.InMemoryAppStorage
import com.mobiletestplayground.core.domain.access.AccessLevel
import com.mobiletestplayground.core.domain.auth.LoginError
import com.mobiletestplayground.core.domain.auth.LoginUseCase
import com.mobiletestplayground.core.domain.form.FieldError
import com.mobiletestplayground.core.domain.form.FormField
import com.mobiletestplayground.core.domain.network.NetworkError
import com.mobiletestplayground.core.domain.network.NetworkPlaygroundRepository
import com.mobiletestplayground.core.domain.network.NetworkResult
import com.mobiletestplayground.core.domain.network.NetworkScenario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
abstract class MainDispatcherTest {
    protected val dispatcher = StandardTestDispatcher()

    @Before
    fun setMain() = Dispatchers.setMain(dispatcher)

    @After
    fun resetMain() = Dispatchers.resetMain()
}

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest : MainDispatcherTest() {
    private val timing = UiTiming(loginDelayMillis = 600)
    private val storage = InMemoryAppStorage()
    private val repository = LocalAuthRepository(storage, LocalAccountDirectory(), LocalAccountDirectory()) { false }
    private val testMode = StoredTestModeRepository(storage)

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        LoginViewModel(LoginUseCase(repository), repository, timing, handle, testMode)

    @Test
    fun `shows loading for the fixed delay, then the error`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEmailChange("test@example.com")
        vm.onPasswordChange("wrong")
        vm.submit()
        assertTrue(vm.state.value.isLoading)
        advanceTimeBy(timing.loginDelayMillis - 1)
        assertTrue(vm.state.value.isLoading)
        advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
        assertEquals(LoginError.INCORRECT_PASSWORD, vm.state.value.error)
    }

    @Test
    fun `editing clears the error and success clears the password`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.submit()
        advanceUntilIdle()
        assertEquals(LoginError.EMPTY_EMAIL, vm.state.value.error)
        vm.onEmailChange("t")
        assertNull(vm.state.value.error)

        vm.onEmailChange("test@example.com")
        vm.onPasswordChange("Password123!")
        vm.submit()
        advanceUntilIdle()
        assertTrue(vm.state.value.loggedIn)
        assertEquals("", vm.state.value.password)
    }

    @Test
    fun `guest entry is offered only when test mode disables authentication`() = runTest(dispatcher) {
        val vm = viewModel()
        assertTrue(vm.authenticationRequired.value)
        testMode.update { it.copy(authenticationRequired = false) }
        advanceUntilIdle()
        assertFalse(vm.authenticationRequired.value)
    }

    @Test
    fun `remembered email prefills the form`() = runTest(dispatcher) {
        LoginUseCase(repository)("qa@example.com", "QaPassword123!", rememberMe = true)
        repository.endSession()
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals("qa@example.com", vm.state.value.email)
        assertTrue(vm.state.value.rememberMe)
    }

    @Test
    fun `email survives process death through SavedStateHandle but the password does not`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        viewModel(handle).apply {
            onEmailChange("typed@example.com")
            onPasswordChange("secret")
        }
        val restored = viewModel(handle)
        advanceUntilIdle()
        assertEquals("typed@example.com", restored.state.value.email)
        assertEquals("", restored.state.value.password)
    }
}

class FormsViewModelTest {
    @Test
    fun `errors appear after submit and update live`() {
        val vm = FormsViewModel()
        vm.edit { it.copy(email = "bad") }
        assertTrue(vm.state.value.errors.isEmpty(), "no errors before the first submit")
        vm.submit()
        assertEquals(FieldError.INVALID_EMAIL, vm.state.value.errors[FormField.EMAIL])
        vm.edit { it.copy(email = "good@example.com") }
        assertNull(vm.state.value.errors[FormField.EMAIL])
        assertFalse(vm.state.value.submitted)
    }

    @Test
    fun `valid form submits and reset restores the initial state`() {
        val vm = FormsViewModel()
        vm.edit {
            it.copy(
                fullName = "Ada",
                email = "ada@example.com",
                password = "password1",
                age = "30",
                country = "de",
                plan = "pro",
                date = "2025-01-15",
                acceptTerms = true,
            )
        }
        vm.submit()
        assertTrue(vm.state.value.submitted)
        vm.reset()
        assertEquals(FormsViewModel().state.value, vm.state.value)
    }
}

class ControlsViewModelTest {
    @Test
    fun `stepper is bounded and progress caps at 100`() {
        val vm = ControlsViewModel(SavedStateHandle())
        vm.step(-1)
        assertEquals(0, vm.stepper.value)
        repeat(15) { vm.step(1) }
        assertEquals(ControlsViewModel.STEPPER_MAX, vm.stepper.value)
        repeat(6) { vm.advanceProgress() }
        assertEquals(100, vm.progress.value)
    }

    @Test
    fun `state is restored from SavedStateHandle`() {
        val handle = SavedStateHandle()
        ControlsViewModel(handle).apply {
            setSwitch(true)
            setRadio("large")
        }
        val restored = ControlsViewModel(handle)
        assertTrue(restored.switch.value)
        assertEquals("large", restored.radio.value)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class InfiniteListViewModelTest : MainDispatcherTest() {
    @Test
    fun `loads pages until the end`() = runTest(dispatcher) {
        val vm = InfiniteListViewModel(UiTiming(pageLoadMillis = 10))
        advanceUntilIdle()
        assertEquals(20, vm.state.value.items.size)
        repeat(10) {
            vm.loadMore()
            advanceUntilIdle()
        }
        assertEquals(100, vm.state.value.items.size)
        assertTrue(vm.state.value.endReached)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkViewModelTest : MainDispatcherTest() {
    private val repository = object : NetworkPlaygroundRepository {
        var calls = 0
        override suspend fun fetchProducts(scenario: NetworkScenario) = when (scenario) {
            NetworkScenario.EMPTY -> NetworkResult.Empty
            else -> {
                calls++
                NetworkResult.Failure(NetworkError.Server(500))
            }
        }
    }

    @Test
    fun `loading then error, and retry repeats the last scenario`() = runTest(dispatcher) {
        val vm = NetworkViewModel(repository)
        vm.state.test {
            assertEquals(NetworkUiState.Idle, awaitItem())
            vm.load(NetworkScenario.SERVER_ERROR)
            assertEquals(NetworkUiState.Loading(NetworkScenario.SERVER_ERROR), awaitItem())
            assertEquals(NetworkUiState.Error(NetworkError.Server(500)), awaitItem())
            vm.retry()
            assertEquals(NetworkUiState.Loading(NetworkScenario.SERVER_ERROR), awaitItem())
            assertEquals(NetworkUiState.Error(NetworkError.Server(500)), awaitItem())
            vm.load(NetworkScenario.EMPTY)
            assertEquals(NetworkUiState.Loading(NetworkScenario.EMPTY), awaitItem())
            assertEquals(NetworkUiState.Empty, awaitItem())
        }
        assertEquals(2, repository.calls)
    }
}

class NavigationRulesTest {
    @Test
    fun `route access levels`() {
        assertEquals(AccessLevel.PUBLIC, Routes.accessLevel(Routes.LOGIN))
        assertEquals(AccessLevel.PUBLIC, Routes.accessLevel(Routes.TEST_MODE))
        assertEquals(AccessLevel.ADMIN, Routes.accessLevel(Routes.ADMIN_USERS))
        assertEquals(AccessLevel.AUTHENTICATED, Routes.accessLevel(Routes.HOME))
        assertEquals(AccessLevel.AUTHENTICATED, Routes.accessLevel(Scenario.FORMS.route))
    }

    @Test
    fun `deep links route to scenarios and reject unknown ids`() {
        assertEquals("scenario/forms", DeepLinkRouter.routeFor("mtp://playground/scenario/forms"))
        assertEquals(Routes.TEST_MODE, DeepLinkRouter.routeFor("mtp://playground/scenario/test_mode"))
        assertEquals("product/product_003", DeepLinkRouter.routeFor("mtp://playground/product/product_003"))
        assertEquals("nav/detail/3", DeepLinkRouter.routeFor("mtp://playground/detail/3"))
        assertNull(DeepLinkRouter.routeFor("mtp://playground/scenario/unknown"))
    }

    @Test
    fun `there are 20 scenarios with unique ids`() {
        assertEquals(20, Scenario.entries.size)
        assertEquals(20, Scenario.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `scenario search matches localized titles and ids`() {
        val titles = mapOf(Scenario.FORMS to "Formulare", Scenario.LISTS to "Listen")
        val items = titles.keys.toList()
        assertEquals(listOf(Scenario.FORMS), ScenarioFilter.filter(items, "form", { titles.getValue(it) }, { it.id }))
        assertEquals(listOf(Scenario.LISTS), ScenarioFilter.filter(items, "listen", { titles.getValue(it) }, { it.id }))
        assertEquals(items, ScenarioFilter.filter(items, " ", { titles.getValue(it) }, { it.id }))
    }

    @Test
    fun `inline validation`() {
        assertNull(inlineError(""))
        assertNull(inlineError("12345"))
        assertEquals(R.string.errors_inline_digits, inlineError("12a"))
        assertEquals(R.string.errors_inline_length, inlineError("123456"))
    }
}
