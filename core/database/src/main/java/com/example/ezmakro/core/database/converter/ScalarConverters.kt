package com.example.ezmakro.core.database.converter

import androidx.room.TypeConverter
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.FinalWeight
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.model.WeightLogId
import java.time.LocalDate

/**
 * Room [TypeConverter]s for primitive-backed domain value types: branded IDs,
 * inline weight classes, enums and [LocalDate].
 *
 * Complex value objects ([NutritionValues], [ServingUnit], the original
 * [FoodItem] snapshot) are stored as JSON via [JsonConverters].
 */
@Suppress("TooManyFunctions")
object ScalarConverters {
    @TypeConverter
    fun localDateToText(value: LocalDate): String = value.toString()

    @TypeConverter
    fun textToLocalDate(value: String): LocalDate = LocalDate.parse(value)

    @TypeConverter
    fun mealSlotToText(value: MealSlot): String = value.name

    @TypeConverter
    fun textToMealSlot(value: String): MealSlot = MealSlot.valueOf(value)

    @TypeConverter
    fun cachedStatusToText(value: CachedFoodItemStatus): String =
        when (value) {
            CachedFoodItemStatus.Original -> "ORIGINAL"
            CachedFoodItemStatus.UserModified -> "USER_MODIFIED"
            CachedFoodItemStatus.UpdateAvailable -> "UPDATE_AVAILABLE"
        }

    @TypeConverter
    fun textToCachedStatus(value: String): CachedFoodItemStatus =
        when (value) {
            "ORIGINAL" -> CachedFoodItemStatus.Original
            "USER_MODIFIED" -> CachedFoodItemStatus.UserModified
            "UPDATE_AVAILABLE" -> CachedFoodItemStatus.UpdateAvailable
            else -> CachedFoodItemStatus.Original
        }

    @TypeConverter
    fun foodIdToString(value: FoodId): String = value.value

    @TypeConverter
    fun stringToFoodId(value: String): FoodId = FoodId(value)

    @TypeConverter
    fun recipeIdToString(value: RecipeId): String = value.value

    @TypeConverter
    fun stringToRecipeId(value: String): RecipeId = RecipeId(value)

    @TypeConverter
    fun diaryEntryIdToString(value: DiaryEntryId): String = value.value

    @TypeConverter
    fun stringToDiaryEntryId(value: String): DiaryEntryId = DiaryEntryId(value)

    @TypeConverter
    fun weightLogIdToString(value: WeightLogId): String = value.value

    @TypeConverter
    fun stringToWeightLogId(value: String): WeightLogId = WeightLogId(value)

    @TypeConverter
    fun rawWeightToDouble(value: RawWeight?): Double? = value?.grams

    @TypeConverter
    fun doubleToRawWeight(value: Double?): RawWeight? = value?.let { RawWeight(it) }

    @TypeConverter
    fun finalWeightToDouble(value: FinalWeight?): Double? = value?.grams

    @TypeConverter
    fun doubleToFinalWeight(value: Double?): FinalWeight? = value?.let { FinalWeight(it) }
}
