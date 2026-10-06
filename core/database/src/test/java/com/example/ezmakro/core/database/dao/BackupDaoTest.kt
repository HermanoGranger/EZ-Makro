package com.example.ezmakro.core.database.dao

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isTrue
import com.example.ezmakro.core.database.DatabaseTest
import com.example.ezmakro.core.database.entity.DiaryEntryEntity
import com.example.ezmakro.core.database.entity.FoodItemEntity
import com.example.ezmakro.core.database.entity.WeightLogEntity
import com.example.ezmakro.core.database.testutil.FoodItemFixtures
import com.example.ezmakro.core.database.testutil.RecipeFixtures
import com.example.ezmakro.core.database.testutil.upsertFood
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Validates [BackupDao]: full-table reads, `IGNORE`-based bulk inserts (A7), merge idempotency
 * (A7/A19), FTS maintenance by the Room content-sync triggers (A13) and the FK-safe
 * [BackupDao.replaceAll] order.
 */
@RunWith(RobolectricTestRunner::class)
class BackupDaoTest : DatabaseTest() {
    private val dao get() = db.backupDao()

    private fun food(
        id: String,
        name: String = id,
        favorite: Boolean = false,
        lastUsedAt: Long? = null,
    ): FoodItemEntity = FoodItemFixtures.food(id = id, name = name).copy(isFavorite = favorite, lastUsedAt = lastUsedAt)

    private fun entry(
        id: String,
        date: String = "2026-01-05",
        foodId: String? = "f1",
        recipeId: String? = null,
        recipeOverridden: Boolean = false,
    ) = DiaryEntryEntity(
        id = id,
        slot = "BREAKFAST",
        date = date,
        foodItemId = foodId,
        recipeId = recipeId,
        amountGrams = if (recipeId == null) 150.0 else null,
        portionCount = if (recipeId == null) null else 1.0,
        recipeOverridden = recipeOverridden,
        foodSnapshot = if (foodId == null) null else """{"id":"$foodId","name":"Haferflocken"}""",
        recipeSnapshot = if (recipeId == null) null else """{"id":"$recipeId","name":"Pfannkuchen"}""",
    )

    private fun weightLog(
        date: String = "2026-01-05",
        kilograms: Double = 75.5,
    ) = WeightLogEntity(date = date, kilograms = kilograms)

    // --- Read ---

    @Test
    fun `read queries return every row including snapshot and favorite columns`() =
        runTest {
            val foods =
                listOf(
                    food("f1", name = "Haferflocken", favorite = true, lastUsedAt = 100L),
                    food("f2", name = "Milch"),
                )
            val recipes =
                listOf(
                    RecipeFixtures
                        .recipe(
                            id = "r1",
                            name = "Pfannkuchen",
                            finalWeightGrams = 400.0,
                            category = "Frühstück",
                            isFavorite = true,
                            lastUsedAt = 200L,
                        ).copy(lastModifiedAt = 1234L),
                )
            val ingredients =
                listOf(
                    RecipeFixtures.ingredient(recipeId = "r1", foodId = "f1"),
                    RecipeFixtures
                        .ingredient(recipeId = "r1", foodId = "f2", grams = 200.0)
                        .copy(recipeSnapshotJson = """{"id":"r1","portions":2}"""),
                )
            val entries =
                listOf(entry(id = "d1"), entry(id = "d2", foodId = null, recipeId = "r1", recipeOverridden = true))
            val logs = listOf(weightLog("2026-01-05", 75.5), weightLog("2026-01-06", 75.0))

            val report = dao.insertAll(foods, recipes, ingredients, entries, logs)
            assertThat(report.foodItems).isEqualTo(TableInsertCounts(inserted = 2, skipped = 0))

            val storedFood = dao.readFoodItems().single { it.id == "f1" }
            assertThat(storedFood.name).isEqualTo("Haferflocken")
            assertThat(storedFood.origName).isEqualTo("Haferflocken")
            assertThat(storedFood.isFavorite).isTrue()
            assertThat(storedFood.lastUsedAt).isEqualTo(100L)
            assertThat(storedFood.nutriKcal).isEqualTo(350.0)

            val storedRecipe = dao.readRecipes().single()
            assertThat(storedRecipe.id).isEqualTo("r1")
            assertThat(storedRecipe.finalWeightGrams).isEqualTo(400.0)
            assertThat(storedRecipe.portions).isEqualTo(2)
            assertThat(storedRecipe.category).isEqualTo("Frühstück")
            assertThat(storedRecipe.isFavorite).isEqualTo(1)
            assertThat(storedRecipe.lastUsedAt).isEqualTo(200L)
            assertThat(storedRecipe.lastModifiedAt).isEqualTo(1234L)

            val storedIngredients = dao.readRecipeIngredients()
            assertThat(storedIngredients).hasSize(2)
            assertThat(storedIngredients.single { it.foodItemId == "f2" }.rawWeightGrams).isEqualTo(200.0)
            assertThat(storedIngredients.single { it.recipeSnapshotJson != null }.recipeSnapshotJson)
                .isEqualTo("""{"id":"r1","portions":2}""")

            val storedEntry = dao.readDiaryEntries().single { it.id == "d2" }
            assertThat(storedEntry.recipeOverridden).isTrue()
            assertThat(storedEntry.recipeSnapshot).isEqualTo("""{"id":"r1","name":"Pfannkuchen"}""")
            assertThat(storedEntry.portionCount).isEqualTo(1.0)

            assertThat(dao.readDiaryEntries()).hasSize(2)
            assertThat(dao.readWeightLogs())
                .containsExactly(weightLog("2026-01-05", 75.5), weightLog("2026-01-06", 75.0))
        }

