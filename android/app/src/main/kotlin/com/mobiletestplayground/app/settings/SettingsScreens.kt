package com.mobiletestplayground.app.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mobiletestplayground.app.BuildConfig
import com.mobiletestplayground.app.R
import com.mobiletestplayground.app.di.AppModule
import com.mobiletestplayground.core.data.ClearLocalStorageUseCase
import com.mobiletestplayground.core.data.remote.BackendClientProvider
import com.mobiletestplayground.core.domain.auth.LogoutUseCase
import com.mobiletestplayground.core.domain.catalog.CartRepository
import com.mobiletestplayground.core.domain.list.DynamicItemsRepository
import com.mobiletestplayground.core.domain.list.GenerateTestDataUseCase
import com.mobiletestplayground.core.domain.preferences.PreferencesRepository
import com.mobiletestplayground.core.domain.preferences.ThemeMode
import com.mobiletestplayground.core.domain.preferences.UserPreferences
import com.mobiletestplayground.core.domain.testmode.ResetApplicationStateUseCase
import com.mobiletestplayground.core.domain.testmode.TestModeRepository
import com.mobiletestplayground.core.domain.testmode.TestModeSettings
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.LabeledSwitch
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ScreenColumn
import com.mobiletestplayground.core.ui.components.SectionTitle
import com.mobiletestplayground.core.ui.components.ValueRow
import com.mobiletestplayground.core.ui.components.textTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    testModeRepository: TestModeRepository,
    private val backendClient: BackendClientProvider,
    @param:Named(AppModule.TEST_MODE_ENABLED) val testModeEnabled: Boolean,
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
    val testMode: StateFlow<TestModeSettings> = testModeRepository.settings

    fun backendUrl(): String = backendClient.baseUrl()

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onTestMode: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    PlaygroundScaffold(stringResource(R.string.settings_title), TestTags.SETTINGS_SCREEN, onBack) { padding ->
        ScreenColumn(padding) {
            SectionTitle(R.string.settings_theme)
            androidx.compose.foundation.layout.Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = preferences.themeMode == mode, role = Role.RadioButton, onClick = {
                                viewModel.setTheme(mode)
                            })
                            .testTag(TestTags.settingsTheme(mode.name.lowercase())),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = preferences.themeMode == mode, onClick = null)
                        Text(stringResource(mode.label()))
                    }
                }
            }
            SectionTitle(R.string.scenario_test_mode)
            if (viewModel.testModeEnabled) {
                Button(onClick = onTestMode, modifier = Modifier.fillMaxWidth().testTag(TestTags.SETTINGS_TEST_MODE)) {
                    Text(stringResource(R.string.settings_open_test_mode))
                }
            } else {
                Text(stringResource(R.string.test_mode_disabled), modifier = Modifier.testTag(TestTags.SETTINGS_TEST_MODE_UNAVAILABLE))
            }
            val testMode by viewModel.testMode.collectAsStateWithLifecycle()
            ValueRow(
                stringResource(R.string.settings_data_source),
                if (testMode.useBackend) {
                    stringResource(R.string.data_source_backend) + " · " + viewModel.backendUrl()
                } else {
                    stringResource(R.string.data_source_offline)
                },
                TestTags.SETTINGS_DATA_SOURCE,
            )
            ValueRow(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME, TestTags.SETTINGS_VERSION)
        }
    }
}

