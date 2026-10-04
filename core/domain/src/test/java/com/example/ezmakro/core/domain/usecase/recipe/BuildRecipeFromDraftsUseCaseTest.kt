package com.example.ezmakro.core.domain.usecase.recipe

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.example.ezmakro.core.domain.calculation.createRecipeSnapshot
import com.example.ezmakro.core.domain.calculation.nutrition
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.FinalWeight
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.model.RecipeIngredient
import com.example.ezmakro.core.domain.model.RecipeIngredientDraft
import com.example.ezmakro.core.domain.model.RecipeSnapshot
import com.example.ezmakro.core.domain.model.ServingUnit
import com.example.ezmakro.core.domain.result.Result
import org.junit.jupiter.api.Test

private fun food(
    id: String = "f1",
    per100g: NutritionValues? = NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0),
    servingUnit: ServingUnit? = null,
): FoodItem =
    FoodItem(
        id = FoodId(id),
        name = "Food $id",
        barcode = null,
        nutritionPer100g = per100g,
        servingUnit = servingUnit,
        isUserCreated = true,
        cachedStatus = CachedFoodItemStatus.Original,
        originalSnapshot = null,
    )

private fun recipe(
    id: String = "r1",
    name: String = "Unter",
    finalWeight: FinalWeight? = null,
    portions: Int = 2,
    lastModifiedAt: Long = 1_234L,
): Recipe =
    Recipe(
        id = RecipeId(id),
        name = name,
        ingredients =
            listOf(
                RecipeIngredient(
                    food = food(id = "sub-food"),
                    rawWeight = RawWeight(100.0),
                    servingCount = null,
                ),
            ),
        finalWeight = finalWeight,
        portions = portions,
        lastModifiedAt = lastModifiedAt,
    )

private fun draft(
    foodId: String = "f1",
    rawWeight: String = "",
    servingCount: String = "",
    recipeId: String? = null,
    recipeSnapshot: RecipeSnapshot? = null,
): RecipeIngredientDraft =
    RecipeIngredientDraft(
        foodId = foodId,
        foodName = "Draft",
        servingLabel = null,
        rawWeight = rawWeight,
        servingCount = servingCount,
        recipeId = recipeId,
        recipeSnapshot = recipeSnapshot,
    )

class BuildRecipeFromDraftsUseCaseTest {
    private val useCase = BuildRecipeFromDraftsUseCase()

    private fun dataOf(result: Result<List<RecipeIngredient>, RecipeDraftError>): List<RecipeIngredient> {
        assertThat(result).isInstanceOf(Result.Success::class)
        return when (result) {
            is Result.Success -> result.data
            is Result.Error -> throw AssertionError("expected Success but was ${result.error}")
        }
    }

    private fun errorOf(result: Result<List<RecipeIngredient>, RecipeDraftError>): RecipeDraftError =
        when (result) {
            is Result.Success -> throw AssertionError("expected Error but was ${result.data.size} ingredients")
            is Result.Error -> result.error
        }

    @Test
    fun `raw weight only resolves to an ingredient without serving count`() {
        val apple = food("f1")

        val ingredients = dataOf(useCase(listOf(draft(rawWeight = "150")), listOf(apple), emptyList()))

        assertThat(ingredients.size).isEqualTo(1)
        val ingredient = ingredients.first()
        assertThat(ingredient.food).isEqualTo(apple)
        assertThat(ingredient.rawWeight).isEqualTo(RawWeight(150.0))
        assertThat(ingredient.servingCount).isNull()
        assertThat(ingredient.recipeSnapshot).isNull()
    }

    @Test
    fun `serving count with a serving unit resolves to a portion ingredient`() {
        val egg = food("f1", servingUnit = ServingUnit("1 Ei", 50.0))

        val ingredients = dataOf(useCase(listOf(draft(servingCount = "2")), listOf(egg), emptyList()))

        val ingredient = ingredients.first()
        assertThat(ingredient.food).isEqualTo(egg)
        assertThat(ingredient.rawWeight).isNull()
        assertThat(ingredient.servingCount).isEqualTo(2.0)
        assertThat(ingredient.effectiveRawWeight()).isEqualTo(RawWeight(100.0))
    }

    @Test
    fun `raw weight wins over serving count when both are set`() {
        val egg = food("f1", servingUnit = ServingUnit("1 Ei", 50.0))

        val ingredients =
            dataOf(
                useCase(
                    listOf(draft(rawWeight = "100", servingCount = "2")),
                    listOf(egg),
                    emptyList(),
                ),
            )

        val ingredient = ingredients.first()
        assertThat(ingredient.rawWeight).isEqualTo(RawWeight(100.0))
        assertThat(ingredient.servingCount).isEqualTo(2.0)
        assertThat(ingredient.effectiveRawWeight()).isEqualTo(RawWeight(100.0))
    }