    @Test
    fun `read id projections return the natural keys`() =
        runTest {
            dao.insertAll(
                foods = listOf(food("f1"), food("f2")),
                recipes = listOf(RecipeFixtures.recipe(id = "r1")),
                ingredients = listOf(RecipeFixtures.ingredient(recipeId = "r1", foodId = "f1")),
                entries = listOf(entry(id = "d1")),
                weightLogs = listOf(weightLog()),
            )

            assertThat(dao.readFoodItemIds()).containsExactlyInAnyOrder("f1", "f2")
            assertThat(dao.readRecipeIds()).containsExactly("r1")
        }

    // --- IGNORE semantics on the natural keys (A7) ---

    @Test
    fun `insertFoodItems keeps only the first row of a duplicate id in one call`() =
        runTest {
            val first = food("f1", name = "Apfel", favorite = true, lastUsedAt = 100L)
            val duplicate = food("f1", name = "Import Apfel", favorite = false, lastUsedAt = null)

            val rowIds = dao.insertFoodItems(listOf(first, duplicate))

            assertThat(rowIds).hasSize(2)
            assertThat(rowIds[0]).isGreaterThanOrEqualTo(0L)
            assertThat(rowIds[1]).isEqualTo(-1L)
            assertThat(dao.countFoodItems()).isEqualTo(1)

            val stored = dao.readFoodItems().single()
            assertThat(stored.name).isEqualTo("Apfel")
            assertThat(stored.isFavorite).isTrue()
            assertThat(stored.lastUsedAt).isEqualTo(100L)
        }

    @Test
    fun `insertFoodItems skips an existing id without resetting favorite or last used`() =
        runTest {
            dao.insertFoodItems(listOf(food("f1", name = "Apfel", favorite = true, lastUsedAt = 100L)))

            val rowIds =
                dao.insertFoodItems(listOf(food("f1", name = "Import Apfel", favorite = false, lastUsedAt = null)))

            assertThat(rowIds).containsExactly(-1L)
            val stored = dao.readFoodItems().single()
            assertThat(stored.name).isEqualTo("Apfel")
            assertThat(stored.isFavorite).isTrue()
            assertThat(stored.lastUsedAt).isEqualTo(100L)
        }

    @Test
    fun `insertFoodItems assigns fresh row ids and a second identical round inserts nothing`() =
        runTest {
            val foods = listOf(food("f1", name = "Apfel"), food("f2", name = "Milch"))

            val firstRound = dao.insertFoodItems(foods)
            assertThat(firstRound.count { it > 0L }).isEqualTo(2)
            assertThat(dao.readFoodItems().map { it.rowId }.count { it > 0 }).isEqualTo(2)

            val secondRound = dao.insertFoodItems(foods)
            assertThat(secondRound.count { it == -1L }).isEqualTo(2)
            assertThat(dao.countFoodItems()).isEqualTo(2)
        }

