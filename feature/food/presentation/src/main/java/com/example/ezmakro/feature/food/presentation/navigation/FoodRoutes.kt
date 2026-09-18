@file:Suppress("TooManyFunctions")

package com.example.ezmakro.feature.food.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.feature.food.presentation.detail.FoodDetailRoot
import com.example.ezmakro.feature.food.presentation.edit.FoodEditRoot
import com.example.ezmakro.feature.food.presentation.search.FoodSearchRoot
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream

// Route types — kept for serialization tests and potential future re-use.
// Navigation uses explicit string routes (below) to avoid
// KClass.serializer() companion-resolution edge case in Navigation 2.9.

@Serializable
object FoodSearch

@Serializable
data class FoodDetail(
    val foodId: String,
)

@Serializable
data class FoodEdit(
    val foodId: String? = null,
    val prefillBarcode: String? = null,
)

/**
 * Graph-level route for bottom navigation compatibility.
 */
@Serializable
object Food

// ---- Public string route constants ----

const val ROUTE_FOOD = "food"
const val ROUTE_SEARCH = "food_search"

// Route patterns for composable registration (include {param} placeholders)
internal const val ROUTE_DETAIL_PATTERN = "food_detail/{foodId}?slot={slot}&date={date}"
internal const val ROUTE_EDIT_PATTERN = "food_edit/{foodId}?prefillBarcode={prefillBarcode}"

// Route prefixes for navigation (no {param} placeholders; append values directly)
internal const val ROUTE_DETAIL = "food_detail"
internal const val ROUTE_EDIT = "food_edit"
internal const val ROUTE_DETAIL_PARAM_ID = "foodId"
internal const val ROUTE_EDIT_PARAM_ID = "foodId"
internal const val ROUTE_EDIT_PARAM_BARCODE = "prefillBarcode"
internal const val ROUTE_DETAIL_PARAM_SLOT = "slot"
internal const val ROUTE_DETAIL_PARAM_DATE = "date"

internal const val EDIT_ROUTE_MISSING_ID = "_none_"

/**
 * Percent-encodes a value so it can be embedded into a string route without
 * breaking path or query delimiters (`/`, `?`, `&`, `=`). Behaviorally
 * identical to `android.net.Uri.encode(value)`: letters, digits and the
 * default allow set (`_-!.~'()*`) stay literal, everything else is encoded
 * as UTF-8 `%XX` sequences.
 */
fun encodeRouteParam(value: String): String {
    if (value.isEmpty()) return value
    val sb = StringBuilder(value.length)
    for (ch in value) {
        if (isUnescaped(ch)) {
            sb.append(ch)
        } else {
            for (byte in ch.toString().toByteArray(Charsets.UTF_8)) {
                val unsigned = byte.toInt() and BYTE_MASK
                sb
                    .append('%')
                    .append(HEX_DIGITS[unsigned ushr NYBBLE_SHIFT])
                    .append(HEX_DIGITS[unsigned and NYBBLE_MASK])
            }
        }
    }
    return sb.toString()
}

/**
 * Percent-decodes a value read back from a route argument. Behaviorally
 * identical to `android.net.Uri.decode(value)`: `%XX` sequences are decoded
 * as UTF-8, invalid sequences are passed through literally.
 */
fun decodeRouteParam(value: String): String {
    if ('%' !in value) return value
    val sb = StringBuilder(value.length)
    val bytes = ByteArrayOutputStream()

    fun flushBytes() {
        if (bytes.size() == 0) return
        sb.append(bytes.toByteArray().toString(Charsets.UTF_8))
        bytes.reset()
    }

    var i = 0
    while (i < value.length) {
        val ch = value[i]
        if (ch == '%' && i + 2 < value.length && areHexDigits(value[i + 1], value[i + 2])) {
            bytes.write((hexValue(value[i + 1]) shl NYBBLE_SHIFT) or hexValue(value[i + 2]))
            i += PERCENT_SEQUENCE_LENGTH
        } else {
            flushBytes()
            sb.append(ch)
            i++
        }
    }
    flushBytes()
    return sb.toString()
}

private fun isUnescaped(ch: Char): Boolean = isAsciiLetterOrDigit(ch) || DEFAULT_UNESCAPED.contains(ch)

private fun isAsciiLetterOrDigit(ch: Char): Boolean = ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9'

private fun areHexDigits(
    a: Char,
    b: Char,
): Boolean = Character.digit(a, HEX_RADIX) >= 0 && Character.digit(b, HEX_RADIX) >= 0

private fun hexValue(c: Char): Int = Character.digit(c, HEX_RADIX)

