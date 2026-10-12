package com.example.ezmakro.feature.foodpicker.presentation.picker

import com.example.ezmakro.core.domain.calculation.estimatedPer100g
import com.example.ezmakro.core.domain.calculation.nutritionPer100g
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.presentation.uitext.UiText

data class FoodPickerState(
    val activeTab: FoodPickerTab = FoodPickerTab.SEARCH,
    val searchQuery: String = "",
    val recentFoods: List<PickerFoodUi> = emptyList(),
    val localSearchResults: List<PickerFoodUi> = emptyList(),
    val onlineSearchResults: List<PickerFoodUi> = emptyList(),
    val onlineFoodItems: List<FoodItem> = emptyList(),
    val isSearchingOnline: Boolean = false,
    val onlineError: UiText? = null,
    val selectionError: UiText? = null,
    val userFoods: List<PickerFoodUi> = emptyList(),
    val userRecipes: List<PickerRecipeUi> = emptyList(),
    val recentRecipes: List<PickerRecipeUi> = emptyList(),
    val localRecipeResults: List<PickerRecipeUi> = emptyList(),
    val favoriteFoods: List<PickerFoodUi> = emptyList(),
    val favoriteRecipes: List<PickerRecipeUi> = emptyList(),
    val showFoods: Boolean = true,
    val isCameraPermissionGranted: Boolean = false,
    val isLookingUpBarcode: Boolean = false,
    val barcodeError: UiText? = null,
    /**
     * Id of the food whose inline detail panel is open. Actions only write this
     * id; [detailFood] is re-projected from the observed data (see
     * `FoodPickerViewModel.refreshDetail()`), so an edit made elsewhere shows up
     * in the still-open panel without reopening it.
     */
    val detailFoodId: String? = null,
    /**
     * Id of the recipe whose inline detail panel is open, counterpart of [detailFoodId].
     */
    val detailRecipeId: String? = null,
    /**
     * Read-only projection of [detailFoodId], maintained exclusively by
     * `FoodPickerViewModel.refreshDetail()`. `null` means the panel is closed —
     * including when the source item disappeared.
     */
    val detailFood: FoodItem? = null,
    /**
     * Read-only projection of [detailRecipeId], maintained exclusively by
     * `FoodPickerViewModel.refreshDetail()`.
     */
    val detailRecipe: Recipe? = null,
    val selectedItemReady: Boolean = false,
    val selectedItemId: String? = null,
    val selectedItemIsRecipe: Boolean = false,
    val selectedItemHasServingUnit: Boolean = false,
    /** Weight of one portion in grams for the selected item, when known. */
    val selectedItemGramsPerPortion: Double? = null,
    /** Whether [selectedItemGramsPerPortion] is estimated (recipe without measured final weight). */
    val selectedItemGramsPerPortionEstimated: Boolean = false,
    /**
     * Set while the recipe-ingredient amount dialog is open: the item is
     * awaiting a raw weight / serving count before it is emitted as
     * `AddAsIngredient`.
     */
    val pendingIngredient: PendingIngredient? = null,
    /**
     * Ids of foods/recipes added during the current picker session. Rows show a
     * checkmark instead of the add icon for these; reset when the sheet is
     * dismissed.
     */
    val addedItemIds: Set<String> = emptySet(),
    /** Last confirmed diary amount for this session, reused as dialog default. */
    val lastDiaryAmount: String? = null,
    /** Whether [lastDiaryAmount] referred to grams (true) or portions (false). */
    val lastDiaryIsGrams: Boolean = true,
    /** Last confirmed recipe-ingredient raw weight, reused as dialog default. */
    val lastIngredientRawWeight: String? = null,
    /** Last confirmed recipe-ingredient serving count, reused as dialog default. */
    val lastIngredientServingCount: String? = null,
    val targetContext: FoodPickerTarget = FoodPickerTarget.DIARY,
    val targetDate: String? = null,
    val slot: MealSlot? = null,
)

/**
 * Item awaiting its amount before it can be added as a recipe ingredient.
 *
 * [servingAmount] is the human-readable size of one portion (e.g. "300 g"),
 * shown as a hint above the serving-count field; null when unknown.
 */
data class PendingIngredient(
    val itemId: String,
    val itemName: String,
    val servingLabel: String?,
    val servingAmount: String? = null,
    val isRecipe: Boolean,
)

enum class FoodPickerTab { BARCODE, SEARCH, FAVORITES }

enum class FoodPickerTarget { DIARY, RECIPE_INGREDIENT, BROWSE }

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
    val isFavorite: Boolean,
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
        kcalPer100g = nutritionPer100g()?.kcal ?: estimatedPer100g().kcal,
        isFavorite = isFavorite,
    )
