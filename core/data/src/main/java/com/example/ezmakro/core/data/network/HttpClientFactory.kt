package com.example.ezmakro.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Central Ktor configuration. The engine is injected so tests can swap in a
 * [io.ktor.client.engine.mock.MockEngine] (see safeCall tests). The base URL is
 * intentionally generic; feature clients (e.g. OpenFoodFacts in Phase 4) append
 * their own routes and set the mandatory User-Agent header per ADR/scope.
 */
object HttpClientFactory {
    // api.* ist die für API-Zugriffe vorgesehene Subdomain (statt world.*)
    const val BASE_URL = "https://world.openfoodfacts.org/api/v2"

    /**
     * Mandatory OpenFoodFacts User-Agent (App name, version, contact). OFF
     * rate-limits or blocks requests without a meaningful User-Agent (scope /
     * ADR). Sent on every request via [io.ktor.client.plugins.defaultRequest].
     *
     * TODO(Phase 4): replace the placeholder contact with a real address/URL
     * before release, and wire the version from BuildConfig.
     */
    const val USER_AGENT = "EZMakro/0.1 (Android; contact: openfoodfacts.perfume121@passmail.net)"

    fun create(engine: HttpClientEngine): HttpClient =
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        encodeDefaults = true
                    },
                )
            }
            install(Logging) {
                logger =
                    object : Logger {
                        override fun log(message: String) {
                            com.example.ezmakro.core.data.logging.AppLogger
                                .d(message, tag = "HttpClient")
                        }
                    }
                level = LogLevel.ALL
            }
            defaultRequest {
                url(BASE_URL)
                headers.append(HttpHeaders.UserAgent, USER_AGENT)
            }
        }
}
