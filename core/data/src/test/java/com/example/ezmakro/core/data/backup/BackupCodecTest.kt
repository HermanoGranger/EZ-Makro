package com.example.ezmakro.core.data.backup

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.example.ezmakro.core.data.backup.BackupCodec.DecodeError
import com.example.ezmakro.core.data.backup.BackupCodec.DecodeResult
import org.junit.jupiter.api.Test

class BackupCodecTest {
    @Test
    fun `round trip keeps the content identical`() {
        val content = fullContent()
        val document = fullDocument(content).copy(checksum = BackupCodec.checksum(content))

        val decoded = BackupCodec.decode(BackupCodec.encode(document)).expectSuccess()

        assertThat(decoded.document.content).isEqualTo(content)
        assertThat(decoded.checksumValid).isTrue()
    }

    @Test
    fun `checksum is stable for identical content`() {
        val content = fullContent()

        assertThat(BackupCodec.checksum(content)).isEqualTo(BackupCodec.checksum(content))
    }

    @Test
    fun `checksum changes when a nested value changes`() {
        val content = fullContent()
        val recipe = content.recipes.single()
        val ingredient = recipe.ingredients.single()
        val heavier = ingredient.copy(rawWeightGrams = 81.0)
        val changed = content.copy(recipes = listOf(recipe.copy(ingredients = listOf(heavier))))

        assertThat(BackupCodec.checksum(changed)).isNotEqualTo(BackupCodec.checksum(content))
    }

    @Test
    fun `checksumValid is true for a freshly generated document`() {
        val content = fullContent()
        val document = fullDocument(content).copy(checksum = BackupCodec.checksum(content))

        val decoded = BackupCodec.decode(BackupCodec.encode(document)).expectSuccess()

        assertThat(decoded.checksumValid).isTrue()
    }

    @Test
    fun `checksum mismatch is a flag and not a failure`() {
        val content = fullContent()
        val document = fullDocument(content).copy(checksum = "sha256:0000-not-the-real-checksum")

        val decoded = BackupCodec.decode(BackupCodec.encode(document)).expectSuccess()

        assertThat(decoded.checksumValid).isFalse()
    }

    @Test
    fun `minimal fixture decodes with a valid checksum`() {
        val decoded = BackupCodec.decode(fixture("backup-v1-minimal.json")).expectSuccess()

        assertThat(BackupCodec.checksum(decoded.document.content)).isEqualTo(decoded.document.checksum)
        assertThat(decoded.checksumValid).isTrue()
    }

    @Test
    fun `evolved fixture ignores unknown fields and falls back to defaults`() {
        val decoded = BackupCodec.decode(fixture("backup-v1-evolved.json")).expectSuccess()
        val content = decoded.document.content

        assertThat(decoded.checksumValid).isFalse()
        val food = content.foodItems.single()
        assertThat(food.name).isEqualTo("Future Oats")
        assertThat(food.cachedStatus).isEqualTo("SYNCED_WITH_CLOUD")
        assertThat(food.nutriProteinG).isNull()
        assertThat(food.lastUsedAt).isNull()
        assertThat(food.origName).isNull()
        val recipe = content.recipes.single()
        assertThat(recipe.category).isNull()
        assertThat(recipe.lastUsedAt).isNull()
        assertThat(recipe.portions).isEqualTo(1)
        assertThat(recipe.isFavorite).isFalse()
        assertThat(recipe.ingredients.single().servingCount).isNull()
        val diary = content.diaryEntries.single()
        assertThat(diary.foodItemId).isNull()
        val weightLog = content.weightLogs.single()
        assertThat(weightLog.kilograms).isEqualTo(81.25)
    }

    @Test
    fun `corrupt fixture fails as corrupted`() {
        val result = BackupCodec.decode(fixture("backup-corrupt.json"))

        assertThat(result).isEqualTo(DecodeResult.Failure(DecodeError.Corrupted))
    }

    @Test
    fun `a foreign json file is not a backup`() {
        val result = BackupCodec.decode("""{"foo":1}""")

        assertThat(result).isEqualTo(DecodeResult.Failure(DecodeError.NotAZBackup))
    }

    @Test
    fun `missing format field is reported as not a backup`() {
        val result = BackupCodec.decode("""{"formatVersion":1,"content":{}}""")

        assertThat(result).isEqualTo(DecodeResult.Failure(DecodeError.NotAZBackup))
    }

    @Test
    fun `missing formatVersion is reported as corrupted`() {
        val result = BackupCodec.decode("""{"format":"ezmakro.backup","content":{}}""")

        assertThat(result).isEqualTo(DecodeResult.Failure(DecodeError.Corrupted))
    }

    @Test
    fun `missing required frame field is reported as corrupted`() {
        val text =
            """
            {
              "format": "ezmakro.backup",
              "formatVersion": 1,
              "appVersionName": "1.0",
              "appVersionCode": 1,
              "exportedAtEpochMillis": 1750000000000,
              "dbSchemaVersion": 9
            }
            """.trimIndent()

        val result = BackupCodec.decode(text)

        assertThat(result).isEqualTo(DecodeResult.Failure(DecodeError.Corrupted))
    }