    @Test
    fun `insertRecipes insertDiaryEntries and insertWeightLogs skip existing primary keys`() =
        runTest {
            dao.insertAll(
                foods = listOf(food("f1")),
                recipes = listOf(RecipeFixtures.recipe(id = "r1")),
                ingredients = listOf(RecipeFixtures.ingredient(recipeId = "r1", foodId = "f1")),
                entries = listOf(entry(id = "d1")),
                weightLogs = listOf(weightLog()),
            )

            assertThat(dao.insertRecipes(listOf(RecipeFixtures.recipe(id = "r1")))).containsExactly(-1L)
            assertThat(dao.insertDiaryEntries(listOf(entry(id = "d1")))).containsExactly(-1L)
            assertThat(dao.insertWeightLogs(listOf(weightLog()))).containsExactly(-1L)
        }

    // --- Merge idempotency (A7/A19) ---

    @Test
    fun `insertAll called twice writes no second copy of any row`() =
        runTest {
            val foods = listOf(food("f1"), food("f2"))
            val recipes = listOf(RecipeFixtures.recipe(id = "r1"))
            val ingredients =
                listOf(
                    RecipeFixtures.ingredient(recipeId = "r1", foodId = "f1"),
                    RecipeFixtures.ingredient(recipeId = "r1", foodId = "f2"),
                )
            val entries = listOf(entry(id = "d1"))
            val logs = listOf(weightLog("2026-01-05"), weightLog("2026-01-06"))

            val first = dao.insertAll(foods, recipes, ingredients, entries, logs)
            assertThat(first.foodItems).isEqualTo(TableInsertCounts(inserted = 2, skipped = 0))
            assertThat(first.recipes).isEqualTo(TableInsertCounts(inserted = 1, skipped = 0))
            assertThat(first.recipeIngredients).isEqualTo(TableInsertCounts(inserted = 2, skipped = 0))
            assertThat(first.diaryEntries).isEqualTo(TableInsertCounts(inserted = 1, skipped = 0))
            assertThat(first.weightLogs).isEqualTo(TableInsertCounts(inserted = 2, skipped = 0))

            val second = dao.insertAll(foods, recipes, ingredients, entries, logs)
            assertThat(second.foodItems).isEqualTo(TableInsertCounts(inserted = 0, skipped = 2))
            assertThat(second.recipes).isEqualTo(TableInsertCounts(inserted = 0, skipped = 1))
            assertThat(second.recipeIngredients).isEqualTo(TableInsertCounts(inserted = 0, skipped = 2))
            assertThat(second.diaryEntries).isEqualTo(TableInsertCounts(inserted = 0, skipped = 1))
            assertThat(second.weightLogs).isEqualTo(TableInsertCounts(inserted = 0, skipped = 2))

            assertThat(dao.countFoodItems()).isEqualTo(2)
            assertThat(dao.countRecipes()).isEqualTo(1)
            assertThat(dao.countRecipeIngredients()).isEqualTo(2)
            assertThat(dao.countDiaryEntries()).isEqualTo(1)
            assertThat(dao.countWeightLogs()).isEqualTo(2)
        }

    @Test
    fun `count methods match the inserted row counts`() =
        runTest {
            dao.insertFoodItems(listOf(food("f1"), food("f2"), food("f3")))
            dao.insertRecipes(listOf(RecipeFixtures.recipe(id = "r1"), RecipeFixtures.recipe(id = "r2")))
            dao.insertRecipeIngredients(
                listOf(
                    RecipeFixtures.ingredient(recipeId = "r1", foodId = "f1"),
                    RecipeFixtures.ingredient(recipeId = "r2", foodId = "f2"),
                ),
            )
            dao.insertDiaryEntries(listOf(entry(id = "d1"), entry(id = "d2")))
            dao.insertWeightLogs(listOf(weightLog("2026-01-05"), weightLog("2026-01-06"), weightLog("2026-01-07")))

            assertThat(dao.countFoodItems()).isEqualTo(3)
            assertThat(dao.countRecipes()).isEqualTo(2)
            assertThat(dao.countRecipeIngredients()).isEqualTo(2)
            assertThat(dao.countDiaryEntries()).isEqualTo(2)
            assertThat(dao.countWeightLogs()).isEqualTo(3)
        }