@Composable
private fun BackendSection(settings: TestModeSettings, viewModel: TestModeViewModel) {
    SectionTitle(R.string.test_mode_backend)
    Text(
        stringResource(R.string.test_mode_backend_hint),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag(textTag(R.string.test_mode_backend_hint)),
    )
    LabeledSwitch(
        stringResource(R.string.test_mode_use_backend),
        settings.useBackend,
        viewModel::setUseBackend,
        TestTags.TEST_MODE_USE_BACKEND,
    )
    var url by rememberSaveable(settings.backendUrlOverride) { mutableStateOf(settings.backendUrlOverride.orEmpty()) }
    OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text(stringResource(R.string.test_mode_backend_url)) },
        supportingText = { Text(stringResource(R.string.test_mode_backend_url_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.TEST_MODE_BACKEND_URL),
    )
    OutlinedButton(onClick = { viewModel.setBackendUrl(url) }, modifier = Modifier.testTag(TestTags.TEST_MODE_BACKEND_URL_SAVE)) {
        Text(stringResource(R.string.action_save))
    }
    ValueRow(stringResource(R.string.test_mode_backend_active_url), viewModel.activeBackendUrl(), TestTags.TEST_MODE_BACKEND_ACTIVE_URL)
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

enum class TestModeAction { RESET_APP, CLEAR_STORAGE, RESET_DATA, GENERATE_DATA }

@HiltViewModel
class TestModeViewModel @Inject constructor(
    private val testModeRepository: TestModeRepository,
    private val resetApplicationState: ResetApplicationStateUseCase,
    private val clearLocalStorage: ClearLocalStorageUseCase,
    private val generateTestData: GenerateTestDataUseCase,
    private val dynamicItems: DynamicItemsRepository,
    private val cart: CartRepository,
    private val logout: LogoutUseCase,
    private val backendClient: BackendClientProvider,
    @param:Named(AppModule.TEST_MODE_ENABLED) val enabled: Boolean,
) : ViewModel() {
    val settings: StateFlow<TestModeSettings> = testModeRepository.settings

    /** The URL backend calls currently go to (override or build default). */
    fun activeBackendUrl(): String = backendClient.baseUrl()

    /**
     * Switching between the backend and the offline/test mode signs out: a session (and token)
     * from one source means nothing to the other.
     */
    fun setUseBackend(value: Boolean) {
        if (!enabled) return
        viewModelScope.launch {
            testModeRepository.update { it.copy(useBackend = value) }
            logout()
        }
    }

    fun setBackendUrl(value: String) {
        if (!enabled) return
        viewModelScope.launch { testModeRepository.update { it.copy(backendUrlOverride = value.trim().ifEmpty { null }) } }
    }
    private val mutableLastAction = MutableStateFlow<TestModeAction?>(null)
    val lastAction: StateFlow<TestModeAction?> = mutableLastAction.asStateFlow()

    fun update(transform: (TestModeSettings) -> TestModeSettings) {
        if (!enabled) return
        viewModelScope.launch { testModeRepository.update(transform) }
    }

    fun perform(action: TestModeAction) {
        // Defence in depth: the screen hides these actions too, but a disabled build never runs them.
        if (!enabled) return
        viewModelScope.launch {
            // Resetting signs out, which navigates away and clears this ViewModel. NonCancellable lets
            // every store finish resetting instead of stopping at the first one.
            withContext(NonCancellable) { execute(action) }
            mutableLastAction.value = action
        }
    }

    private suspend fun execute(action: TestModeAction) {
        when (action) {
            TestModeAction.RESET_APP -> resetApplicationState()
            TestModeAction.CLEAR_STORAGE -> clearLocalStorage()
            TestModeAction.RESET_DATA -> {
                dynamicItems.reset()
                cart.clear()
            }
            TestModeAction.GENERATE_DATA -> generateTestData()
        }
    }
}

@Composable
fun TestModeScreen(onBack: () -> Unit, viewModel: TestModeViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lastAction by viewModel.lastAction.collectAsStateWithLifecycle()
    PlaygroundScaffold(stringResource(R.string.scenario_test_mode), TestTags.TEST_MODE_SCREEN, onBack) { padding ->
        ScreenColumn(padding) {
            if (!viewModel.enabled) {
                Text(stringResource(R.string.test_mode_disabled), modifier = Modifier.testTag(TestTags.TEST_MODE_DISABLED))
                return@ScreenColumn
            }
            Card {
                Text(
                    stringResource(R.string.test_mode_warning),
                    modifier = Modifier.padding(16.dp).testTag(textTag(R.string.test_mode_warning)),
                )
            }
            BackendSection(settings, viewModel)
            SectionTitle(R.string.test_mode_simulation)
            LabeledSwitch(
                stringResource(R.string.test_mode_network_error),
                settings.simulateNetworkError,
                { value -> viewModel.update { it.copy(simulateNetworkError = value) } },
                TestTags.TEST_MODE_NETWORK_ERROR,
            )
            LabeledSwitch(
                stringResource(R.string.test_mode_slow_network),
                settings.simulateSlowNetwork,
                { value -> viewModel.update { it.copy(simulateSlowNetwork = value) } },
                TestTags.TEST_MODE_SLOW_NETWORK,
            )
            LabeledSwitch(
                stringResource(R.string.test_mode_auth_required),
                settings.authenticationRequired,
                { value -> viewModel.update { it.copy(authenticationRequired = value) } },
                TestTags.TEST_MODE_AUTH_REQUIRED,
            )
            SectionTitle(R.string.test_mode_data)
            ActionButton(R.string.test_mode_generate_data, TestTags.TEST_MODE_GENERATE_DATA) {
                viewModel.perform(TestModeAction.GENERATE_DATA)
            }
            ActionButton(R.string.test_mode_reset_data, TestTags.TEST_MODE_RESET_DATA) { viewModel.perform(TestModeAction.RESET_DATA) }
            ActionButton(R.string.test_mode_clear_storage, TestTags.TEST_MODE_CLEAR_STORAGE, destructive = true) {
                viewModel.perform(TestModeAction.CLEAR_STORAGE)
            }
            ActionButton(R.string.test_mode_reset_app, TestTags.TEST_MODE_RESET_APP, destructive = true) {
                viewModel.perform(TestModeAction.RESET_APP)
            }
            lastAction?.let { action ->
                Text(
                    stringResource(action.doneMessage()),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .testTag(TestTags.TEST_MODE_STATUS)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun ActionButton(label: Int, tag: String, destructive: Boolean = false, onClick: () -> Unit) {
    if (destructive) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().testTag(tag),
        ) { Text(stringResource(label)) }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text(stringResource(label)) }
    }
}

private fun TestModeAction.doneMessage() = when (this) {
    TestModeAction.RESET_APP -> R.string.test_mode_done_reset_app
    TestModeAction.CLEAR_STORAGE -> R.string.test_mode_done_clear_storage
    TestModeAction.RESET_DATA -> R.string.test_mode_done_reset_data
    TestModeAction.GENERATE_DATA -> R.string.test_mode_done_generate_data
}
