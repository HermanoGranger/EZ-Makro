package com.example.ezmakro.core.data.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.security.MessageDigest

/**
 * Encode / decode / verify of the versioned backup JSON format (ADR-007).
 *
 * Pure Kotlin: no Android, no Room, no IO. The codec is a function between
 * [BackupDocumentDto] and [String] and is testable without an emulator.
 *
 * [decode] follows the validation order of §3.4:
 * 1. Parse the raw text — a parse failure is [DecodeError.Corrupted].
 * 2. Pre-check `format` / `formatVersion` on the raw [JsonObject] **before**
 *    the strict mapping runs, so a foreign file is reported as
 *    [DecodeError.NotAZBackup] and a newer format version as
 *    [DecodeError.UnsupportedVersion] instead of a generic decode error.
 * 3. Strict decoding into [BackupDocumentDto] — missing frame fields are
 *    [DecodeError.Corrupted] (A12: frame fields stay default-free).
 *
 * The checksum comparison is **never** an error: it surfaces as
 * [DecodeResult.Success.checksumValid] so the importer can turn a mismatch or a
 * missing `checksum` into a warning (A14). SHA-256 is computed over the
 * canonical encoding of `content` — settings sorted, everything else in DTO
 * declaration order — so the value is independent of the order in which the
 * producer happened to assemble the settings.
 */
object BackupCodec {
    const val FORMAT_ID = "ezmakro.backup"
    const val FORMAT_VERSION = 1
    const val MIN_SUPPORTED_FORMAT_VERSION = 1

    private const val HEX_RADIX = 16
    private const val HEX_BYTE_WIDTH = 2
    private const val SHA_256 = "SHA-256"
    private const val CHECKSUM_PREFIX = "sha256:"

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

    private val settingComparator =
        compareBy<BackupSettingDto>({ it.key }, { it.value })

    sealed interface DecodeResult {
        data class Success(
            val document: BackupDocumentDto,
            val checksumValid: Boolean,
        ) : DecodeResult

        data class Failure(
            val reason: DecodeError,
        ) : DecodeResult
    }

    sealed interface DecodeError {
        data object NotAZBackup : DecodeError

        data class UnsupportedVersion(
            val found: Int,
            val supportedMax: Int,
        ) : DecodeError

        data object Corrupted : DecodeError
    }

    fun encode(document: BackupDocumentDto): String = json.encodeToString(BackupDocumentDto.serializer(), document)

    /**
     * Canonical form of `content` used for the checksum: `settings` is sorted
     * by key/value so two archives with the same data hash identically (A14).
     */
    fun canonicalContent(content: BackupContentDto): String =
        json.encodeToString(
            BackupContentDto.serializer(),
            content.copy(settings = content.settings.sortedWith(settingComparator)),
        )

    /** SHA-256 over [canonicalContent], formatted as `"sha256:<hex>"` (A14). */
    fun checksum(content: BackupContentDto): String {
        val canonical = canonicalContent(content).encodeToByteArray()
        val digest = MessageDigest.getInstance(SHA_256).digest(canonical)
        val hex = digest.joinToString(separator = "") { byte -> toHex(byte) }
        return CHECKSUM_PREFIX + hex
    }

    @Suppress("ReturnCount")
    fun decode(text: String): DecodeResult {
        val root =
            try {
                json.parseToJsonElement(text)
            } catch (ignore: SerializationException) {
                return DecodeResult.Failure(DecodeError.Corrupted)
            }
        if (root !is JsonObject) {
            return DecodeResult.Failure(DecodeError.NotAZBackup)
        }
        val format = (root["format"] as? JsonPrimitive)?.content
        if (format != FORMAT_ID) {
            return DecodeResult.Failure(DecodeError.NotAZBackup)
        }
        val formatVersion =
            (root["formatVersion"] as? JsonPrimitive)?.intOrNull
                ?: return DecodeResult.Failure(DecodeError.Corrupted)
        if (formatVersion < MIN_SUPPORTED_FORMAT_VERSION || formatVersion > FORMAT_VERSION) {
            return DecodeResult.Failure(DecodeError.UnsupportedVersion(formatVersion, FORMAT_VERSION))
        }
        val document =
            try {
                json.decodeFromString(BackupDocumentDto.serializer(), text)
            } catch (ignore: SerializationException) {
                return DecodeResult.Failure(DecodeError.Corrupted)
            }
        val checksumValid = document.checksum.isNotEmpty() && document.checksum == checksum(document.content)
        return DecodeResult.Success(document, checksumValid)
    }

    private fun toHex(byte: Byte): String = byte.toUByte().toString(HEX_RADIX).padStart(HEX_BYTE_WIDTH, '0')
}
