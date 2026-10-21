package com.mobiletestplayground.app.scenarios.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.domain.list.SortOrder
import com.mobiletestplayground.core.domain.model.DynamicItem
import com.mobiletestplayground.core.domain.seed.SeedData
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.LoadingIndicator
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    PlaygroundScaffold(stringResource(R.string.scenario_lists), TestTags.LISTS_SCREEN, onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.testTag(TestTags.LISTS_TABS)) {
                listOf(
                    R.string.lists_static to TestTags.LISTS_TAB_STATIC,
                    R.string.lists_dynamic to TestTags.LISTS_TAB_DYNAMIC,
                    R.string.lists_infinite to TestTags.LISTS_TAB_INFINITE,
                ).forEachIndexed { index, (label, tag) ->
                    Tab(selected = tab == index, onClick = {
                        tab = index
                    }, text = { Text(stringResource(label)) }, modifier = Modifier.testTag(tag))
                }
            }
            when (tab) {
                0 -> StaticList()
                1 -> DynamicList()
                else -> InfiniteList()
            }
        }
    }
}

@Composable
private fun StaticList() {
    LazyColumn(Modifier.fillMaxSize().testTag(TestTags.STATIC_LIST)) {
        itemsIndexed(SeedData.staticListItems) { index, title ->
            ListItem(
                headlineContent = { Text(title) },
                supportingContent = { Text(stringResource(R.string.lists_static_subtitle, index + 1)) },
                modifier = Modifier.testTag(TestTags.listItem(index + 1)),
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun DynamicList(viewModel: DynamicListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var newTitle by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<DynamicItem?>(null) }
    var deleting by remember { mutableStateOf<DynamicItem?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                label = { Text(stringResource(R.string.lists_new_item)) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag(TestTags.DYNAMIC_LIST_INPUT),
            )
            Button(
                onClick = { viewModel.add(newTitle) { added -> if (added) newTitle = "" } },
                enabled = newTitle.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp).testTag(TestTags.DYNAMIC_LIST_ADD),
            ) { Text(stringResource(R.string.action_add)) }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text(stringResource(R.string.lists_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(TestTags.DYNAMIC_LIST_SEARCH),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag(TestTags.DYNAMIC_LIST_SORT)) {
            SortOrder.entries.forEach { order ->
                FilterChip(
                    selected = state.sort == order,
                    onClick = { viewModel.onSortChange(order) },
                    label = { Text(stringResource(order.label())) },
                    modifier = Modifier.testTag(TestTags.dynamicListSort(order.tagSuffix())),
                )
            }
        }
        Text(
            stringResource(R.string.lists_count, state.items.size, state.totalCount),
            modifier = Modifier.testTag(TestTags.DYNAMIC_LIST_COUNT),
        )
        if (state.items.isEmpty()) {
            Text(stringResource(R.string.lists_empty), modifier = Modifier.padding(top = 16.dp).testTag(TestTags.DYNAMIC_LIST_EMPTY))
        }
        LazyColumn(Modifier.fillMaxSize().testTag(TestTags.DYNAMIC_LIST)) {
            items(state.items, key = { it.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.title, Modifier.testTag(TestTags.title(TestTags.dynamicListItem(item.id)))) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { editing = item }, modifier = Modifier.testTag(TestTags.dynamicListEdit(item.id))) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.lists_edit, item.title))
                            }
                            IconButton(onClick = { deleting = item }, modifier = Modifier.testTag(TestTags.dynamicListDelete(item.id))) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.lists_delete, item.title))
                            }
                        }
                    },
                    modifier = Modifier.testTag(TestTags.dynamicListItem(item.id)),
                )
            }
        }
    }

    editing?.let { item ->
        var title by rememberSaveable(item.id) { mutableStateOf(item.title) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.lists_edit_title)) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    modifier = Modifier.testTag(TestTags.DYNAMIC_LIST_EDIT_INPUT),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.rename(item.id, title)
                        editing = null
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.testTag(TestTags.DIALOG_CONFIRM),
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }, modifier = Modifier.testTag(TestTags.DIALOG_CANCEL)) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            modifier = Modifier.testTag(TestTags.DYNAMIC_LIST_EDIT_DIALOG),
        )
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(stringResource(R.string.dialog_delete_message, item.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(item.id)
                        deleting = null
                    },
                    modifier = Modifier.testTag(TestTags.DIALOG_CONFIRM),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }, modifier = Modifier.testTag(TestTags.DIALOG_CANCEL)) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            modifier = Modifier.testTag(TestTags.DIALOG_CONFIRMATION),
        )
    }
}

private fun SortOrder.label() = when (this) {
    SortOrder.BY_ID -> R.string.lists_sort_id
    SortOrder.TITLE_ASCENDING -> R.string.lists_sort_asc
    SortOrder.TITLE_DESCENDING -> R.string.lists_sort_desc
}

private fun SortOrder.tagSuffix() = when (this) {
    SortOrder.BY_ID -> "id"
    SortOrder.TITLE_ASCENDING -> "asc"
    SortOrder.TITLE_DESCENDING -> "desc"
}

@Composable
private fun InfiniteList(viewModel: InfiniteListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - PREFETCH_DISTANCE
        }
    }
    LaunchedEffect(nearEnd, state.items.size) {
        if (nearEnd) viewModel.loadMore()
    }
    Column(Modifier.fillMaxSize()) {
        Text(
            pluralStringResource(R.plurals.lists_loaded, state.items.size, state.items.size),
            modifier = Modifier.padding(16.dp).testTag(TestTags.INFINITE_COUNT),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag(TestTags.INFINITE_LIST),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(state.items, key = { it.index }) { item ->
                ListItem(headlineContent = { Text(item.title) }, modifier = Modifier.testTag(TestTags.infiniteItem(item.index)))
            }
            if (state.isLoading) {
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                        LoadingIndicator(TestTags.INFINITE_LOADING)
                    }
                }
            }
            if (state.endReached) {
                item {
                    Text(
                        stringResource(R.string.lists_end),
                        modifier = Modifier.fillMaxWidth().padding(16.dp).testTag(TestTags.INFINITE_END),
                    )
                }
            }
        }
    }
}

private const val PREFETCH_DISTANCE = 3
