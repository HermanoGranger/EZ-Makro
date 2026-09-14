package com.example.ezmakro.feature.foodpicker.presentation.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ezmakro.core.domain.model.MealSlot
import com.example.ezmakro.core.presentation.diary.AddToDiarySheet
import com.example.ezmakro.core.presentation.util.ObserveAsEvents
import com.example.ezmakro.feature.foodpicker.presentation.R
import com.example.ezmakro.feature.foodpicker.presentation.picker.barcode.BarcodeTab
import com.example.ezmakro.feature.foodpicker.presentation.picker.myfoods.MyFoodsTab
import com.example.ezmakro.feature.foodpicker.presentation.picker.search.SearchTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodPickerSheet(
    isVisible: Boolean,
    initialSlot: MealSlot? = null,
    date: String? = null,
    onAddToDiary: (
        foodId: String,
        isRecipe: Boolean,
        date: String,
        slot: MealSlot,
        amount: Double,
        isGrams: Boolean,
    ) -> Unit,
    onAddAsIngredient: (itemId: String, itemName: String, servingLabel: String?, isRecipe: Boolean) -> Unit,
    onNavigateToFoodCreate: (barcode: String?) -> Unit,
    onNavigateToQuickAdd: (date: String, slot: MealSlot) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
    viewModel: FoodPickerViewModel,
) {
    if (!isVisible) return

    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(initialSlot, date) {
        initialSlot?.let { viewModel.onAction(FoodPickerAction.OnSlotSet(it)) }
        date?.let { viewModel.onAction(FoodPickerAction.OnDateSet(it)) }
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is FoodPickerEvent.AddToDiary ->
                onAddToDiary(event.foodId, event.isRecipe, event.date, event.slot, event.amount, event.isGrams)

            is FoodPickerEvent.AddAsIngredient ->
                onAddAsIngredient(event.itemId, event.itemName, event.servingLabel, event.isRecipe)

            is FoodPickerEvent.NavigateToFoodDetail -> { /* handled by caller */ }
            is FoodPickerEvent.NavigateToFoodCreate -> onNavigateToFoodCreate(event.barcode)
            is FoodPickerEvent.NavigateToQuickAdd -> onNavigateToQuickAdd(event.date, event.slot)
            FoodPickerEvent.Dismissed -> onDismiss()
        }
    }

    if (state.selectedItemReady) {
        AddToDiarySheet(
            isRecipe = state.selectedItemIsRecipe,
            hasServingUnit = state.selectedItemHasServingUnit,
            fixedSlot = state.slot,
            onDismiss = { viewModel.onAction(FoodPickerAction.OnDismissSelection) },
            onConfirm = { slot, amount, isGrams ->
                viewModel.onAction(
                    FoodPickerAction.OnConfirmAddToDiary(slot = slot, amount = amount, isGrams = isGrams),
                )
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = remember { RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp) },
    ) {
        FoodPickerContent(
            state = state,
            onAction = viewModel::onAction,
            modifier = Modifier.fillMaxWidth().height(500.dp),
        )
    }
}

@Composable
fun FoodPickerContent(
    state: FoodPickerState,
    onAction: (FoodPickerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (state.targetContext == FoodPickerTarget.DIARY) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { onAction(FoodPickerAction.OnQuickAddClick) }) {
                    Text(stringResource(R.string.foodpicker_quick_add))
                }
            }
        }

        TabRow(selectedTabIndex = state.activeTab.ordinal) {
            FoodPickerTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = state.activeTab == tab,
                    onClick = { onAction(FoodPickerAction.OnTabSelected(tab)) },
                    text = { Text(stringResource(tab.labelRes)) },
                    icon = { Icon(tab.icon, contentDescription = null) },
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state.activeTab) {
                FoodPickerTab.BARCODE ->
                    BarcodeTab(
                        state = state,
                        onAction = onAction,
                        modifier = Modifier.fillMaxSize(),
                    )
                FoodPickerTab.SEARCH -> SearchTab(state = state, onAction = onAction)
                FoodPickerTab.FAVORITES -> MyFoodsTab(state = state, onAction = onAction)
            }
        }
    }
}

private val FoodPickerTab.icon: ImageVector
    get() =
        when (this) {
            FoodPickerTab.BARCODE -> Icons.Filled.CameraAlt
            FoodPickerTab.SEARCH -> Icons.Filled.Search
            FoodPickerTab.FAVORITES -> Icons.Filled.Favorite
        }

private val FoodPickerTab.labelRes: Int
    get() =
        when (this) {
            FoodPickerTab.BARCODE -> R.string.foodpicker_tab_barcode
            FoodPickerTab.SEARCH -> R.string.foodpicker_tab_search
            FoodPickerTab.FAVORITES -> R.string.foodpicker_tab_favorites
        }
