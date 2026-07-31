package com.example.ezmakro.core.data.network

import com.example.ezmakro.core.data.logging.AppLogger
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Result
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.ContentConvertException
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import java.net.UnknownHostException

/**
 * Typed Ktor extensions that wrap every call in [safeCall] and map the
 * response to [Result]<[T], [DataError.Network]>. Call sites stay uniform:
 * `httpClient.get(route = "/product/$barcode")`.
 */
suspend inline fun <reified Response : Any> HttpClient.get(
    route: String,
    queryParameters: Map<String, Any?> = emptyMap(),
    retryPolicy: RetryPolicy = RetryPolicy.none,
): Result<Response, DataError.Network> = safeCall({ get(route) { applyParameters(queryParameters) } }, retryPolicy)

suspend inline fun <reified Request : Any, reified Response : Any> HttpClient.post(
    route: String,
    body: Request,
): Result<Response, DataError.Network> = safeCall({ post(route) { setBody(body) } }, RetryPolicy.none)

suspend inline fun <reified Response : Any> HttpClient.delete(
    route: String,
    queryParameters: Map<String, Any?> = emptyMap(),
): Result<Response, DataError.Network> =
    safeCall({ delete(route) { applyParameters(queryParameters) } }, RetryPolicy.none)

fun HttpRequestBuilder.applyParameters(queryParameters: Map<String, Any?>) {
    queryParameters.forEach { (key, value) ->
        if (value != null) parameter(key, value)
    }
}

@Suppress(
    "TooGenericExceptionCaught",
    "InstanceOfCheckForException",
    "ReturnCount",
    "MagicNumber",
)
suspend inline fun <reified T> safeCall(
    execute: () -> HttpResponse,
    retryPolicy: RetryPolicy = RetryPolicy.none,
): Result<T, DataError.Network> {
    var attempt = 0
    while (true) {
        val response =
            try {
                execute()
            } catch (e: UnresolvedAddressException) {
                AppLogger.e("No internet", e, tag = "safeCall")
                return Result.Error(DataError.Network.NO_INTERNET)
            } catch (e: UnknownHostException) {
                AppLogger.e("No internet", e, tag = "safeCall")
                return Result.Error(DataError.Network.NO_INTERNET)
            } catch (e: SerializationException) {
                AppLogger.e("Serialization failed", e, tag = "safeCall")
                return Result.Error(DataError.Network.SERIALIZATION)
            } catch (e: ContentConvertException) {
                AppLogger.e("Serialization failed", e, tag = "safeCall")
                return Result.Error(DataError.Network.SERIALIZATION)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.e("Unknown network error", e, tag = "safeCall")
                return Result.Error(DataError.Network.UNKNOWN)
            }

        val statusCode = response.status.value
        if (attempt + 1 >= retryPolicy.maxAttempts || !retryPolicy.isRetryable(statusCode)) {
            return responseToResult(response)
        }
        attempt++
        delay(retryPolicy.backoffDelay(attempt))
    }
}

@Suppress("MagicNumber")
suspend inline fun <reified T> responseToResult(response: HttpResponse): Result<T, DataError.Network> =
    when (response.status.value) {
        in 200..299 ->
            try {
                Result.Success(response.body<T>())
            } catch (e: SerializationException) {
                AppLogger.e("Serialization failed", e, tag = "safeCall")
                Result.Error(DataError.Network.SERIALIZATION)
            } catch (e: ContentConvertException) {
                AppLogger.e("Serialization failed", e, tag = "safeCall")
                Result.Error(DataError.Network.SERIALIZATION)
            }
        400 -> Result.Error(DataError.Network.BAD_REQUEST)
        401 -> Result.Error(DataError.Network.UNAUTHORIZED)
        403 -> Result.Error(DataError.Network.FORBIDDEN)
        404 -> Result.Error(DataError.Network.NOT_FOUND)
        408 -> Result.Error(DataError.Network.REQUEST_TIMEOUT)
        409 -> Result.Error(DataError.Network.CONFLICT)
        413 -> Result.Error(DataError.Network.PAYLOAD_TOO_LARGE)
        429 -> Result.Error(DataError.Network.TOO_MANY_REQUESTS)
        503 -> Result.Error(DataError.Network.SERVICE_UNAVAILABLE)
        in 500..599 -> Result.Error(DataError.Network.SERVER_ERROR)
        else -> Result.Error(DataError.Network.UNKNOWN)
    }
