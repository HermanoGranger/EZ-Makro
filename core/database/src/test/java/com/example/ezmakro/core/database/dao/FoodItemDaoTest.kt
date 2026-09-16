package com.example.ezmakro.core.database.dao

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.example.ezmakro.core.database.DatabaseTest
import com.example.ezmakro.core.database.testutil.FoodItemFixtures
import com.example.ezmakro.core.database.testutil.upsertFood
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Validates [FoodItemDao]: CRUD, FTS4 search quality (umlauts, case,
 * partial matches) and the ADR-004 status-transition operations.
 */
@RunWith(RobolectricTestRunner::class)
class FoodItemDaoTest : DatabaseTest() {
    private val dao get() = db.foodItemDao()

    @Test
    fun `upsert then getById returns the stored entity`() {
        val entity = FoodItemFixtures.food(id = "abc", name = "Banane")
        dao.upsertFood(entity)

        assertThat(dao.getById("abc")?.name).isEqualTo("Banane")
        assertThat(dao.getById("abc")?.nutriKcal).isEqualTo(350.0)
    }

    @Test
    fun `upsert replaces an existing row (upsert semantics)`() =
        runTest {
            dao.upsertFood(FoodItemFixtures.food(id = "abc", name = "Alt"))
            dao.upsertFood(FoodItemFixtures.food(id = "abc", name = "Neu"))

            assertThat(dao.getById("abc")?.name).isEqualTo("Neu")
            dao.observeAll().test {
                assertThat(awaitItem().count { it.id == "abc" }).isEqualTo(1)
            }
        }

    @Test
    fun `delete removes the entity`() {
        dao.upsertFood(FoodItemFixtures.food(id = "abc"))
        dao.deleteById("abc")

        assertThat(dao.getById("abc")).isNull()
    }

    @Test
    fun `getByBarcode finds the cached entry`() {
        dao.upsertFood(FoodItemFixtures.food(id = "abc", barcode = "12345"))

        assertThat(dao.getByBarcode("12345")?.id).isEqualTo("abc")
        assertThat(dao.getByBarcode("00000")).isNull()
    }

    // --- FTS4 search quality (Phase 3 acceptance) ---

    @Test
    fun `fts matches case-insensitively`() {
        dao.upsertFood(FoodItemFixtures.food(id = "a", name = "Haferflocken"))
        dao.upsertFood(FoodItemFixtures.food(id = "b", name = "Vollkornbrot"))

        val results = dao.searchRowIds("HAFERFLOCKEN").resolve()
        assertThat(results).containsExactly("a")
    }

    @Test
    fun `fts matches umlauts without diacritics`() {
        dao.upsertFood(FoodItemFixtures.food(id = "a", name = "Müsli"))
        dao.upsertFood(FoodItemFixtures.food(id = "b", name = "Käse"))

        // Query folded to ASCII must still hit the umlaut name.
        val results = dao.searchRowIds("MUSLI").resolve()
        assertThat(results).containsExactly("a")
    }

    @Test
    fun `fts supports partial prefix matches`() {
        dao.upsertFood(FoodItemFixtures.food(id = "a", name = "Haferflocken"))
        dao.upsertFood(FoodItemFixtures.food(id = "b", name = "Haferkleie"))
        dao.upsertFood(FoodItemFixtures.food(id = "c", name = "Banane"))

        val results = dao.searchRowIds("Hafer*").resolve()
        assertThat(results).containsExactlyInAnyOrder("a", "b")
    }

    @Test
    fun `fts query with special characters is sanitized and does not crash`() {
        dao.upsertFood(FoodItemFixtures.food(id = "a", name = "Milch"))
        dao.upsertFood(FoodItemFixtures.food(id = "b", name = "Käse"))

        val query = FtsQuerySanitizer.sanitize("milch\"*)(") + "*"
        val results = dao.searchRowIds(query).resolve()
        assertThat(results).containsExactly("a")
    }

    @Test
    fun `FtsQuerySanitizer strips FTS metacharacters`() {
        assertThat(FtsQuerySanitizer.sanitize("\"*()+-:")).isEqualTo("")
        assertThat(FtsQuerySanitizer.sanitize("Hafer\"flocken")).isEqualTo("Haferflocken")
        assertThat(FtsQuerySanitizer.sanitize("milch 2%")).isEqualTo("milch 2%")
    }

    // --- ADR-004 status transitions ---