private const val DEFAULT_UNESCAPED = "_-!.~'()*"
private const val HEX_DIGITS = "0123456789ABCDEF"
private const val BYTE_MASK = 0xFF
private const val NYBBLE_SHIFT = 4
private const val NYBBLE_MASK = 0xF
private const val PERCENT_SEQUENCE_LENGTH = 3
private const val HEX_RADIX = 16

/**
 * String-based food feature navigation graph.
 *
 * Uses explicit string routes for all destinations to avoid kotlinx.serialization
 * `KClass.serializer()` companion resolution edge case in Navigation 2.9.
 * The graph-level route [ROUTE_FOOD] matches [Food]'s serial name for bottom nav.
 */
fun NavGraphBuilder.foodGraph(
    navController: NavHostController,
    onNavigateBack: () -> Unit,
    onAddToDiary: (String, String, MealSlot, Double, Boolean) -> Unit = { _, _, _, _, _ -> },
    onNavigateToFoodDetail: (String) -> Unit = {},
    onNavigateToRecipeDetail: (String) -> Unit = {},
) {
    navigation(startDestination = ROUTE_SEARCH, route = ROUTE_FOOD) {
        searchComposable(navController, onAddToDiary, onNavigateToFoodDetail, onNavigateToRecipeDetail)
        detailComposable(navController, onNavigateBack, onAddToDiary)
        editComposable(navController, onNavigateBack)
    }
}

private fun NavGraphBuilder.searchComposable(
    navController: NavHostController,
    onAddToDiary: (String, String, MealSlot, Double, Boolean) -> Unit,
    onNavigateToFoodDetail: (String) -> Unit,
    onNavigateToRecipeDetail: (String) -> Unit,
) {
    composable(route = ROUTE_SEARCH) {
        FoodSearchRoot(
            onNavigateToFoodDetail = onNavigateToFoodDetail,
            onNavigateToRecipeDetail = onNavigateToRecipeDetail,
            onNavigateToCreate = { barcode ->
                val route =
                    if (barcode != null) {
                        "$ROUTE_EDIT/$EDIT_ROUTE_MISSING_ID?$ROUTE_EDIT_PARAM_BARCODE=${encodeRouteParam(barcode)}"
                    } else {
                        "$ROUTE_EDIT/$EDIT_ROUTE_MISSING_ID"
                    }
                navController.navigate(route)
            },
            onAddToDiary = onAddToDiary,
        )
    }
}

private fun NavGraphBuilder.detailComposable(
    navController: NavHostController,
    onNavigateBack: () -> Unit,
    onAddToDiary: (String, String, MealSlot, Double, Boolean) -> Unit,
) {
    composable(
        route = ROUTE_DETAIL_PATTERN,
        arguments =
            listOf(
                navArgument(ROUTE_DETAIL_PARAM_ID) { type = NavType.StringType },
                navArgument(ROUTE_DETAIL_PARAM_SLOT) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(ROUTE_DETAIL_PARAM_DATE) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
    ) {
        val foodId =
            it.arguments?.getString(ROUTE_DETAIL_PARAM_ID)?.let(::decodeRouteParam)
                ?: return@composable
        val slotArg = it.arguments?.getString(ROUTE_DETAIL_PARAM_SLOT).orEmpty()
        val date = it.arguments?.getString(ROUTE_DETAIL_PARAM_DATE).orEmpty()
        FoodDetailRoot(
            foodId = foodId,
            initialSlot = MealSlot.entries.firstOrNull { slot -> slot.name == slotArg },
            date = date,
            onNavigateBack = onNavigateBack,
            onNavigateToEdit = { id -> navController.navigate("$ROUTE_EDIT/${encodeRouteParam(id)}") },
            onAddToDiary = onAddToDiary,
        )
    }
}

private fun NavGraphBuilder.editComposable(
    navController: NavHostController,
    onNavigateBack: () -> Unit,
) {
    composable(
        route = ROUTE_EDIT_PATTERN,
        arguments =
            listOf(
                navArgument(ROUTE_EDIT_PARAM_ID) {
                    type = NavType.StringType
                    defaultValue = EDIT_ROUTE_MISSING_ID
                },
                navArgument(ROUTE_EDIT_PARAM_BARCODE) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
    ) {
        val foodId =
            it.arguments
                ?.getString(ROUTE_EDIT_PARAM_ID)
                ?.takeIf { it != EDIT_ROUTE_MISSING_ID }
                ?.let(::decodeRouteParam)
        val prefillBarcode =
            it.arguments?.getString(ROUTE_EDIT_PARAM_BARCODE)?.takeIf { it.isNotEmpty() }
        FoodEditRoot(
            foodId = foodId,
            prefillBarcode = prefillBarcode,
            onNavigateBack = onNavigateBack,
            onSaved = { navController.popBackStack() },
        )
    }
}
