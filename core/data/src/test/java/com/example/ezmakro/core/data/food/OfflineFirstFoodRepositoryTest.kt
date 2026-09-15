package com.example.ezmakro.core.data.food

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.example.ezmakro.core.domain.datasource.FoodLocalDataSource
import com.example.ezmakro.core.domain.datasource.FoodRemoteDataSource
import com.example.ezmakro.core.domain.model.CachedFoodItemStatus
import com.example.ezmakro.core.domain.model.FoodId
import com.example.ezmakro.core.domain.model.FoodItem
import com.example.ezmakro.core.domain.model.NutritionValues
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.EmptyResult
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private fun food(
    id: String,
    name: String = "Food",
    barcode: String? = null,
    status: CachedFoodItemStatus = CachedFoodItemStatus.Original,
    hasSnapshot: Boolean = true,
): FoodItem {
    val base =
        FoodItem(
            id = FoodId(id),
            name = name,
            barcode = barcode,
            nutritionPer100g = NutritionValues(100.0, 1.0, 1.0, 1.0, 1.0),
            servingUnit = null,
            isUserCreated = false,
            cachedStatus = CachedFoodItemStatus.Original,
            originalSnapshot = null,
        )
    if (!hasSnapshot) return base.copy(cachedStatus = status)
    val withSnapshot = base.copy(originalSnapshot = base)
    return withSnapshot.copy(cachedStatus = status)
}

private class FakeLocal : FoodLocalDataSource {
    val store = MutableStateFlow<Map<String, FoodItem>>(emptyMap())

    override fun observeAll(): Flow<List<FoodItem>> = store.map { it.values.toList() }

    override fun observeFavorites(): Flow<List<FoodItem>> = store.map { m -> m.values.filter { it.isFavorite } }

    override fun observeRecent(limit: Int): Flow<List<FoodItem>> =
        store.map { m ->
            m.values
                .filter { it.lastUsedAt != null }
                .sortedByDescending { it.lastUsedAt }
                .take(limit)
        }

    override suspend fun getById(id: FoodId) = Result.Success(store.value[id.value])

    override suspend fun getByBarcode(barcode: String): Result.Success<FoodItem?> =
        Result.Success(store.value.values.firstOrNull { it.barcode == barcode })

    override fun searchByName(query: String): Flow<List<FoodItem>> =
        store.map { m -> m.values.filter { it.name.contains(query, ignoreCase = true) } }

    override suspend fun upsert(foodItem: FoodItem): EmptyResult<DataError.Local> {
        store.value = store.value + (foodItem.id.value to foodItem)
        return Result.Success(Unit)
    }

    override suspend fun upsertAll(foodItems: List<FoodItem>): EmptyResult<DataError.Local> {
        foodItems.forEach { upsert(it) }
        return Result.Success(Unit)
    }

    override suspend fun deleteById(id: FoodId): EmptyResult<DataError.Local> {
        store.value = store.value - id.value
        return Result.Success(Unit)
    }

    override suspend fun markUserModified(id: FoodId): EmptyResult<DataError.Local> {
        mutate(id) { it.copy(cachedStatus = CachedFoodItemStatus.UserModified) }
        return Result.Success(Unit)
    }

    override suspend fun markUpdateAvailable(id: FoodId): EmptyResult<DataError.Local> {
        mutate(id) {
            if (it.cachedStatus == CachedFoodItemStatus.UserModified) {
                it
            } else {
                it.copy(cachedStatus = CachedFoodItemStatus.UpdateAvailable)
            }
        }
        return Result.Success(Unit)
    }

    override suspend fun resetToOriginal(id: FoodId): EmptyResult<DataError.Local> {
        val snapshot = store.value[id.value]?.originalSnapshot ?: return Result.Success(Unit)
        store.value = store.value + (id.value to snapshot.copy(originalSnapshot = snapshot))
        return Result.Success(Unit)
    }

    override suspend fun setFavorite(
        id: FoodId,
        favorite: Boolean,
    ): EmptyResult<DataError.Local> {
        mutate(id) { it.copy(isFavorite = favorite) }
        return Result.Success(Unit)
    }

