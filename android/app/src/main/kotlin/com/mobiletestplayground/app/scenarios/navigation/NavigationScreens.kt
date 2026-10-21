package com.mobiletestplayground.app.scenarios.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ScreenColumn
import com.mobiletestplayground.core.ui.components.SectionTitle
import com.mobiletestplayground.core.ui.components.ValueRow
import com.mobiletestplayground.core.ui.components.textTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The counter demonstrates state retention: it is held in SavedStateHandle, so it survives
 * rotation (ViewModel retained) and process death (state restored), but not a fresh launch.
 */
@HiltViewModel
class NavigationViewModel @Inject constructor(private val handle: SavedStateHandle) : ViewModel() {
    val counter: StateFlow<Int> = handle.getStateFlow(KEY_COUNTER, 0)

    fun increment() {
        handle[KEY_COUNTER] = counter.value + 1
    }

    private companion object {
        const val KEY_COUNTER = "counter"
    }
}

@Composable
fun NavigationRootScreen(
    onBack: () -> Unit,
    onPushDetail: () -> Unit,
    onOpenModal: () -> Unit,
    onOpenNested: () -> Unit,
    viewModel: NavigationViewModel = hiltViewModel(),
) {
    val counter by viewModel.counter.collectAsStateWithLifecycle()
    PlaygroundScaffold(stringResource(R.string.scenario_navigation), TestTags.NAVIGATION_SCREEN, onBack) { padding ->
        ScreenColumn(padding) {
            Text(stringResource(R.string.navigation_intro), modifier = Modifier.testTag(textTag(R.string.navigation_intro)))
            Button(onClick = onPushDetail, modifier = Modifier.fillMaxWidth().testTag(TestTags.NAV_PUSH_DETAIL)) {
                Text(stringResource(R.string.navigation_push))
            }
            Button(onClick = onOpenModal, modifier = Modifier.fillMaxWidth().testTag(TestTags.NAV_OPEN_MODAL)) {
                Text(stringResource(R.string.navigation_modal))
            }
            Button(onClick = onOpenNested, modifier = Modifier.fillMaxWidth().testTag(TestTags.NAV_OPEN_NESTED)) {
                Text(stringResource(R.string.navigation_nested))
            }
            SectionTitle(R.string.navigation_state)
            Text(
                stringResource(R.string.navigation_state_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(textTag(R.string.navigation_state_hint)),
            )
            ValueRow(stringResource(R.string.navigation_counter), counter.toString(), TestTags.NAV_COUNTER_VALUE)
            OutlinedButton(onClick = viewModel::increment, modifier = Modifier.testTag(TestTags.NAV_COUNTER_INCREMENT)) {
                Text(stringResource(R.string.navigation_increment))
            }
        }
    }
}

@Composable
fun NavigationDetailScreen(level: Int, onBack: () -> Unit, onPushNext: () -> Unit, onPopToRoot: () -> Unit) {
    PlaygroundScaffold(stringResource(R.string.navigation_detail_title, level), TestTags.NAV_DETAIL_SCREEN, onBack) { padding ->
        ScreenColumn(padding) {
            Text(
                stringResource(R.string.navigation_level, level),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.testTag(TestTags.NAV_DETAIL_LEVEL),
            )
            Button(onClick = onPushNext, modifier = Modifier.fillMaxWidth().testTag(TestTags.NAV_DETAIL_PUSH_NEXT)) {
                Text(stringResource(R.string.navigation_push_next))
            }
            OutlinedButton(onClick = onPopToRoot, modifier = Modifier.fillMaxWidth().testTag(TestTags.NAV_DETAIL_POP_TO_ROOT)) {
                Text(stringResource(R.string.navigation_pop_to_root))
            }
        }
    }
}

/** A full-screen modal destination (a navigation `dialog`), dismissed by its close button or Back. */
@Composable
fun NavigationModalScreen(onClose: () -> Unit) {
    Surface(Modifier.fillMaxSize().testTag(TestTags.NAV_MODAL_SCREEN)) {
        Column(Modifier.padding(24.dp)) {
            IconButton(onClick = onClose, modifier = Modifier.testTag(TestTags.NAV_MODAL_CLOSE)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
            }
            SectionTitle(R.string.navigation_modal_title)
            Text(stringResource(R.string.navigation_modal_body), modifier = Modifier.testTag(textTag(R.string.navigation_modal_body)))
        }
    }
}

private val nestedTabs = listOf("a", "b", "c")

/** A screen hosting its own NavHost: tab switches happen in the nested back stack. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationNestedScreen(onBack: () -> Unit) {
    val nestedController = rememberNavController()
    val entry by nestedController.currentBackStackEntryAsState()
    val current = entry?.destination?.route ?: nestedTabs.first()
    PlaygroundScaffold(stringResource(R.string.navigation_nested), TestTags.NAV_NESTED_SCREEN, onBack) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(
                selectedTabIndex = nestedTabs.indexOf(current).coerceAtLeast(0),
                modifier = Modifier.testTag(TestTags.NAV_NESTED_TABS),
            ) {
                nestedTabs.forEach { tab ->
                    Tab(
                        selected = current == tab,
                        onClick = {
                            nestedController.navigate(tab) {
                                popUpTo(nestedController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        text = { Text(stringResource(R.string.navigation_tab, tab.uppercase())) },
                        modifier = Modifier.testTag(TestTags.navNestedTab(tab)),
                    )
                }
            }
            NavHost(nestedController, startDestination = nestedTabs.first()) {
                nestedTabs.forEach { tab ->
                    composable(tab) {
                        Text(
                            stringResource(R.string.navigation_tab_content, tab.uppercase()),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(24.dp).testTag(TestTags.NAV_NESTED_CONTENT),
                        )
                    }
                }
            }
        }
    }
}