    @Test
    fun `markUserModified sets status to USER_MODIFIED`() {
        dao.upsertFood(FoodItemFixtures.food(id = "a", cachedStatus = "ORIGINAL"))

        dao.markUserModified("a")

        assertThat(dao.getById("a")?.cachedStatus).isEqualTo("USER_MODIFIED")
    }

    @Test
    fun `markUpdateAvailable does not overwrite USER_MODIFIED`() {
        dao.upsertFood(FoodItemFixtures.food(id = "a", cachedStatus = "USER_MODIFIED"))

        dao.markUpdateAvailable("a")

        assertThat(dao.getById("a")?.cachedStatus).isEqualTo("USER_MODIFIED")
    }

    @Test
    fun `markUpdateAvailable flags a pristine ORIGINAL entry`() {
        dao.upsertFood(FoodItemFixtures.food(id = "a", cachedStatus = "ORIGINAL"))

        dao.markUpdateAvailable("a")

        assertThat(dao.getById("a")?.cachedStatus).isEqualTo("UPDATE_AVAILABLE")
    }

    @Test
    fun `resetToOriginal restores snapshot and clears status`() {
        dao.upsertFood(
            FoodItemFixtures.food(
                id = "a",
                name = "Edited Name",
                cachedStatus = "USER_MODIFIED",
            ),
        )

        // Snapshot was stored as "Haferflocken" / 350 kcal at insert time.
        dao.resetToOriginal(
            id = "a",
            origName = "Haferflocken",
            origBarcode = null,
            origKcal = 350.0,
            origProteinG = 12.0,
            origFatG = 6.0,
            origCarbsG = 60.0,
            origFiberG = 8.0,
            origServingLabel = null,
            origServingGrams = null,
            origIsUserCreated = false,
        )

        val restored = dao.getById("a")
        assertThat(restored?.name).isEqualTo("Haferflocken")
        assertThat(restored?.cachedStatus).isEqualTo("ORIGINAL")
    }

    // --- Phase 5: favorites & recent ---

    @Test
    fun `setFavorite toggles the flag and observeFavorites reflects it`() =
        runTest {
            dao.upsertFood(FoodItemFixtures.food(id = "a", name = "Apfel"))
            dao.upsertFood(FoodItemFixtures.food(id = "b", name = "Banane"))

            dao.setFavorite("b", true)

            dao.observeFavorites().test {
                assertThat(awaitItem().map { it.id }).containsExactly("b")
            }
        }

    @Test
    fun `upsert does not clobber an existing favorite flag`() {
        dao.upsertFood(FoodItemFixtures.food(id = "a", name = "Apfel"))
        dao.setFavorite("a", true)

        dao.upsertFood(FoodItemFixtures.food(id = "a", name = "Apfel neu"))

        assertThat(dao.getById("a")?.isFavorite).isEqualTo(true)
        assertThat(dao.getById("a")?.name).isEqualTo("Apfel neu")
    }

    @Test
    fun `markUsed stamps last_used_at and observeRecent orders newest first`() =
        runTest {
            dao.upsertFood(FoodItemFixtures.food(id = "a", name = "Apfel"))
            dao.upsertFood(FoodItemFixtures.food(id = "b", name = "Banane"))
            dao.upsertFood(FoodItemFixtures.food(id = "c", name = "Clementine"))

            dao.markUsed("a", 100L)
            dao.markUsed("b", 300L)
            dao.markUsed("c", 200L)

            dao.observeRecent(limit = 2).test {
                assertThat(awaitItem().map { it.id }).containsExactly("b", "c")
            }
        }

    @Test
    fun `observeRecent excludes never-used foods`() =
        runTest {
            dao.upsertFood(FoodItemFixtures.food(id = "a", name = "Apfel"))
            dao.upsertFood(FoodItemFixtures.food(id = "b", name = "Banane"))
            dao.markUsed("a", 100L)

            dao.observeRecent(limit = 10).test {
                assertThat(awaitItem().map { it.id }).containsExactly("a")
            }
        }

    @Test
    fun `upsert does not clobber an existing last_used_at`() {
        dao.upsertFood(FoodItemFixtures.food(id = "a", name = "Apfel"))
        dao.markUsed("a", 500L)

        dao.upsertFood(FoodItemFixtures.food(id = "a", name = "Apfel neu"))

        assertThat(dao.getById("a")?.lastUsedAt).isEqualTo(500L)
    }

    private fun List<Int>.resolve(): List<String> = dao.getByRowIds(this).map { it.id }
}
