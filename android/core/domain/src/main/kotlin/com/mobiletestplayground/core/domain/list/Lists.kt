package com.mobiletestplayground.core.domain.list

import com.mobiletestplayground.core.domain.model.DynamicItem
import com.mobiletestplayground.core.domain.model.PagedItem
import com.mobiletestplayground.core.domain.seed.SeedData
import kotlinx.coroutines.flow.Flow

enum class SortOrder { BY_ID, TITLE_ASCENDING, TITLE_DESCENDING }

/** Search then sort, as a pure function so the rules are unit tested once and shared by the UI. */
object DynamicListQuery {
    fun apply(items: List<DynamicItem>, query: String, sort: SortOrder): List<DynamicItem> {
        val needle = query.trim()
        val filtered = if (needle.isEmpty()) items else items.filter { it.title.contains(needle, ignoreCase = true) }
        return when (sort) {
            SortOrder.BY_ID -> filtered.sortedBy { it.id }
            SortOrder.TITLE_ASCENDING -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            SortOrder.TITLE_DESCENDING -> filtered.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
        }
    }
}

interface DynamicItemsRepository {
    val items: Flow<List<DynamicItem>>

    /** Adds an item and returns it. Blank titles are rejected by returning null. */
    suspend fun add(title: String): DynamicItem?

    suspend fun rename(id: Int, title: String): Boolean

    suspend fun delete(id: Int)

    suspend fun reset()
}

data class Page(val items: List<PagedItem>, val hasMore: Boolean)

/** Deterministic pages: the same index always produces the same items, and the list always ends. */
object PagedItems {
    fun page(pageIndex: Int, pageSize: Int = SeedData.PAGE_SIZE, total: Int = SeedData.PAGED_ITEM_COUNT): Page {
        require(pageIndex >= 0) { "pageIndex must not be negative" }
        val start = pageIndex * pageSize
        val end = minOf(start + pageSize, total)
        val items = (start until end).map { PagedItem(it + 1, "Row " + (it + 1).toString().padStart(3, '0')) }
        return Page(items, hasMore = end < total)
    }
}

/**
 * Replaces the dynamic list with the seed plus ten generated items, "Generated 01" … "Generated 10".
 * Always produces the same ids and titles, whatever the list held before.
 */
class GenerateTestDataUseCase @javax.inject.Inject constructor(
    private val repository: DynamicItemsRepository,
) {
    suspend operator fun invoke() {
        repository.reset()
        for (number in 1..GENERATED_COUNT) {
            repository.add("Generated " + number.toString().padStart(2, '0'))
        }
    }

    companion object {
        const val GENERATED_COUNT = 10
    }
}
