package com.example.ezmakro.feature.foodpicker.presentation.picker

import com.example.ezmakro.core.domain.calculation.nutritionPer100g
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.presentation.uitext.UiText

data class FoodPickerState(
    val activeTab: FoodPickerTab = FoodPickerTab.SEARCH,
    val searchQuery: String = "",
    val recentFoods: List<PickerFoodUi> = emptyList(),
    val localSearchResults: List<PickerFoodUi> = emptyList(),
    val onlineSearchResults: List<PickerFoodUi> = emptyList(),
    val isSearchingOnline: Boolean = false,
    val onlineError: UiText? = null,
    val userFoods: List<PickerFoodUi> = emptyList(),
    val userRecipes: List<PickerRecipeUi> = emptyList(),
    val showFoods: Boolean = true,
    val isCameraPermissionGranted: Boolean = false,
    val isLookingUpBarcode: Boolean = false,
    val barcodeError: UiText? = null,
    val selectedItemReady: Boolean = false,
    val selectedItemId: String? = null,
    val selectedItemIsRecipe: Boolean = false,
    val selectedItemHasServingUnit: Boolean = false,
    val targetContext: FoodPickerTarget = FoodPickerTarget.DIARY,
    val targetDate: String? = null,
)

enum class FoodPickerTab { BARCODE, SEARCH, MY_FOODS }

enum class FoodPickerTarget { DIARY, RECIPE_INGREDIENT }

data class PickerFoodUi(
    val id: String,
    val name: String,
    val kcalPer100g: Double,
    val servingLabel: String?,
    val isFavorite: Boolean,
)

data class PickerRecipeUi(
    val id: String,
    val name: String,
    val kcalPer100g: Double,
)

fun FoodItem.toPickerFoodUi() =
    PickerFoodUi(
        id = id.value,
        name = name,
        kcalPer100g = nutritionPer100g?.kcal ?: 0.0,
        servingLabel = servingUnit?.label,
        isFavorite = isFavorite,
    )

fun Recipe.toPickerRecipeUi() =
    PickerRecipeUi(
        id = id.value,
        name = name,
        kcalPer100g = nutritionPer100g()?.kcal ?: 0.0,
    )
