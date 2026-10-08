package com.example.ezmakro.core.data.food.seed

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.example.ezmakro.core.data.datastore.SettingsDataStore
import com.example.ezmakro.core.database.EZDatabase
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * [BlsFoodSeeder] against a real in-memory Room database and a hand-written
 * seed document: versioned idempotency, the USER_MODIFIED protection and the
 * re-insertion of deleted seeds on a version bump (ADR-008).
 */
@RunWith(RobolectricTestRunner::class)
class BlsFoodSeederTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: EZDatabase
    private lateinit var settings: SettingsDataStore
    private lateinit var scope: CoroutineScope
    private lateinit var seeder: BlsFoodSeeder
    private val seedSource = FakeSeedFoodDataSource(document(version = 1, foodA(), foodB()))

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EZDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settings =
            SettingsDataStore(
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { File(tempFolder.root, "test_settings.preferences_pb") },
                ),
            )
        seeder = BlsFoodSeeder(seedSource = seedSource, dao = db.foodItemDao(), settings = settings)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    @Test
    fun `first run inserts every seed food and writes the version marker`() =
        runTest {
            val result = seeder.seedIfNeeded()

            assertThat((result as Result.Success).data).isEqualTo(Unit)
            val row = db.foodItemDao().getById("bls:A")
            assertThat(row).isNotNull()
            assertThat(row!!.name).isEqualTo("Apfel")
            assertThat(row.nutriKcal).isEqualTo(100.0)
            assertThat(row.nutriProteinG).isEqualTo(1.0)
            assertThat(row.nutriFatG).isEqualTo(2.0)
            assertThat(row.nutriCarbsG).isEqualTo(3.0)
            assertThat(row.nutriFiberG).isEqualTo(4.0)
            assertThat(row.barcode).isNull()
            assertThat(row.isUserCreated).isFalse()
            assertThat(row.cachedStatus).isEqualTo("ORIGINAL")
            assertThat(row.isFavorite).isFalse()
            assertThat(db.foodItemDao().getById("bls:B")).isNotNull()
            assertThat(settings.getString(BlsFoodSeeder.SEED_FOOD_VERSION_KEY).first()).isEqualTo("1")
        }

    @Test
    fun `seed rows carry their own values as original snapshot`() =
        runTest {
            seeder.seedIfNeeded()

            val row = db.foodItemDao().getById("bls:A")!!
            assertThat(row.origName).isEqualTo("Apfel")
            assertThat(row.origBarcode).isNull()
            assertThat(row.origNutriKcal).isEqualTo(100.0)
            assertThat(row.origNutriProteinG).isEqualTo(1.0)
            assertThat(row.origNutriFatG).isEqualTo(2.0)
            assertThat(row.origNutriCarbsG).isEqualTo(3.0)
            assertThat(row.origNutriFiberG).isEqualTo(4.0)
            assertThat(row.origIsUserCreated).isEqualTo(false)
        }

    @Test
    fun `second run with the same version is a no-op`() =
        runTest {
            seeder.seedIfNeeded()
            db.foodItemDao().upsert(
                id = "bls:A",
                name = "Edited locally",
                barcode = null,
                nutriKcal = 50.0,
                nutriProteinG = null,
                nutriFatG = null,
                nutriCarbsG = null,
                nutriFiberG = null,
                servingLabel = null,
                servingGramsEquivalent = null,
                isUserCreated = false,
                cachedStatus = "USER_MODIFIED",
                origName = "Apfel",
                origBarcode = null,
                origNutriKcal = 100.0,
                origNutriProteinG = 1.0,
                origNutriFatG = 2.0,
                origNutriCarbsG = 3.0,
                origNutriFiberG = 4.0,
                origServingLabel = null,
                origServingGrams = null,
                origIsUserCreated = false,
            )

            seeder.seedIfNeeded()

            val row = db.foodItemDao().getById("bls:A")!!
            assertThat(row.name).isEqualTo("Edited locally")
            assertThat(row.nutriKcal).isEqualTo(50.0)
            assertThat(settings.getString(BlsFoodSeeder.SEED_FOOD_VERSION_KEY).first()).isEqualTo("1")
        }

    @Test
    fun `version bump refreshes untouched seeds but never user edits`() =
        runTest {
            seeder.seedIfNeeded()
            // Simulate the repository's user edit of a cached seed (ADR-004).
            db.foodItemDao().markUserModified("bls:A")
            db.foodItemDao().upsert(
                id = "bls:A",
                name = "My Apfel",
                barcode = null,
                nutriKcal = 42.0,
                nutriProteinG = null,
                nutriFatG = null,
                nutriCarbsG = null,
                nutriFiberG = null,
                servingLabel = null,
                servingGramsEquivalent = null,
                isUserCreated = false,
                cachedStatus = "USER_MODIFIED",
                origName = "Apfel",
                origBarcode = null,
                origNutriKcal = 100.0,
                origNutriProteinG = 1.0,
                origNutriFatG = 2.0,
                origNutriCarbsG = 3.0,
                origNutriFiberG = 4.0,
                origServingLabel = null,
                origServingGrams = null,
                origIsUserCreated = false,
            )

            seedSource.document =
                document(
                    version = 2,
                    foodA().copy(name = "Apfel (neue Version)", kcal = 200.0),
                    foodB(),
                )
            seeder.seedIfNeeded()

            val edited = db.foodItemDao().getById("bls:A")!!
            assertThat(edited.name).isEqualTo("My Apfel")
            assertThat(edited.nutriKcal).isEqualTo(42.0)
            val refreshed = db.foodItemDao().getById("bls:B")!!
            assertThat(refreshed.name).isEqualTo(foodB().name)
            assertThat(settings.getString(BlsFoodSeeder.SEED_FOOD_VERSION_KEY).first()).isEqualTo("2")
        }

    @Test
    fun `version bump refreshes nutrition of an untouched seed`() =
        runTest {
            seeder.seedIfNeeded()
            seedSource.document =
                document(version = 2, foodA().copy(name = "Apfel (neue Version)", kcal = 200.0), foodB())

            seeder.seedIfNeeded()

            val row = db.foodItemDao().getById("bls:A")!!
            assertThat(row.name).isEqualTo("Apfel (neue Version)")
            assertThat(row.nutriKcal).isEqualTo(200.0)
            assertThat(row.origName).isEqualTo("Apfel (neue Version)")
            assertThat(row.origNutriKcal).isEqualTo(200.0)
            assertThat(row.cachedStatus).isEqualTo("ORIGINAL")
        }

    @Test
    fun `a deleted seed reappears on the next version bump`() =
        runTest {
            seeder.seedIfNeeded()
            db.foodItemDao().deleteById("bls:B")

            seedSource.document = document(version = 2, foodA(), foodB())
            seeder.seedIfNeeded()

            assertThat(db.foodItemDao().getById("bls:B")).isNotNull()
        }

    @Test
    fun `favorites and last-used survive a version bump`() =
        runTest {
            seeder.seedIfNeeded()
            db.foodItemDao().setFavorite("bls:A", true)
            db.foodItemDao().markUsed("bls:A", 123456789L)

            seedSource.document = document(version = 2, foodA().copy(kcal = 111.0), foodB())
            seeder.seedIfNeeded()

            val row = db.foodItemDao().getById("bls:A")!!
            assertThat(row.isFavorite).isTrue()
            assertThat(row.lastUsedAt).isEqualTo(123456789L)
            assertThat(row.nutriKcal).isEqualTo(111.0)
        }

    @Test
    fun `a failing load returns a typed error and writes no marker`() =
        runTest {
            seedSource.error = DataError.Local.UNKNOWN

            val result = seeder.seedIfNeeded()

            assertThat((result as Result.Error).error).isEqualTo(DataError.Local.UNKNOWN)
            assertThat(db.foodItemDao().getById("bls:A")).isNull()
            assertThat(settings.getString(BlsFoodSeeder.SEED_FOOD_VERSION_KEY).first()).isNull()
        }

    @Test
    fun `the bundled asset parses and matches the expected shape`() =
        runTest {
            val load = AssetSeedFoodDataSource(ApplicationProvider.getApplicationContext()).load()

            val document = (load as Result.Success).data
            assertThat(document.seedVersion).isEqualTo(1)
            assertThat(document.foods).hasSize(251)
            assertThat(document.source.license).isEqualTo("CC BY 4.0")
            assertThat(document.foods.first().id).isEqualTo("bls:F110600")
        }
}

private class FakeSeedFoodDataSource(
    var document: SeedFoodDocumentDto,
    var error: DataError.Local? = null,
) : SeedFoodDataSource {
    override suspend fun load(): Result<SeedFoodDocumentDto, DataError.Local> =
        error?.let { Result.Error(it) } ?: Result.Success(document)
}

private fun foodA() =
    SeedFoodDto(
        id = "bls:A",
        name = "Apfel",
        kcal = 100.0,
        proteinG = 1.0,
        fatG = 2.0,
        carbsG = 3.0,
        fiberG = 4.0,
    )

private fun foodB() =
    SeedFoodDto(
        id = "bls:B",
        name = "Brokkoli",
        kcal = 34.0,
        proteinG = 2.8,
        fatG = 0.4,
        carbsG = 7.0,
        fiberG = 2.6,
    )

private fun document(
    version: Int,
    vararg foods: SeedFoodDto,
) = SeedFoodDocumentDto(
    seedVersion = version,
    source = SeedSourceDto(name = "BLS", publisher = "MRI", license = "CC BY 4.0", citation = "cite", doi = "doi"),
    foods = foods.toList(),
)