    override suspend fun markUsed(
        id: FoodId,
        usedAtEpochMillis: Long,
    ): EmptyResult<DataError.Local> {
        mutate(id) { it.copy(lastUsedAt = usedAtEpochMillis) }
        return Result.Success(Unit)
    }

    private fun mutate(
        id: FoodId,
        block: (FoodItem) -> FoodItem,
    ) {
        store.value[id.value]?.let { store.value = store.value + (id.value to block(it)) }
    }
}

private class FakeRemote(
    var searchResult: Result<List<FoodItem>, DataError.Network> = Result.Success(emptyList()),
    var barcodeResult: Result<FoodItem?, DataError.Network> = Result.Success(null),
) : FoodRemoteDataSource {
    override suspend fun searchByName(query: String) = searchResult

    override suspend fun getByBarcode(barcode: String) = barcodeResult
}

class OfflineFirstFoodRepositoryTest {
    @Test
    fun `searchRemote does not cache anymore`() =
        runTest {
            val local = FakeLocal()
            val remote = FakeRemote(searchResult = Result.Success(listOf(food("1", "Apfel"))))
            val repo = OfflineFirstFoodRepository(local, remote)

            repo.searchRemote("apf")

            assertThat(local.store.value["1"]).isNull()
        }

    @Test
    fun `searchRemote never overwrites a UserModified entry`() =
        runTest {
            val local = FakeLocal()
            local.upsert(food("1", "My Edit", status = CachedFoodItemStatus.UserModified))
            val remote = FakeRemote(searchResult = Result.Success(listOf(food("1", "OFF Name"))))
            val repo = OfflineFirstFoodRepository(local, remote)

            repo.searchRemote("x")

            assertThat(local.store.value["1"]?.name).isEqualTo("My Edit")
            assertThat(local.store.value["1"]?.cachedStatus)
                .isEqualTo(CachedFoodItemStatus.UserModified)
        }

    @Test
    fun `cacheRemoteItem saves and marks used`() =
        runTest {
            val local = FakeLocal()
            val repo = OfflineFirstFoodRepository(local, FakeRemote())

            repo.cacheRemoteItem(food("1", "Apfel"))

            val stored = local.store.value["1"]
            assertThat(stored?.name).isEqualTo("Apfel")
            assertThat(stored?.cachedStatus).isEqualTo(CachedFoodItemStatus.Original)
            assertThat(stored?.originalSnapshot).isNotNull()
            assertThat(stored?.lastUsedAt).isNotNull()
        }

    @Test
    fun `cacheRemoteItem does NOT overwrite UserModified`() =
        runTest {
            val local = FakeLocal()
            local.upsert(food("1", "My Edit", status = CachedFoodItemStatus.UserModified))
            val repo = OfflineFirstFoodRepository(local, FakeRemote())

            repo.cacheRemoteItem(food("1", "OFF Name"))

            assertThat(local.store.value["1"]?.name).isEqualTo("My Edit")
            assertThat(local.store.value["1"]?.cachedStatus)
                .isEqualTo(CachedFoodItemStatus.UserModified)
        }

    @Test
    fun `rememberRemoteItem keeps item in session without persisting`() =
        runTest {
            val local = FakeLocal()
            val repo = OfflineFirstFoodRepository(local, FakeRemote())

            repo.rememberRemoteItem(food("1", "Alpro", hasSnapshot = false))

            assertThat(local.store.value["1"]).isNull()
            assertThat(repo.getSessionItem(FoodId("1"))?.name).isEqualTo("Alpro")
        }

    @Test
    fun `toggleFavorite persists a session-remembered remote item`() =
        runTest {
            val local = FakeLocal()
            val repo = OfflineFirstFoodRepository(local, FakeRemote())
            repo.rememberRemoteItem(food("1", "Alpro", hasSnapshot = false))

            repo.toggleFavorite(FoodId("1"))

            assertThat(local.store.value["1"]?.name).isEqualTo("Alpro")
            assertThat(local.store.value["1"]?.isFavorite).isEqualTo(true)
        }

    @Test
    fun `getSessionItem is null for an unknown id`() =
        runTest {
            val repo = OfflineFirstFoodRepository(FakeLocal(), FakeRemote())

            assertThat(repo.getSessionItem(FoodId("missing"))).isNull()
        }

