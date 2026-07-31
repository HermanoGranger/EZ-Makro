package com.example.ezmakro.core.data.food

import com.example.ezmakro.core.data.food.dto.OffProductResponseDto
import com.example.ezmakro.core.data.food.dto.OffSearchResponseDto
import com.example.ezmakro.core.data.food.mapper.toFoodItem
import com.example.ezmakro.core.data.network.RetryPolicy
import com.example.ezmakro.core.data.network.get
import com.example.ezmakro.core.domain.datasource.FoodRemoteDataSource
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Result
import com.example.ezmakro.core.domain.result.map
import io.ktor.client.HttpClient

/**
 * OpenFoodFacts remote source. The mandatory User-Agent is set globally by
 * [com.example.ezmakro.core.data.network.HttpClientFactory] and therefore
 * applies to every request issued here.
 *
 * OFF returns 503 when its global rate limit is hit; both calls therefore use
 * [RetryPolicy.onServiceUnavailable] to retry with a conservative backoff
 * instead of immediately hammering the service again.
 */
class KtorFoodRemoteDataSource(
    private val httpClient: HttpClient,
) : FoodRemoteDataSource {
    override suspend fun searchByName(query: String): Result<List<FoodItem>, DataError.Network> {
        val response: Result<OffSearchResponseDto, DataError.Network> =
            httpClient.get(
                route = SEARCH_ROUTE,
                queryParameters =
                    mapOf(
                        "search_terms" to query,
                        "search_simple" to 1,
                        "action" to "process",
                        "json" to 1,
                        "page_size" to DEFAULT_PAGE_SIZE,
                    ),
                retryPolicy = RetryPolicy.onServiceUnavailable,
            )
        return response.map { dto -> dto.products.mapNotNull { it.toFoodItem() } }
    }

    override suspend fun getByBarcode(barcode: String): Result<FoodItem?, DataError.Network> {
        val response: Result<OffProductResponseDto, DataError.Network> =
            httpClient.get(
                route = productRoute(barcode),
                retryPolicy = RetryPolicy.onServiceUnavailable,
            )
        return response.map { dto ->
            if (dto.status == PRODUCT_FOUND) dto.product?.toFoodItem() else null
        }
    }

    private fun productRoute(barcode: String): String = "/api/v2/product/$barcode.json"

    private companion object {
        const val SEARCH_ROUTE = "/cgi/search.pl"
        const val DEFAULT_PAGE_SIZE = 25
        const val PRODUCT_FOUND = 1
    }
}
