package com.example.ezmakro.core.domain.usecase.diary

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.DiaryEntry
import com.example.ezmakro.core.domain.model.DiaryEntryId
import com.example.ezmakro.core.domain.model.FinalWeight
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.LocalDateRange
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.model.RawWeight
import com.example.ezmakro.core.domain.model.Recipe
import com.example.ezmakro.core.domain.model.RecipeId
import com.example.ezmakro.core.domain.model.RecipeIngredient
import com.example.ezmakro.core.domain.model.RecipeSnapshot
import com.example.ezmakro.core.domain.repository.DiaryRepository
import com.example.ezmakro.core.domain.repository.RecipeRepository
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.LocalDate

class EditDiaryRecipeEntryUseCaseTest {
    private val date = LocalDate.of(2026, 7, 19)
    private val entryId = DiaryEntryId("e1")
    private val recipeId = RecipeId("r1")
    private val snapshotModifiedAt = 1_700_000_000_000L
    private val liveModifiedAt = 1_700_000_500_000L
    private val liveLastUsedAt = 1_600_000_000_000L
    private val changedAmountGrams = 150.0
    private val changedPortionCount = 3.0

    private val oats =
        FoodItem(
            id = FoodId("f1"),
            name = "Haferflocken",
            barcode = null,
            nutritionPer100g = NutritionValues(370.0, 13.0, 7.0, 60.0, 10.0),
            servingUnit = null,
            isUserCreated = true,
            cachedStatus = CachedFoodItemStatus.Original,
            originalSnapshot = null,
        )
    private val banana =
        FoodItem(
            id = FoodId("f2"),
            name = "Banane",
            barcode = null,
            nutritionPer100g = NutritionValues(89.0, 1.1, 0.3, 23.0, 2.6),
            servingUnit = null,
            isUserCreated = true,
            cachedStatus = CachedFoodItemStatus.Original,
            originalSnapshot = null,
        )

    private fun ingredient(
        food: FoodItem,
        grams: Double,
    ) = RecipeIngredient(food = food, recipeSnapshot = null, rawWeight = RawWeight(grams), servingCount = null)

    private fun snapshotIngredient() =
        RecipeIngredient(
            food = null,
            recipeSnapshot =
                RecipeSnapshot(
                    recipeId = RecipeId("r-sub"),
                    recipeName = "Mandelmus",
                    nutritionPer100g = NutritionValues(600.0, 20.0, 50.0, 10.0, 10.0),
                    originalLastModifiedAt = null,
                    portions = 1,
                    gramsPerPortion = 40.0,
                ),
            rawWeight = RawWeight(40.0),
            servingCount = null,
        )

    private val liveRecipe =
        Recipe(
            id = recipeId,
            name = "Haferflocken-Muesli",
            ingredients = listOf(ingredient(oats, 80.0)),
            finalWeight = FinalWeight(300.0),
            portions = 2,
            category = "Fruehstueck",
            lastModifiedAt = liveModifiedAt,
            isFavorite = true,
            lastUsedAt = liveLastUsedAt,
        )

    /** Diary-Snapshot mit bewusst veralteten Metadaten — Basis für A5. */
    private val snapshotRecipe =
        liveRecipe.copy(
            name = "Muesli (Snapshot)",
            ingredients = listOf(ingredient(oats, 60.0)),
            finalWeight = null,
            portions = 1,
            category = null,
            lastModifiedAt = snapshotModifiedAt,
            isFavorite = false,
            lastUsedAt = null,
        )

    private val editedRecipe =
        liveRecipe.copy(
            name = "Muesli bearbeitet",
            ingredients = listOf(ingredient(oats, 100.0), ingredient(banana, 50.0)),
            finalWeight = FinalWeight(420.0),
            portions = 4,
            lastModifiedAt = snapshotModifiedAt,
        )

    private val recipeEntry =
        DiaryEntry(
            id = entryId,
            slot = MealSlot.BREAKFAST,
            date = date,
            food = null,
            recipe = snapshotRecipe,
            amountGrams = null,
            portionCount = 1.0,
            isRecipeOverridden = false,
        )

    private val foodEntry =
        DiaryEntry(
            id = entryId,
            slot = MealSlot.BREAKFAST,
            date = date,
            food = oats,
            recipe = null,
            amountGrams = 80.0,
            portionCount = null,
        )

