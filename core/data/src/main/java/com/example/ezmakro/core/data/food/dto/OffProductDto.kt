package com.example.ezmakro.core.data.food.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single OpenFoodFacts product. OFF returns a very large, loosely-typed
 * payload; we only model the fields we need and rely on `ignoreUnknownKeys`
 * (configured in [com.example.ezmakro.core.data.network.HttpClientFactory]) for
 * the rest.
 */
@Serializable
data class OffProductDto(
    @SerialName("code")
    val code: String? = null,
    @SerialName("product_name")
    val productName: String? = null,
    @SerialName("product_name_de")
    val productNameDe: String? = null,
    @SerialName("serving_quantity")
    val servingQuantity: String? = null,
    @SerialName("serving_size")
    val servingSize: String? = null,
    @SerialName("nutriments")
    val nutriments: OffNutrimentsDto? = null,
)

/**
 * Nutrient values expressed per 100 g. OFF sometimes only provides `energy_100g`
 * (kJ); [energyKcal100g] is preferred and the mapper converts kJ as a fallback.
 */
@Serializable
data class OffNutrimentsDto(
    @SerialName("energy-kcal_100g")
    val energyKcal100g: Double? = null,
    @SerialName("energy_100g")
    val energy100gKj: Double? = null,
    @SerialName("proteins_100g")
    val proteins100g: Double? = null,
    @SerialName("fat_100g")
    val fat100g: Double? = null,
    @SerialName("carbohydrates_100g")
    val carbohydrates100g: Double? = null,
    @SerialName("fiber_100g")
    val fiber100g: Double? = null,
)
