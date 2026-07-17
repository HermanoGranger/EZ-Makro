package com.example.ezmakro.core.domain.result

/**
 * Marker for any domain- or layer-specific error type. Error types are
 * returned inside [Result] so that expected failures never surface as thrown
 * exceptions (see android-error-handling skill / ADR-001 clean layering).
 */
interface Error

/**
 * Typed, generic result used across all layers. Success carries data of type
 * [D]; failure carries a typed [Error] of type [E].
 */
sealed interface Result<out D, out E : Error> {
    data class Success<out D>(
        val data: D,
    ) : Result<D, Nothing>

    data class Error<out E : com.example.ezmakro.core.domain.result.Error>(
        val error: E,
    ) : Result<Nothing, E>
}

typealias EmptyResult<E> = Result<Unit, E>
