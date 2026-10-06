package com.example.ezmakro.core.data.backup

import android.content.Context
import android.net.Uri
import com.example.ezmakro.core.domain.result.DataError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Byte-level access to the user-chosen SAF document.
 *
 * The repository works with plain `uri: String` (the domain contract of
 * `BackupRepository`); only this adapter turns it back into an [android.net.Uri].
 */
interface BackupStorage {
    suspend fun openOutput(uri: String): OutputStream

    suspend fun openInput(uri: String): InputStream
}

/**
 * SAF-backed implementation. The mode `"wt"` (= write + truncate) matters: a
 * second export into the same document must not leave the tail of the previous,
 * longer file behind — plain `"w"` can preserve old bytes (WP3 Vorgaben).
 *
 * No `FileProvider` and no manifest entry are needed, the picked `content://`
 * URI already grants the access (A1/§1.3).
 */
class ContentResolverBackupStorage(
    private val context: Context,
) : BackupStorage {
    override suspend fun openOutput(uri: String): OutputStream =
        withContext(Dispatchers.IO) {
            requireNotNull(context.contentResolver.openOutputStream(Uri.parse(uri), OUTPUT_TRUNCATE)) {
                "Cannot open output stream for uri"
            }
        }

    override suspend fun openInput(uri: String): InputStream =
        withContext(Dispatchers.IO) {
            requireNotNull(context.contentResolver.openInputStream(Uri.parse(uri))) {
                "Cannot open input stream for uri"
            }
        }

    private companion object {
        const val OUTPUT_TRUNCATE = "wt"
    }
}

/**
 * Maps the I/O failures of a backup run to typed [DataError.Local]s: a document
 * that cannot be opened is [DataError.Local.NOT_FOUND], a device without free
 * space is [DataError.Local.DISK_FULL], everything else stays generic. Shared
 * by the exporter, the importer and the local data source so one rule decides
 * the mapping.
 */
fun ioErrorToDataError(error: Exception): DataError.Local =
    when (error) {
        is FileNotFoundException -> DataError.Local.NOT_FOUND
        is IOException ->
            if (error.message.orEmpty().contains("space", ignoreCase = true)) {
                DataError.Local.DISK_FULL
            } else {
                DataError.Local.UNKNOWN
            }
        else -> DataError.Local.UNKNOWN
    }
