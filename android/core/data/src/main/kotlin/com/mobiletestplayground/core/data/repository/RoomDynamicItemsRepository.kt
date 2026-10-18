package com.mobiletestplayground.core.data.repository

import com.mobiletestplayground.core.data.local.DynamicItemDao
import com.mobiletestplayground.core.data.local.DynamicItemEntity
import com.mobiletestplayground.core.domain.list.DynamicItemsRepository
import com.mobiletestplayground.core.domain.model.DynamicItem
import com.mobiletestplayground.core.domain.seed.SeedData
import com.mobiletestplayground.core.domain.testmode.ResettableStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Dynamic list items persisted in Room, so edits survive a process restart and are cleared only
 * by Test Mode reset or by clearing app data. New ids are max(id) + 1, so ids are never reused
 * within a data set and are predictable after a reset.
 */
class RoomDynamicItemsRepository(
    private val dao: DynamicItemDao,
) : DynamicItemsRepository,
    ResettableStore {

    private val mutex = Mutex()
    private var seeded = false

    override val items: Flow<List<DynamicItem>> = dao.observeAll().map { rows -> rows.map { DynamicItem(it.id, it.title) } }

    /** Seeds the table the first time the app runs. */
    suspend fun ensureSeeded() = mutex.withLock {
        if (!seeded && dao.count() == 0) dao.insert(seedRows())
        seeded = true
    }

    override suspend fun add(title: String): DynamicItem? = mutex.withLock {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return@withLock null
        val item = DynamicItemEntity(dao.maxId() + 1, trimmed)
        dao.insert(listOf(item))
        DynamicItem(item.id, item.title)
    }

    override suspend fun rename(id: Int, title: String): Boolean = mutex.withLock {
        val trimmed = title.trim()
        trimmed.isNotEmpty() && dao.rename(id, trimmed) > 0
    }

    override suspend fun delete(id: Int) = mutex.withLock { dao.delete(id) }

    override suspend fun reset() = mutex.withLock { dao.replaceAll(seedRows()) }

    override suspend fun resetToSeed() = reset()

    private fun seedRows() = SeedData.dynamicItems.map { DynamicItemEntity(it.id, it.title) }
}
