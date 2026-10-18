package com.mobiletestplayground.app.scenarios.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobiletestplayground.app.UiTiming
import com.mobiletestplayground.core.domain.list.DynamicItemsRepository
import com.mobiletestplayground.core.domain.list.DynamicListQuery
import com.mobiletestplayground.core.domain.list.PagedItems
import com.mobiletestplayground.core.domain.list.SortOrder
import com.mobiletestplayground.core.domain.model.DynamicItem
import com.mobiletestplayground.core.domain.model.PagedItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DynamicListUiState(
    val items: List<DynamicItem> = emptyList(),
    val totalCount: Int = 0,
    val query: String = "",
    val sort: SortOrder = SortOrder.BY_ID,
)

@HiltViewModel
class DynamicListViewModel @Inject constructor(
    private val repository: DynamicItemsRepository,
    private val handle: SavedStateHandle,
) : ViewModel() {
    private val query = handle.getStateFlow(KEY_QUERY, "")
    private val sort = handle.getStateFlow(KEY_SORT, SortOrder.BY_ID.name)

    val state: StateFlow<DynamicListUiState> = combine(repository.items, query, sort) { items, q, s ->
        val order = SortOrder.valueOf(s)
        DynamicListUiState(DynamicListQuery.apply(items, q, order), items.size, q, order)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), DynamicListUiState())

    fun onQueryChange(value: String) {
        handle[KEY_QUERY] = value
    }

    fun onSortChange(value: SortOrder) {
        handle[KEY_SORT] = value.name
    }

    fun add(title: String, onAdded: (Boolean) -> Unit) {
        viewModelScope.launch { onAdded(repository.add(title) != null) }
    }

    fun rename(id: Int, title: String) {
        viewModelScope.launch { repository.rename(id, title) }
    }

    fun delete(id: Int) {
        viewModelScope.launch { repository.delete(id) }
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_SORT = "sort"
        const val STOP_TIMEOUT = 5_000L
    }
}

data class InfiniteListUiState(
    val items: List<PagedItem> = emptyList(),
    val isLoading: Boolean = false,
    val endReached: Boolean = false,
)

/** Loads fixed pages with a fixed delay, so "scroll, wait for loading, see next page" is reproducible. */
@HiltViewModel
class InfiniteListViewModel @Inject constructor(
    private val timing: UiTiming,
) : ViewModel() {
    private val mutableState = MutableStateFlow(InfiniteListUiState())
    val state: StateFlow<InfiniteListUiState> = mutableState.asStateFlow()
    private var nextPage = 0

    init {
        loadMore()
    }

    fun loadMore() {
        val current = mutableState.value
        if (current.isLoading || current.endReached) return
        mutableState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            delay(timing.pageLoadMillis)
            val page = PagedItems.page(nextPage++)
            mutableState.update { it.copy(items = it.items + page.items, isLoading = false, endReached = !page.hasMore) }
        }
    }
}
