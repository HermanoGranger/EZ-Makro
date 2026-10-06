package com.example.ezmakro.core.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * [BackupStorage] that keeps documents in memory — no SAF, no Robolectric.
 *
 * A written document is readable through [read] after the producer closed the
 * stream (`ByteArrayOutputStream.close()` is a no-op), and a document prepared
 * with [write] is returned by [openInput].
 */
class InMemoryBackupStorage : BackupStorage {
    private val outputs = mutableMapOf<String, ByteArrayOutputStream>()
    private val inputs = mutableMapOf<String, ByteArray>()

    override suspend fun openOutput(uri: String): OutputStream = ByteArrayOutputStream().also { outputs[uri] = it }

    override suspend fun openInput(uri: String): InputStream {
        val bytes = inputs[uri] ?: outputs[uri]?.toByteArray()
        requireNotNull(bytes) { "no document at $uri" }
        return ByteArrayInputStream(bytes)
    }

    /** Puts a raw document where [openInput] can find it. */
    fun write(
        uri: String,
        bytes: ByteArray,
    ) {
        inputs[uri] = bytes
    }

    /** The document that was produced for [uri] in this run. */
    fun read(uri: String): ByteArray = outputs.getValue(uri).toByteArray()

    fun readText(uri: String): String = read(uri).toString(Charsets.UTF_8)
}
