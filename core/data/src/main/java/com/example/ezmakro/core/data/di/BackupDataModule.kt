package com.example.ezmakro.core.data.di

import com.example.ezmakro.core.data.backup.BackupExporter
import com.example.ezmakro.core.data.backup.BackupImporter
import com.example.ezmakro.core.data.backup.BackupLocalDataSource
import com.example.ezmakro.core.data.backup.BackupStorage
import com.example.ezmakro.core.data.backup.ContentResolverBackupStorage
import com.example.ezmakro.core.data.backup.PackageManagerAppVersionProvider
import com.example.ezmakro.core.data.backup.RoomBackupLocalDataSource
import com.example.ezmakro.core.data.backup.RoomBackupRepository
import com.example.ezmakro.core.domain.repository.BackupRepository
import com.example.ezmakro.core.domain.util.AppVersionProvider
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Wires the backup data layer (WP3): SAF stream access, app version metadata,
 * the narrow local data source over `BackupDao` + `SettingsDataStore`, the
 * export/import orchestration and the domain-facing repository.
 */
val backupDataModule =
    module {
        single<BackupStorage> { ContentResolverBackupStorage(androidContext()) }
        single<AppVersionProvider> { PackageManagerAppVersionProvider(androidContext()) }
        single<BackupLocalDataSource> { RoomBackupLocalDataSource(dao = get(), settings = get()) }
        single { BackupExporter(local = get(), storage = get(), versionProvider = get()) }
        single { BackupImporter(local = get(), storage = get()) }
        single<BackupRepository> { RoomBackupRepository(local = get(), exporter = get(), importer = get()) }
    }