    @Test
    fun `missing checksum succeeds with checksumValid false`() {
        val text =
            """
            {
              "format": "ezmakro.backup",
              "formatVersion": 1,
              "appVersionName": "1.0",
              "appVersionCode": 1,
              "exportedAtEpochMillis": 1750000000000,
              "dbSchemaVersion": 9,
              "content": {}
            }
            """.trimIndent()

        val decoded = BackupCodec.decode(text).expectSuccess()

        assertThat(decoded.checksumValid).isFalse()
    }

    @Test
    fun `formatVersion from a newer app version is unsupported`() {
        val result = BackupCodec.decode("""{"format":"ezmakro.backup","formatVersion":99,"content":{}}""")

        assertThat(result).isEqualTo(DecodeResult.Failure(DecodeError.UnsupportedVersion(99, 1)))
    }

    @Test
    fun `formatVersion below the supported range is unsupported`() {
        val result = BackupCodec.decode("""{"format":"ezmakro.backup","formatVersion":0,"content":{}}""")

        assertThat(result).isEqualTo(DecodeResult.Failure(DecodeError.UnsupportedVersion(0, 1)))
    }

    @Test
    fun `canonical content does not depend on the settings order`() {
        val kcal = BackupSettingDto("goal_kcal", "2000.0")
        val protein = BackupSettingDto("goal_protein", "150.0")
        val first = BackupContentDto(settings = listOf(kcal, protein))
        val second = BackupContentDto(settings = listOf(protein, kcal))

        assertThat(BackupCodec.canonicalContent(first)).isEqualTo(BackupCodec.canonicalContent(second))
        assertThat(BackupCodec.checksum(first)).isEqualTo(BackupCodec.checksum(second))
    }

    @Test
    fun `the format carries no surrogate key and no fts section`() {
        val encoded = BackupCodec.encode(fullDocument(fullContent()))

        assertThat(encoded).doesNotContain("rowId", "row_id", "food_items_fts")
    }

    private fun DecodeResult.expectSuccess(): DecodeResult.Success {
        assertThat(this).isInstanceOf(DecodeResult.Success::class.java)
        return this as DecodeResult.Success
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/backup/$name")) { "missing fixture $name" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    private fun fullDocument(content: BackupContentDto): BackupDocumentDto =
        BackupDocumentDto(
            format = BackupCodec.FORMAT_ID,
            formatVersion = BackupCodec.FORMAT_VERSION,
            appVersionName = "1.0",
            appVersionCode = 1,
            exportedAtEpochMillis = 1750000000000L,
            dbSchemaVersion = 9,
            content = content,
        )

    private fun fullContent(): BackupContentDto =
        BackupContentDto(
            settings =
                listOf(
                    BackupSettingDto("goal_kcal", "2000.0"),
                    BackupSettingDto("goal_protein_g", "150.0"),
                ),
            foodItems = listOf(fullFoodItem()),
            recipes = listOf(fullRecipe()),
            diaryEntries = listOf(fullDiaryEntry()),
            weightLogs = listOf(BackupWeightLogDto(date = "2026-10-05", kilograms = 80.5)),
        )

    private fun fullFoodItem(): BackupFoodItemDto =
        BackupFoodItemDto(
            id = "off|12345",
            name = "Haferflocken äöü 🌾",
            barcode = "12345",
            nutriKcal = 379.0,
            nutriProteinG = 13.2,
            nutriFatG = 6.5,
            nutriCarbsG = 67.7,
            nutriFiberG = 10.1,
            servingLabel = "40 g",
            servingGramsEquivalent = 40.0,
            isUserCreated = true,
            cachedStatus = "USER_MODIFIED",
            origName = "Oats",
            origBarcode = "12345",
            origNutriKcal = 350.0,
            origNutriProteinG = 12.0,
            origNutriFatG = 6.0,
            origNutriCarbsG = 60.0,
            origNutriFiberG = 9.0,
            origServingLabel = null,
            origServingGramsEquivalent = null,
            origIsUserCreated = false,
            isFavorite = true,
            lastUsedAt = 1749000000000L,
        )

    private fun fullRecipe(): BackupRecipeDto =
        BackupRecipeDto(
            id = "recipe-1",
            name = "Porridge",
            finalWeightGrams = 500.0,
            portions = 4,
            category = "Frühstück",
            lastModifiedAt = 1749000000000L,
            isFavorite = false,
            lastUsedAt = null,
            ingredients =
                listOf(
                    BackupIngredientDto(
                        foodItemId = "off|12345",
                        rawWeightGrams = 80.0,
                        servingCount = null,
                        recipeSnapshotJson = """{"name":"Porridge","finalWeightGrams":500.0}""",
                    ),
                ),
        )

    private fun fullDiaryEntry(): BackupDiaryEntryDto =
        BackupDiaryEntryDto(
            id = "diary-1",
            slot = "BREAKFAST",
            date = "2026-10-05",
            foodItemId = null,
            recipeId = "recipe-1",
            amountGrams = null,
            portionCount = 1.0,
            recipeOverridden = false,
            foodSnapshot = """{"id":"off|12345","name":"Haferflocken"}""",
            recipeSnapshot = """{"id":"recipe-1","name":"Porridge"}""",
        )
}
