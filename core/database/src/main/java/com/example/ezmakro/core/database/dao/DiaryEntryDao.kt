package com.example.ezmakro.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.example.ezmakro.core.database.entity.DiaryEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Local data access for [DiaryEntryEntity] (the meal log).
 */
@Dao
interface DiaryEntryDao {
    @Upsert
    fun upsert(entity: DiaryEntryEntity)

    @Delete
    fun delete(entity: DiaryEntryEntity)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    fun deleteById(id: String)

    @Query("SELECT * FROM diary_entries WHERE id = :id")
    fun getById(id: String): DiaryEntryEntity?

    @Query("SELECT * FROM diary_entries WHERE date = :date ORDER BY slot")
    fun observeByDate(date: String): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entries WHERE date >= :from AND date <= :to ORDER BY date, slot")
    fun getByDateRange(
        from: String,
        to: String,
    ): List<DiaryEntryEntity>
}
