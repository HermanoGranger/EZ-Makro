package com.example.ezmakro.core.domain.model

/**
 * The four meal slots a diary day is split into (scope: Frühstück, Mittag,
 * Abend, Snacks).
 */
enum class MealSlot {
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACKS,
}

/**
 * Aggregate Root: one logged consumption in the diary.
 *
 * A diary entry references either a [FoodItem] (quick add / single food) or a
 * [Recipe]; never both.
 *
 * For a food, [amountGrams] is the consumed raw amount.
 * For a recipe, the user logs EITHER [portionCount] portions OR [amountGrams]
 * of the prepared dish — exactly one must be set (the other is null).
 */
data class DiaryEntry(
    val id: DiaryEntryId,
    val slot: MealSlot,
    val date: java.time.LocalDate,
    val food: FoodItem?,
    val recipe: Recipe?,
    val amountGrams: Double?,
    val portionCount: Double?,
) {
    init {
        require(food != null || recipe != null) { "DiaryEntry needs a food or a recipe" }
        require(!(food != null && recipe != null)) { "DiaryEntry must not reference both food and recipe" }

        if (food != null) {
            require(amountGrams != null) { "Food diary entry needs amountGrams" }
            require(portionCount == null) { "Food diary entry must not set portionCount" }
            require(amountGrams >= 0.0) { "amountGrams must be non-negative" }
        }
        if (recipe != null) {
            require(amountGrams != null || portionCount != null) {
                "Recipe diary entry needs either amountGrams or portionCount"
            }
            require(!(amountGrams != null && portionCount != null)) {
                "Recipe diary entry must not set both amountGrams and portionCount"
            }
            amountGrams?.let { require(it >= 0.0) { "amountGrams must be non-negative" } }
            portionCount?.let { require(it >= 0.0) { "portionCount must be non-negative" } }
        }
    }
}
