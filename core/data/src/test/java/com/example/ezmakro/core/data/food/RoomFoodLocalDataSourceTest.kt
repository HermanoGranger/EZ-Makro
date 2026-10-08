package com.example.ezmakro.core.data.food

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import com.example.ezmakro.core.database.EZDatabase
import com.example.ezmakro.core.database.entity.FoodItemEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [RoomFoodLocalDataSource.searchByName] against a real in-memory Room
 * database: the hybrid FTS-prefix + LIKE-contains merge that makes "Mehl"
 * find "Weizenmehl" without duplicating prefix hits (compound BLS seed names,
 * ADR-008).
 */
@RunWith(RobolectricTestRunner::class)
class RoomFoodLocalDataSourceTest {
    private lateinit var db: EZDatabase
    private lateinit var source: RoomFoodLocalDataSource

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EZDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        source = RoomFoodLocalDataSource(dao = db.foodItemDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `contains query finds compound names`() =
        runTest {
            insert("a" to "Weizenmehl Type 405", "b" to "Dinkelmehl", "c" to "Banane")

            val results = source.searchByName("mehl").first()

            // Ordered by name: "Dinkelmehl" before "Weizenmehl Type 405".
            assertThat(results.map { it.id.value }).containsExactly("b", "a")
        }

    @Test
    fun `multi term query matches independent of order and intervening words`() =
        runTest {
            insert(
                "a" to "Weizenmehl Type 550",
                "b" to "Weizenmehl Type 405",
                "c" to "Roggenmehl",
                "d" to "Zucker 550g",
            )

            // Every term must appear; "Type" in between and term order are irrelevant.
            assertThat(source.searchByName("mehl 550").first().map { it.id.value }).containsExactly("a")
            assertThat(source.searchByName("550 mehl").first().map { it.id.value }).containsExactly("a")
            assertThat(source.searchByName("weizenmehl 550 type").first().map { it.id.value }).containsExactly("a")
            assertThat(source.searchByName("mehl 999").first()).isEmpty()
        }

    @Test
    fun `prefix and contains matches are merged without duplicates`() =
        runTest {
            insert("a" to "Haferflocken", "b" to "Haferkleie", "c" to "Vollkornhaferbrot")

            val results = source.searchByName("hafer").first()

            assertThat(results.map { it.id.value }).containsExactly("a", "b", "c")
        }

    @Test
    fun `umlaut names match a folded query via FTS`() =
        runTest {
            insert("a" to "Müsli", "b" to "Käse", "c" to "Banane")

            val results = source.searchByName("musli").first()

            assertThat(results.map { it.id.value }).containsExactly("a")
        }

    @Test
    fun `percent in the query is matched literally, not as a wildcard`() =
        runTest {
            insert("a" to "Joghurt 2% Fett", "b" to "Milch", "c" to "Kaffee")

            val results = source.searchByName("2%").first()

            assertThat(results.map { it.id.value }).containsExactly("a")
        }

    @Test
    fun `results are ordered by name`() =
        runTest {
            insert("a" to "Zwiebel", "b" to "Apfel", "c" to "Erdnuss")

            val results = source.searchByName("e").first()

            assertThat(results.map { it.name }).containsExactly("Apfel", "Erdnuss", "Zwiebel")
        }

    @Test
    fun `blank query returns no results`() =
        runTest {
            insert("a" to "Banane")

            assertThat(source.searchByName("   ").first()).isEmpty()
        }

    @Test
    fun `query with parentheses matches via both search paths`() =
        runTest {
            insert("a" to "Milch (fettarm)", "b" to "Banane")

            val results = source.searchByName("(fettarm)").first()

            assertThat(results).hasSize(1)
            assertThat(results.map { it.id.value }).containsExactly("a")
        }

    private suspend fun insert(vararg pairs: Pair<String, String>) {
        pairs.forEach { (id, name) -> db.foodItemDao().upsertAll(listOf(entity(id, name))) }
    }

    private fun entity(
        id: String,
        name: String,
    ) = FoodItemEntity(
        id = id,
        name = name,
        barcode = null,
        nutriKcal = null,
        nutriProteinG = null,
        nutriFatG = null,
        nutriCarbsG = null,
        nutriFiberG = null,
        servingLabel = null,
        servingGramsEquivalent = null,
        isUserCreated = false,
        cachedStatus = "ORIGINAL",
        origName = name,
        origBarcode = null,
        origNutriKcal = null,
        origNutriProteinG = null,
        origNutriFatG = null,
        origNutriCarbsG = null,
        origNutriFiberG = null,
        origServingLabel = null,
        origServingGramsEquivalent = null,
        origIsUserCreated = false,
    )
}
