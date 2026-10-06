package com.example.ezmakro.core.domain.result

inline fun <T, E : Error, R> Result<T, E>.map(map: (T) -> R): Result<R, E> =
    when (this) {
        is Result.Error -> Result.Error(error)
        is Result.Success -> Result.Success(map(data))
    }

/**
 * Chains two dependent results: applies [transform] to the success value and
 * passes the error through untouched. Needed for multi-step flows where each
 * step fails with the same error type (e.g. the backup export/import), so the
 * steps stay flat instead of nesting `when` blocks.
 */
inline fun <T, E : Error, R> Result<T, E>.flatMap(transform: (T) -> Result<R, E>): Result<R, E> =
    when (this) {
        is Result.Error -> this
        is Result.Success -> transform(data)
    }

inline fun <T, E : Error> Result<T, E>.onSuccess(action: (T) -> Unit): Result<T, E> =
    when (this) {
        is Result.Error -> this
        is Result.Success -> {
            action(data)
            this
        }
    }

inline fun <T, E : Error> Result<T, E>.onFailure(action: (E) -> Unit): Result<T, E> =
    when (this) {
        is Result.Error -> {
            action(error)
            this
        }
        is Result.Success -> this
    }

fun <T, E : Error> Result<T, E>.asEmptyResult(): EmptyResult<E> = map { }
