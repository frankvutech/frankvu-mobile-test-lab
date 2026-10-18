package com.mobiletestplayground.app.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobiletestplayground.app.R
import com.mobiletestplayground.app.navigation.Scenario
import com.mobiletestplayground.core.domain.auth.AuthRepository
import com.mobiletestplayground.core.domain.model.Session
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    val session: StateFlow<Session?> = authRepository.session
    val query: StateFlow<String> = savedState.getStateFlow(KEY_QUERY, "")

    fun onQueryChange(value: String) {
        savedState[KEY_QUERY] = value
    }

    private companion object {
        const val KEY_QUERY = "query"
    }
}

/** Matches a scenario when the query appears in its localized title or its stable id. */
object ScenarioFilter {
    fun <T> filter(items: List<T>, query: String, title: (T) -> String, id: (T) -> String): List<T> {
        val needle = query.trim()
        if (needle.isEmpty()) return items
        return items.filter { title(it).contains(needle, ignoreCase = true) || id(it).contains(needle, ignoreCase = true) }
    }
}

@Composable
fun HomeScreen(
    onScenario: (Scenario) -> Unit,
    onCart: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val titles = Scenario.entries.associateWith { stringResource(it.title) }
    val visible = ScenarioFilter.filter(Scenario.entries, query, { titles.getValue(it) }, { it.id })

    PlaygroundScaffold(
        title = stringResource(R.string.app_name),
        rootTag = TestTags.HOME_SCREEN,
        onBack = null,
        actions = {
            IconButton(onClick = onCart, modifier = Modifier.testTag(TestTags.HOME_CART)) {
                Icon(Icons.Filled.ShoppingCart, contentDescription = stringResource(R.string.nav_cart))
            }
            IconButton(onClick = onProfile, modifier = Modifier.testTag(TestTags.HOME_PROFILE)) {
                Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.nav_profile))
            }
            IconButton(onClick = onSettings, modifier = Modifier.testTag(TestTags.HOME_SETTINGS)) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag(TestTags.HOME_SCENARIO_LIST),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                val name = session?.user?.displayName
                Text(
                    text = if (name != null) stringResource(R.string.home_welcome, name) else stringResource(R.string.home_welcome_guest),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag(TestTags.HOME_WELCOME),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text(stringResource(R.string.home_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .testTag(TestTags.HOME_SEARCH),
                )
            }
            if (visible.isEmpty()) {
                item {
                    Text(stringResource(R.string.home_no_results), modifier = Modifier.testTag(TestTags.HOME_EMPTY_SEARCH))
                }
            }
            items(visible, key = { it.id }) { scenario ->
                ScenarioCard(scenario, titles.getValue(scenario)) { onScenario(scenario) }
            }
        }
    }
}

@Composable
private fun ScenarioCard(scenario: Scenario, title: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag(TestTags.scenario(scenario.id))
            .semantics { role = Role.Button },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(scenario.icon, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            androidx.compose.foundation.layout.Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(scenario.description), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
