package com.example.ezmakro.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.ezmakro.core.database.entity.WeightLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Local data access for [WeightLogEntity]. [upsert] replaces same-day entries
 * because [WeightLogEntity.date] is the primary key.
 */
@Dao
interface WeightLogDao {
    @Upsert
    fun upsert(entity: WeightLogEntity)

    @Query("SELECT * FROM weight_logs WHERE date = :date")
    fun getById(date: String): WeightLogEntity?

    @Query("SELECT * FROM weight_logs WHERE date >= :from AND date <= :to ORDER BY date")
    fun getByDateRange(
        from: String,
        to: String,
    ): List<WeightLogEntity>

    @Query("SELECT * FROM weight_logs ORDER BY date DESC LIMIT 1")
    fun observeLatest(): Flow<WeightLogEntity?>
}
