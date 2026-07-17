package com.example.ezmakro.core.domain.result

inline fun <T, E : Error, R> Result<T, E>.map(map: (T) -> R): Result<R, E> =
    when (this) {
        is Result.Error -> Result.Error(error)
        is Result.Success -> Result.Success(map(data))
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