    @Test
    fun `unknown food id fails with UnknownFood`() {
        val apple = food("f1")

        val result = useCase(listOf(draft(foodId = "gone", rawWeight = "100")), listOf(apple), emptyList())

        assertThat(errorOf(result)).isEqualTo(RecipeDraftError.UnknownFood)
    }

    @Test
    fun `zero amount fails with MissingAmount`() {
        val apple = food("f1")

        val result = useCase(listOf(draft(rawWeight = "0")), listOf(apple), emptyList())

        assertThat(errorOf(result)).isEqualTo(RecipeDraftError.MissingAmount)
    }

    @Test
    fun `blank amount fails with MissingAmount`() {
        val apple = food("f1")

        val result = useCase(listOf(draft()), listOf(apple), emptyList())

        assertThat(errorOf(result)).isEqualTo(RecipeDraftError.MissingAmount)
    }

    @Test
    fun `serving count without raw weight and without serving unit fails with MissingAmount`() {
        val apple = food("f1")

        val result = useCase(listOf(draft(servingCount = "2")), listOf(apple), emptyList())

        assertThat(errorOf(result)).isEqualTo(RecipeDraftError.MissingAmount)
    }

    @Test
    fun `existing sub recipe resolves to a freshly created snapshot`() {
        val subRecipe = recipe(id = "r2", finalWeight = FinalWeight(300.0), portions = 2)

        val ingredients =
            dataOf(
                useCase(
                    listOf(draft(foodId = "r2", rawWeight = "150", recipeId = "r2")),
                    emptyList(),
                    listOf(subRecipe),
                ),
            )

        val ingredient = ingredients.first()
        assertThat(ingredient.food).isNull()
        assertThat(ingredient.recipeSnapshot).isEqualTo(createRecipeSnapshot(subRecipe))
        assertThat(ingredient.recipeSnapshot?.recipeId).isEqualTo(RecipeId("r2"))
        assertThat(ingredient.rawWeight).isEqualTo(RawWeight(150.0))
    }

    @Test
    fun `deleted sub recipe keeps the snapshot carried by the draft`() {
        val staleSnapshot =
            RecipeSnapshot(
                recipeId = RecipeId("r2"),
                recipeName = "Unter",
                nutritionPer100g = NutritionValues(100.0, 10.0, 5.0, 2.0, 1.0),
                originalLastModifiedAt = 1_234L,
                portions = 2,
                gramsPerPortion = 100.0,
            )

        val ingredients =
            dataOf(
                useCase(
                    listOf(
                        draft(
                            foodId = "r2",
                            rawWeight = "150",
                            recipeId = "r2",
                            recipeSnapshot = staleSnapshot,
                        ),
                    ),
                    emptyList(),
                    emptyList(),
                ),
            )

        val ingredient = ingredients.first()
        assertThat(ingredient.recipeSnapshot).isEqualTo(staleSnapshot)
        assertThat(ingredient.recipeSnapshot?.originalLastModifiedAt).isEqualTo(1_234L)
        assertThat(ingredient.rawWeight).isEqualTo(RawWeight(150.0))
    }

    @Test
    fun `deleted sub recipe without a snapshot in the draft fails with MissingSnapshot`() {
        val result =
            useCase(
                listOf(draft(foodId = "r2", rawWeight = "150", recipeId = "r2")),
                emptyList(),
                emptyList(),
            )

        assertThat(errorOf(result)).isEqualTo(RecipeDraftError.MissingSnapshot)
    }

    @Test
    fun `one broken draft aborts the whole list without a partial result`() {
        val apple = food("f1")

        val result =
            useCase(
                listOf(
                    draft(rawWeight = "100"),
                    draft(foodId = "gone", rawWeight = "100"),
                    draft(rawWeight = "50"),
                ),
                listOf(apple),
                emptyList(),
            )

        assertThat(result).isInstanceOf(Result.Error::class)
        assertThat(errorOf(result)).isEqualTo(RecipeDraftError.UnknownFood)
    }

    @Test
    fun `sub recipe draft with serving count resolves through the snapshot grams per portion`() {
        val subRecipe = recipe(id = "r2", finalWeight = FinalWeight(300.0), portions = 2)

        val ingredients =
            dataOf(
                useCase(
                    listOf(draft(foodId = "r2", servingCount = "2", recipeId = "r2")),
                    emptyList(),
                    listOf(subRecipe),
                ),
            )

        val ingredient = ingredients.first()
        assertThat(ingredient.rawWeight).isNull()
        assertThat(ingredient.servingCount).isEqualTo(2.0)
        // 300 g final weight over 2 portions => 150 g per portion, two portions => 300 g
        assertThat(ingredient.effectiveRawWeight()).isEqualTo(RawWeight(300.0))
        assertThat(ingredient.nutrition().kcal).isCloseTo(100.0, 0.001)
    }
}
