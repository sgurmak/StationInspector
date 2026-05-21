package com.example.stationinspector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.stationinspector.ui.export.ExportScreen
import com.example.stationinspector.ui.navigation.BottomNavBar

// ─────────────────────────────────────────────────────────────────────────────
//  App-wide gradient tokens
// ─────────────────────────────────────────────────────────────────────────────

val AppGradientTop    = Color(0xFF392153)
val AppGradientBottom = Color(0xFF13111A)

// ─────────────────────────────────────────────────────────────────────────────
//  MainAppScreen — root composable: owns Scaffold, gradient, and tab navigation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MainAppScreen(
    onStationClick:  (String, Int) -> Unit,
    onExportClick:   (String) -> Unit = {},
    onNavigateToPoi: (Double, Double, String) -> Unit = { _, _, _ -> }
) {
    val sharedViewModel: StationListViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val selectedDate by sharedViewModel.selectedDate.collectAsState()
    var currentTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            BottomNavBar(
                selectedTab   = currentTab,
                onTabSelected = { currentTab = it }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(AppGradientTop, AppGradientBottom)
                    )
                )
        ) {
            when (currentTab) {
                // ── Tab 0: Work / Station List ────────────────────────────────
                0 -> StationListScreen(
                    viewModel       = sharedViewModel,
                    onStationClick  = onStationClick,
                    onNavigateToPoi = onNavigateToPoi,
                    onNavigateToMap = { currentTab = 1 },
                    contentPadding  = paddingValues
                )

                // ── Tab 1: Map ────────────────────────────────────────────────
                1 -> MapScreen(
                    viewModel      = sharedViewModel,
                    contentPadding = paddingValues
                )

                // ── Tab 2: Export ─────────────────────────────────────────────
                // ExportScreen owns its own ViewModel and handles ZIP logic
                // internally — no callbacks needed from MainAppScreen.
                2 -> ExportScreen(
                    dateStr         = selectedDate?.toString() ?: "",
                    onBackClick     = { currentTab = 0 },
                    onExportSuccess = { currentTab = 0 },
                    contentPadding  = paddingValues
                )

                // ── Tab 3: Settings ───────────────────────────────────────────
                3 -> SettingsScreen(
                    onNavigateBack = { currentTab = 0 },
                    contentPadding = paddingValues
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Placeholder for tabs not yet implemented
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlaceholderScreen(label: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.Text(
            text     = label,
            color    = Color(0xFFFBF7FF),
            modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
        )
    }
}