    @Test
    fun `lookupByBarcode returns local hit without calling remote`() =
        runTest {
            val local = FakeLocal()
            local.upsert(food("1", "Cached", barcode = "999"))
            val remote = FakeRemote(barcodeResult = Result.Error(DataError.Network.NO_INTERNET))
            val repo = OfflineFirstFoodRepository(local, remote)

            val result = repo.lookupByBarcode("999")

            assertThat((result as Result.Success).data?.name).isEqualTo("Cached")
        }

    @Test
    fun `lookupByBarcode falls back to remote and caches the hit`() =
        runTest {
            val local = FakeLocal()
            val remote = FakeRemote(barcodeResult = Result.Success(food("1", "Fresh", barcode = "999")))
            val repo = OfflineFirstFoodRepository(local, remote)

            val result = repo.lookupByBarcode("999")

            assertThat((result as Result.Success).data?.name).isEqualTo("Fresh")
            assertThat(local.store.value["1"]?.name).isEqualTo("Fresh")
        }

    @Test
    fun `lookupByBarcode returns null when neither source has the product`() =
        runTest {
            val repo = OfflineFirstFoodRepository(FakeLocal(), FakeRemote())

            val result = repo.lookupByBarcode("nope")

            assertThat(result).isInstanceOf(Result.Success::class)
            assertThat((result as Result.Success).data).isNull()
        }

    @Test
    fun `saveUserEdit flags a cached entry as UserModified`() =
        runTest {
            val local = FakeLocal()
            local.upsert(food("1", "Original"))
            val repo = OfflineFirstFoodRepository(local, FakeRemote())

            repo.saveUserEdit(food("1", "Edited"))

            assertThat(local.store.value["1"]?.cachedStatus)
                .isEqualTo(CachedFoodItemStatus.UserModified)
            assertThat(local.store.value["1"]?.name).isEqualTo("Edited")
        }

    @Test
    fun `resetToOriginal restores the snapshot`() =
        runTest {
            val local = FakeLocal()
            local.upsert(food("1", "Original"))
            val repo = OfflineFirstFoodRepository(local, FakeRemote())
            repo.saveUserEdit(food("1", "Edited"))

            repo.resetToOriginal(FoodId("1"))

            assertThat(local.store.value["1"]?.name).isEqualTo("Original")
            assertThat(local.store.value["1"]?.cachedStatus).isEqualTo(CachedFoodItemStatus.Original)
        }

    @Test
    fun `refetchFromOff replaces the local entry with the fresh OFF version`() =
        runTest {
            val local = FakeLocal()
            local.upsert(food("1", "Edited", barcode = "999", status = CachedFoodItemStatus.UserModified))
            val remote = FakeRemote(barcodeResult = Result.Success(food("1", "OFF Latest", barcode = "999")))
            val repo = OfflineFirstFoodRepository(local, remote)

            val result = repo.refetchFromOff(FoodId("1"))

            assertThat((result as Result.Success).data?.name).isEqualTo("OFF Latest")
            assertThat(local.store.value["1"]?.name).isEqualTo("OFF Latest")
            assertThat(local.store.value["1"]?.cachedStatus).isEqualTo(CachedFoodItemStatus.Original)
        }

    @Test
    fun `refetchFromOff errors when the entry has no barcode`() =
        runTest {
            val local = FakeLocal()
            local.upsert(food("1", "NoBarcode", barcode = null))
            val repo = OfflineFirstFoodRepository(local, FakeRemote())

            val result = repo.refetchFromOff(FoodId("1"))

            assertThat(result).isEqualTo(Result.Error(DataError.Local.NOT_FOUND))
        }

    @Test
    fun `toggleFavorite flips the favorite flag`() =
        runTest {
            val local = FakeLocal()
            local.upsert(food("1", "Apfel"))
            val repo = OfflineFirstFoodRepository(local, FakeRemote())

            repo.toggleFavorite(FoodId("1"))
            assertThat(local.store.value["1"]?.isFavorite).isEqualTo(true)

            repo.toggleFavorite(FoodId("1"))
            assertThat(local.store.value["1"]?.isFavorite).isEqualTo(false)
        }

