package com.example.ezmakro.core.data.di

import com.example.ezmakro.core.data.diary.RoomDiaryLocalDataSource
import com.example.ezmakro.core.data.diary.RoomDiaryRepository
import com.example.ezmakro.core.database.dao.DiaryEntryDao
import com.example.ezmakro.core.domain.datasource.DiaryLocalDataSource
import com.example.ezmakro.core.domain.repository.DiaryRepository
import org.koin.dsl.module

/**
 * Wires the Phase 7 meal-log data layer: the Room local source and the
 * offline-first repository. Depends on [DiaryEntryDao] (database module).
 */
val diaryDataModule =
    module {
        single<DiaryLocalDataSource> { RoomDiaryLocalDataSource(dao = get<DiaryEntryDao>()) }
        single<DiaryRepository> { RoomDiaryRepository(local = get()) }
    }
