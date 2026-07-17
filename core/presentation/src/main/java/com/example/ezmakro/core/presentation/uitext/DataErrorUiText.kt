package com.example.ezmakro.core.presentation.uitext

import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.presentation.R

/**
 * Maps a [DataError] to user-facing [UiText]. Internal-only errors that should
 * never reach the user can be mapped to [UiText.StringResource] as a fallback
 * (or intentionally left unmapped in feature code).
 */
fun DataError.toUiText(): UiText {
    val id = when (this) {
        DataError.Network.NO_INTERNET -> R.string.error_no_internet
        DataError.Network.REQUEST_TIMEOUT -> R.string.error_request_timeout
        DataError.Network.UNAUTHORIZED -> R.string.error_unauthorized
        DataError.Network.FORBIDDEN -> R.string.error_forbidden
        DataError.Network.NOT_FOUND -> R.string.error_not_found
        DataError.Network.CONFLICT -> R.string.error_conflict
        DataError.Network.TOO_MANY_REQUESTS -> R.string.error_too_many_requests
        DataError.Network.PAYLOAD_TOO_LARGE -> R.string.error_payload_too_large
        DataError.Network.SERVER_ERROR -> R.string.error_server
        DataError.Network.SERVICE_UNAVAILABLE -> R.string.error_service_unavailable
        DataError.Network.SERIALIZATION -> R.string.error_serialization
        DataError.Network.BAD_REQUEST -> R.string.error_unknown
        DataError.Network.UNKNOWN -> R.string.error_unknown
        DataError.Local.DISK_FULL -> R.string.error_disk_full
        DataError.Local.NOT_FOUND -> R.string.error_not_found
        DataError.Local.UNKNOWN -> R.string.error_unknown
    }
    return UiText.StringResource(id)
}
