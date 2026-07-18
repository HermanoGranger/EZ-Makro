package com.example.ezmakro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.ezmakro.core.designsystem.theme.EZMakroTheme
import com.example.ezmakro.feature.food.presentation.navigation.FoodSearch
import com.example.ezmakro.feature.food.presentation.navigation.foodGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EzMakroApp() }
    }
}

@Composable
private fun EzMakroApp() {
    EZMakroTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val navController = rememberNavController()
            val onNavigateBack: () -> Unit = { navController.popBackStack() }
            androidx.navigation.compose.NavHost(
                navController = navController,
                startDestination = FoodSearch,
            ) {
                foodGraph(navController = navController, onNavigateBack = onNavigateBack)
            }
        }
    }
}