    private class FakeDiaryRepository(
        private val log: MutableList<String>,
        private val getByIdResult: Result<DiaryEntry?, DataError.Local>,
        private val saveResult: EmptyResult<DataError.Local> = Result.Success(Unit),
    ) : DiaryRepository {
        val saveCalls = mutableListOf<DiaryEntry>()
        val getByIdCalls = mutableListOf<DiaryEntryId>()
        var saved: DiaryEntry? = null
            private set

        override fun observeByDate(date: LocalDate): Flow<List<DiaryEntry>> = flowOf(emptyList())

        override suspend fun getById(id: DiaryEntryId): Result<DiaryEntry?, DataError.Local> {
            getByIdCalls += id
            return getByIdResult
        }

        override suspend fun save(entry: DiaryEntry): EmptyResult<DataError.Local> {
            log += "diary"
            saveCalls += entry
            saved = entry
            return saveResult
        }

        override suspend fun delete(id: DiaryEntryId): EmptyResult<DataError.Local> = Result.Success(Unit)

        override fun observeByDateRange(range: LocalDateRange): Flow<List<DiaryEntry>> = flowOf(emptyList())
    }

    private class FakeRecipeRepository(
        private val log: MutableList<String>,
        private val getByIdResult: Result<Recipe?, DataError.Local>,
        private val saveResult: EmptyResult<DataError.Local> = Result.Success(Unit),
    ) : RecipeRepository {
        val calls = mutableListOf<String>()
        val saveCalls = mutableListOf<Recipe>()
        var saved: Recipe? = null
            private set

        override fun observeAll(): Flow<List<Recipe>> = flowOf(emptyList())

        override fun observeById(id: RecipeId): Flow<Recipe?> = flowOf(null)

        override fun observeFavorites(): Flow<List<Recipe>> = flowOf(emptyList())

        override fun observeRecent(limit: Int): Flow<List<Recipe>> = flowOf(emptyList())

        override suspend fun getById(id: RecipeId): Result<Recipe?, DataError.Local> {
            calls += "getById"
            return getByIdResult
        }

        override suspend fun save(recipe: Recipe): EmptyResult<DataError.Local> {
            calls += "save"
            log += "recipe"
            saveCalls += recipe
            saved = recipe
            return saveResult
        }

        override suspend fun delete(id: RecipeId): EmptyResult<DataError.Local> = Result.Success(Unit)

        override suspend fun duplicate(id: RecipeId): Result<RecipeId, DataError.Local> =
            Result.Error(DataError.Local.UNKNOWN)

        override suspend fun toggleFavorite(id: RecipeId): EmptyResult<DataError.Local> = Result.Success(Unit)

        override suspend fun markUsed(id: RecipeId): EmptyResult<DataError.Local> {
            calls += "markUsed"
            log += "markUsed"
            return Result.Success(Unit)
        }
    }

    private fun fakes(
        entry: DiaryEntry? = recipeEntry,
        recipe: Recipe? = liveRecipe,
        recipeSaveResult: EmptyResult<DataError.Local> = Result.Success(Unit),
    ): Triple<MutableList<String>, FakeDiaryRepository, FakeRecipeRepository> {
        val log = mutableListOf<String>()
        val diary = FakeDiaryRepository(log, Result.Success(entry))
        val recipeRepository = FakeRecipeRepository(log, Result.Success(recipe), recipeSaveResult)
        return Triple(log, diary, recipeRepository)
    }

    private suspend fun edit(
        diary: FakeDiaryRepository,
        recipeRepository: FakeRecipeRepository,
        edited: Recipe = editedRecipe,
        amountGrams: Double? = null,
        portionCount: Double? = 1.0,
        scope: DiaryRecipeEditScope,
    ): EmptyResult<DataError.Local> =
        EditDiaryRecipeEntryUseCase(diary, recipeRepository)(
            entryId = entryId,
            editedRecipe = edited,
            amountGrams = amountGrams,
            portionCount = portionCount,
            scope = scope,
        )

    @Test
    fun `entry only writes the diary once and flags the snapshot as overridden`() =
        runBlocking {
            val (log, diary, recipeRepository) = fakes()

            val result = edit(diary, recipeRepository, scope = DiaryRecipeEditScope.ENTRY_ONLY)

            assertThat(result).isEqualTo(Result.Success(Unit))
            assertThat(log).isEqualTo(listOf("diary"))
            assertThat(recipeRepository.calls).isEmpty()
            val saved = diary.saved!!
            assertThat(saved.isRecipeOverridden).isTrue()
            assertThat(saved.recipe?.lastModifiedAt).isEqualTo(snapshotModifiedAt)
            assertThat(saved.recipe?.ingredients).isEqualTo(editedRecipe.ingredients)
            assertThat(saved.recipe?.portions).isEqualTo(editedRecipe.portions)
            assertThat(saved.recipe?.finalWeight).isEqualTo(editedRecipe.finalWeight)
        }

