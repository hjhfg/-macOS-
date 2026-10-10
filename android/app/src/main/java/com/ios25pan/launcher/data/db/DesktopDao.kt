package com.ios25pan.launcher.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DesktopDao {
    @Query("SELECT * FROM desktop_items ORDER BY sortOrder")
    fun observeAll(): Flow<List<DesktopItemEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM desktop_items")
    suspend fun maxOrder(): Int

    @Upsert
    suspend fun upsert(item: DesktopItemEntity)

    @Query("DELETE FROM desktop_items WHERE id = :id OR parentId = :id")
    suspend fun deleteTree(id: String)
}