    // --- FTS stays complete (A13) ---

    @Test
    fun `insertAll feeds the fts index so search finds imported and existing foods`() =
        runTest {
            db.foodItemDao().upsertFood(FoodItemFixtures.food(id = "existing", name = "Banane"))

            dao.insertAll(
                foods = listOf(food("imported", name = "Haferflocken")),
                recipes = emptyList(),
                ingredients = emptyList(),
                entries = emptyList(),
                weightLogs = emptyList(),
            )

            val hits = db.foodItemDao().searchByName("Haferflocken OR Banane")
            assertThat(hits.map { it.name }).containsExactlyInAnyOrder("Banane", "Haferflocken")
        }

    @Test
    fun `clearing food and recipe tables also clears the fts index`() =
        runTest {
            db.foodItemDao().upsertFood(FoodItemFixtures.food(id = "existing", name = "Banane"))
            dao.insertAll(
                foods = listOf(food("f1", name = "Haferflocken")),
                recipes = listOf(RecipeFixtures.recipe(id = "r1")),
                ingredients = listOf(RecipeFixtures.ingredient(recipeId = "r1", foodId = "f1")),
                entries = emptyList(),
                weightLogs = emptyList(),
            )

            dao.deleteAllRecipeIngredients()
            dao.deleteAllRecipes()
            dao.deleteAllFoodItems()

            assertThat(db.foodItemDao().searchByName("Haferflocken")).hasSize(0)
            assertThat(db.foodItemDao().searchByName("Banane")).hasSize(0)
            assertThat(dao.countFoodItems()).isEqualTo(0)
            assertThat(dao.countRecipes()).isEqualTo(0)
            assertThat(dao.countRecipeIngredients()).isEqualTo(0)
        }

    // --- Replace mode ---

    @Test
    fun `replaceAll wipes all five tables and stores only the new stock`() =
        runTest {
            dao.insertAll(
                foods = listOf(food("f1", name = "Haferflocken"), food("f2")),
                recipes = listOf(RecipeFixtures.recipe(id = "r1")),
                ingredients = listOf(RecipeFixtures.ingredient(recipeId = "r1", foodId = "f1")),
                entries = listOf(entry(id = "d1")),
                weightLogs = listOf(weightLog("2026-01-05")),
            )

            val report =
                dao.replaceAll(
                    foods = listOf(food("f9", name = "Quark")),
                    recipes = listOf(RecipeFixtures.recipe(id = "r9")),
                    ingredients = listOf(RecipeFixtures.ingredient(recipeId = "r9", foodId = "f9")),
                    entries = listOf(entry(id = "d9", foodId = "f9")),
                    weightLogs = listOf(weightLog("2026-02-01", 74.0)),
                )

            assertThat(report.foodItems).isEqualTo(TableInsertCounts(inserted = 1, skipped = 0))
            assertThat(report.recipes).isEqualTo(TableInsertCounts(inserted = 1, skipped = 0))
            assertThat(report.recipeIngredients).isEqualTo(TableInsertCounts(inserted = 1, skipped = 0))
            assertThat(report.diaryEntries).isEqualTo(TableInsertCounts(inserted = 1, skipped = 0))
            assertThat(report.weightLogs).isEqualTo(TableInsertCounts(inserted = 1, skipped = 0))

            assertThat(dao.readFoodItemIds()).containsExactly("f9")
            assertThat(dao.readRecipeIds()).containsExactly("r9")
            assertThat(dao.readRecipeIngredients().single().recipeId).isEqualTo("r9")
            assertThat(dao.readDiaryEntries().single().id).isEqualTo("d9")
            assertThat(dao.readWeightLogs()).containsExactly(weightLog("2026-02-01", 74.0))
            assertThat(db.foodItemDao().searchByName("Haferflocken")).hasSize(0)
        }
}