    @Test
    fun `entry only writes the changed amount in the same diary write`() =
        runBlocking {
            val (log, diary, recipeRepository) = fakes()

            edit(
                diary,
                recipeRepository,
                amountGrams = changedAmountGrams,
                portionCount = null,
                scope = DiaryRecipeEditScope.ENTRY_ONLY,
            )

            assertThat(log).isEqualTo(listOf("diary"))
            assertThat(diary.saveCalls).hasSize(1)
            val saved = diary.saved!!
            assertThat(saved.amountGrams).isEqualTo(changedAmountGrams)
            assertThat(saved.portionCount).isNull()
        }

    @Test
    fun `entry only keeps the snapshot timestamp and the new portion count`() =
        runBlocking {
            val (_, diary, recipeRepository) = fakes()

            edit(
                diary,
                recipeRepository,
                portionCount = changedPortionCount,
                scope = DiaryRecipeEditScope.ENTRY_ONLY,
            )

            val saved = diary.saved!!
            assertThat(saved.portionCount).isEqualTo(changedPortionCount)
            assertThat(saved.recipe?.lastModifiedAt).isEqualTo(snapshotModifiedAt)
        }

    @Test
    fun `recipe and entry writes the recipe first and shares one timestamp`() =
        runBlocking {
            val (log, diary, recipeRepository) = fakes()

            val result =
                edit(
                    diary,
                    recipeRepository,
                    portionCount = changedPortionCount,
                    scope = DiaryRecipeEditScope.RECIPE_AND_ENTRY,
                )

            assertThat(result).isEqualTo(Result.Success(Unit))
            assertThat(log).isEqualTo(listOf("recipe", "diary"))
            assertThat(diary.saveCalls).hasSize(1)
            val savedRecipe = recipeRepository.saved!!
            val savedEntry = diary.saved!!
            assertThat(savedEntry.isRecipeOverridden).isFalse()
            assertThat(savedRecipe.lastModifiedAt).isGreaterThan(0L)
            assertThat(savedRecipe.lastModifiedAt).isGreaterThan(snapshotModifiedAt)
            assertThat(savedEntry.recipe?.lastModifiedAt).isEqualTo(savedRecipe.lastModifiedAt)
            assertThat(savedEntry.recipe?.ingredients).isEqualTo(editedRecipe.ingredients)
            assertThat(savedEntry.portionCount).isEqualTo(changedPortionCount)
        }

    @Test
    fun `recipe and entry keeps live metadata and overwrites only the edited fields`() =
        runBlocking {
            val (_, diary, recipeRepository) = fakes()
            val editedWithStaleMetadata =
                editedRecipe.copy(
                    name = "Falscher Name aus dem Snapshot",
                    category = "Snacks",
                    isFavorite = false,
                    lastUsedAt = null,
                )

            edit(
                diary,
                recipeRepository,
                edited = editedWithStaleMetadata,
                scope = DiaryRecipeEditScope.RECIPE_AND_ENTRY,
            )

            val savedRecipe = recipeRepository.saved!!
            assertThat(savedRecipe.id).isEqualTo(liveRecipe.id)
            assertThat(savedRecipe.name).isEqualTo(liveRecipe.name)
            assertThat(savedRecipe.category).isEqualTo(liveRecipe.category)
            assertThat(savedRecipe.isFavorite).isEqualTo(liveRecipe.isFavorite)
            assertThat(savedRecipe.lastUsedAt).isEqualTo(liveRecipe.lastUsedAt)
            assertThat(savedRecipe.ingredients).isEqualTo(editedWithStaleMetadata.ingredients)
            assertThat(savedRecipe.portions).isEqualTo(editedWithStaleMetadata.portions)
            assertThat(savedRecipe.finalWeight).isEqualTo(editedWithStaleMetadata.finalWeight)
            assertThat(savedRecipe.lastModifiedAt).isGreaterThan(snapshotModifiedAt)
        }

    @Test
    fun `recipe and entry never calls markUsed`() =
        runBlocking {
            val (log, diary, recipeRepository) = fakes()

            edit(diary, recipeRepository, scope = DiaryRecipeEditScope.RECIPE_AND_ENTRY)

            assertThat(recipeRepository.calls).isEqualTo(listOf("getById", "save"))
            assertThat(log).isEqualTo(listOf("recipe", "diary"))
        }

