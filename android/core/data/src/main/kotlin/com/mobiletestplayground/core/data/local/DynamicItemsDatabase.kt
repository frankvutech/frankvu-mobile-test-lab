package com.mobiletestplayground.core.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "dynamic_items")
data class DynamicItemEntity(
    @PrimaryKey val id: Int,
    val title: String,
)

@Dao
interface DynamicItemDao {
    @Query("SELECT * FROM dynamic_items ORDER BY id")
    fun observeAll(): Flow<List<DynamicItemEntity>>

    @Query("SELECT COALESCE(MAX(id), 0) FROM dynamic_items")
    suspend fun maxId(): Int

    @Query("SELECT COUNT(*) FROM dynamic_items")
    suspend fun count(): Int

    @Insert
    suspend fun insert(items: List<DynamicItemEntity>)

    @Query("UPDATE dynamic_items SET title = :title WHERE id = :id")
    suspend fun rename(id: Int, title: String): Int

    @Query("DELETE FROM dynamic_items WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM dynamic_items")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(items: List<DynamicItemEntity>) {
        deleteAll()
        insert(items)
    }
}

@Database(entities = [DynamicItemEntity::class], version = 1, exportSchema = true)
abstract class PlaygroundDatabase : RoomDatabase() {
    abstract fun dynamicItems(): DynamicItemDao
}
