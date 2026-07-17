package com.example.ezmakro.core.data.food.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response of the OFF v2 product endpoint `/api/v2/product/{barcode}.json`.
 * [status] is `1` when the product exists, `0` when not found.
 */
@Serializable
data class OffProductResponseDto(
    @SerialName("status")
    val status: Int = 0,
    @SerialName("product")
    val product: OffProductDto? = null,
)

/**
 * Response of the OFF search endpoint `/cgi/search.pl`.
 */
@Serializable
data class OffSearchResponseDto(
    @SerialName("products")
    val products: List<OffProductDto> = emptyList(),
)