    @Test
    fun `sub recipe snapshot inside the edited recipe survives`() =
        runBlocking {
            val (_, diary, recipeRepository) = fakes()
            val subRecipeIngredient = snapshotIngredient()
            val editedWithSubRecipe =
                editedRecipe.copy(
                    ingredients = listOf(ingredient(banana, 50.0), subRecipeIngredient),
                )

            edit(
                diary,
                recipeRepository,
                edited = editedWithSubRecipe,
                scope = DiaryRecipeEditScope.RECIPE_AND_ENTRY,
            )

            assertThat(recipeRepository.saved!!.ingredients).isEqualTo(editedWithSubRecipe.ingredients)
            val entryIngredient =
                diary.saved!!
                    .recipe!!
                    .ingredients
                    .last()
            assertThat(entryIngredient.recipeSnapshot).isEqualTo(subRecipeIngredient.recipeSnapshot)
            assertThat(entryIngredient).isEqualTo(subRecipeIngredient)
        }

    @Test
    fun `failing recipe write leaves the diary untouched`() =
        runBlocking {
            val (log, diary, recipeRepository) =
                fakes(recipeSaveResult = Result.Error(DataError.Local.DISK_FULL))

            val result = edit(diary, recipeRepository, scope = DiaryRecipeEditScope.RECIPE_AND_ENTRY)

            assertThat(result).isEqualTo(Result.Error(DataError.Local.DISK_FULL))
            assertThat(log).isEqualTo(listOf("recipe"))
            assertThat(diary.saveCalls).isEmpty()
            assertThat(diary.saved).isNull()
        }

    @Test
    fun `deleted source recipe does not create a new recipe`() =
        runBlocking {
            val (log, diary, recipeRepository) = fakes(recipe = null)

            val result = edit(diary, recipeRepository, scope = DiaryRecipeEditScope.RECIPE_AND_ENTRY)

            assertThat(result).isEqualTo(Result.Error(DataError.Local.NOT_FOUND))
            assertThat(log).isEmpty()
            assertThat(recipeRepository.saveCalls).isEmpty()
            assertThat(diary.saved).isNull()
        }

    @Test
    fun `failing recipe lookup is propagated without any write`() =
        runBlocking {
            val log = mutableListOf<String>()
            val diary = FakeDiaryRepository(log, Result.Success(recipeEntry))
            val recipeRepository =
                FakeRecipeRepository(log, Result.Error(DataError.Local.DISK_FULL))

            val result =
                EditDiaryRecipeEntryUseCase(diary, recipeRepository)(
                    entryId = entryId,
                    editedRecipe = editedRecipe,
                    amountGrams = null,
                    portionCount = 1.0,
                    scope = DiaryRecipeEditScope.RECIPE_AND_ENTRY,
                )

            assertThat(result).isEqualTo(Result.Error(DataError.Local.DISK_FULL))
            assertThat(log).isEmpty()
            assertThat(diary.saved).isNull()
        }

    @Test
    fun `unknown entry id is reported as not found`() =
        runBlocking {
            val (log, diary, recipeRepository) = fakes(entry = null)

            val result = edit(diary, recipeRepository, scope = DiaryRecipeEditScope.ENTRY_ONLY)

            assertThat(result).isEqualTo(Result.Error(DataError.Local.NOT_FOUND))
            assertThat(log).isEmpty()
            assertThat(diary.getByIdCalls).isEqualTo(listOf(entryId))
            assertThat(diary.saved).isNull()
        }

    @Test
    fun `food entry is not a recipe entry`() =
        runBlocking {
            val (log, diary, recipeRepository) = fakes(entry = foodEntry)

            val result = edit(diary, recipeRepository, scope = DiaryRecipeEditScope.ENTRY_ONLY)

            assertThat(result).isEqualTo(Result.Error(DataError.Local.NOT_FOUND))
            assertThat(log).isEmpty()
            assertThat(recipeRepository.calls).isEmpty()
            assertThat(diary.saved).isNull()
        }

    @Test
    fun `failing entry lookup is propagated unchanged`() =
        runBlocking {
            val log = mutableListOf<String>()
            val diary = FakeDiaryRepository(log, Result.Error(DataError.Local.UNKNOWN))
            val recipeRepository = FakeRecipeRepository(log, Result.Success(liveRecipe))

            val result = edit(diary, recipeRepository, scope = DiaryRecipeEditScope.RECIPE_AND_ENTRY)

            assertThat(result).isEqualTo(Result.Error(DataError.Local.UNKNOWN))
            assertThat(log).isEmpty()
            assertThat(diary.saved).isNull()
        }
}