    @Test
    fun `toggleFavorite errors for an unknown food`() =
        runTest {
            val repo = OfflineFirstFoodRepository(FakeLocal(), FakeRemote())

            val result = repo.toggleFavorite(FoodId("missing"))

            assertThat(result).isEqualTo(Result.Error(DataError.Local.NOT_FOUND))
        }

    @Test
    fun `markUsed stamps the injected clock time`() =
        runTest {
            val local = FakeLocal()
            local.upsert(food("1", "Apfel"))
            val repo = OfflineFirstFoodRepository(local, FakeRemote(), now = { 1_234L })

            repo.markUsed(FoodId("1"))

            assertThat(local.store.value["1"]?.lastUsedAt).isEqualTo(1_234L)
        }

    @Test
    fun `observeFavorites emits only favorited foods`() =
        runTest {
            val local = FakeLocal()
            local.upsert(food("1", "Apfel"))
            local.upsert(food("2", "Banane"))
            val repo = OfflineFirstFoodRepository(local, FakeRemote())
            repo.toggleFavorite(FoodId("2"))

            val favorites = repo.observeFavorites().first()

            assertThat(favorites.map { it.id.value }).isEqualTo(listOf("2"))
        }

    @Test
    fun `saveUserEdit on freshly cached off food creates snapshot from current state`() =
        runTest {
            val local = FakeLocal()
            // Simulate a freshly cached OFF entry that has no originalSnapshot
            // (e.g. pre-migration data or entry cached before the
            // cacheWithoutOverwritingUserEdits snapshot fix).
            val freshOffFood = food("1", "Original", hasSnapshot = false)
            local.upsert(freshOffFood)
            val repo = OfflineFirstFoodRepository(local, FakeRemote())

            repo.saveUserEdit(freshOffFood.copy(name = "Edited"))

            val stored = local.store.value["1"]
            assertThat(stored?.cachedStatus).isEqualTo(CachedFoodItemStatus.UserModified)
            assertThat(stored?.name).isEqualTo("Edited")
            // The fallback creates a snapshot from the current state
            assertThat(stored?.originalSnapshot).isNotNull()
            assertThat(stored?.originalSnapshot?.cachedStatus).isEqualTo(CachedFoodItemStatus.Original)
        }

    @Test
    fun `cacheWithoutOverwritingUserEdits stores snapshot for reset to original`() =
        runTest {
            val local = FakeLocal()
            val remote = FakeRemote(barcodeResult = Result.Success(food("1", "OFF Apfel", barcode = "999")))
            val repo = OfflineFirstFoodRepository(local, remote)

            repo.lookupByBarcode("999")

            val stored = local.store.value["1"]
            assertThat(stored?.originalSnapshot).isNotNull()
            assertThat(stored?.originalSnapshot?.name).isEqualTo("OFF Apfel")
            assertThat(stored?.originalSnapshot?.cachedStatus).isEqualTo(CachedFoodItemStatus.Original)
        }

    @Test
    fun `resetToOriginal works on freshly cached off food`() =
        runTest {
            val local = FakeLocal()
            val remote = FakeRemote(barcodeResult = Result.Success(food("1", "OFF Apfel", barcode = "999")))
            val repo = OfflineFirstFoodRepository(local, remote)

            repo.lookupByBarcode("999")
            repo.saveUserEdit(
                local.store.value["1"]!!.copy(name = "My Edit"),
            )
            repo.resetToOriginal(FoodId("1"))

            val stored = local.store.value["1"]
            assertThat(stored?.name).isEqualTo("OFF Apfel")
            assertThat(stored?.cachedStatus).isEqualTo(CachedFoodItemStatus.Original)
        }

    @Test
    fun `observeRecent returns most recently used first within the limit`() =
        runTest {
            val local = FakeLocal()
            local.upsert(food("1", "Apfel"))
            local.upsert(food("2", "Banane"))
            local.upsert(food("3", "Clementine"))
            local.markUsed(FoodId("1"), 100L)
            local.markUsed(FoodId("2"), 300L)
            local.markUsed(FoodId("3"), 200L)
            val repo = OfflineFirstFoodRepository(local, FakeRemote())

            val recent = repo.observeRecent(limit = 2).first()

            assertThat(recent.map { it.id.value }).isEqualTo(listOf("2", "3"))
        }
}
