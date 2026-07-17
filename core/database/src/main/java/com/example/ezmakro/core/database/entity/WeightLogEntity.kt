package com.example.ezmakro.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent form of [com.example.ezmakro.core.domain.model.WeightLog].
 *
 * [date] is the primary key so re-weighing on the same day replaces the prior
 * value (upsert semantics).
 */
@Entity(tableName = "weight_logs")
data class WeightLogEntity(
    @PrimaryKey
    val date: String,
    val kilograms: Double,
)
