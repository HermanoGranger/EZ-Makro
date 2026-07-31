package com.example.ezmakro.core.data.network

import io.ktor.http.HttpStatusCode

/**
 * Retry behavior for HTTP requests that failed with a transient status.
 * Deliberately conservative: few attempts, a multi-second base delay and
 * exponential backoff — retrying immediately would only aggravate an already
 * overloaded service (OFF global rate limits return 503, see scope/ADR).
 *
 * Only requests that are safe to repeat (idempotent GETs) should opt in;
 * [safeCall] and [HttpClient.get] default to [RetryPolicy.none].
 */
data class RetryPolicy(
    val maxAttempts: Int,
    val baseDelayMillis: Long,
    val isRetryable: (statusCode: Int) -> Boolean,
) {
    /**
     * Exponential backoff for the n-th retry attempt: the first retry waits
     * one [baseDelayMillis], the second two, the third four, and so on.
     */
    fun backoffDelay(attempt: Int): Long = baseDelayMillis * (1L shl (attempt - 1))

    companion object {
        /** Fail fast with the first response — the default. */
        val none = RetryPolicy(maxAttempts = 1, baseDelayMillis = 0L) { false }

        /**
         * OpenFoodFacts returns 503 when its global rate limit is hit or the
         * service is temporarily unavailable. Retry a few times with a
         * multi-second exponential backoff (2s, 4s) instead of hammering it.
         */
        val onServiceUnavailable =
            RetryPolicy(
                maxAttempts = 3,
                baseDelayMillis = 2_000L,
                isRetryable = { it == HttpStatusCode.ServiceUnavailable.value },
            )
    }
}
