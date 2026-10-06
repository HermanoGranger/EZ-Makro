package com.example.ezmakro.core.domain.result

/**
 * Shared error types for data access, split by origin so callers can react
 * appropriately (e.g. surface "no internet" vs. "disk full").
 */
sealed interface DataError : Error {
    enum class Network : DataError {
        BAD_REQUEST,
        REQUEST_TIMEOUT,
        UNAUTHORIZED,
        FORBIDDEN,
        NOT_FOUND,
        CONFLICT,
        TOO_MANY_REQUESTS,
        NO_INTERNET,
        PAYLOAD_TOO_LARGE,
        SERVER_ERROR,
        SERVICE_UNAVAILABLE,
        SERIALIZATION,
        UNKNOWN,
    }

    enum class Local : DataError {
        DISK_FULL,

        /** The selected file is not a readable EZ Makro backup (§3.4 stage 1/2). */
        INVALID_BACKUP,
        NOT_FOUND,
        UNKNOWN,

        /** The archive's `formatVersion` is newer than this app understands (A2). */
        UNSUPPORTED_BACKUP_VERSION,
    }
}
