package com.example.stationinspector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.stationinspector.ui.export.ExportScreen
import com.example.stationinspector.ui.navigation.BottomNavBar
import com.example.stationinspector.ui.navigation.MainTab
import com.example.stationinspector.ui.theme.AppGradientTop
import com.example.stationinspector.ui.theme.AppGradientBottom

// ─────────────────────────────────────────────────────────────────────────────
//  MainAppScreen — root composable: owns Scaffold, gradient, and tab navigation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MainAppScreen(
    onStationClick:  (String, Int) -> Unit,
    onNavigateToPoi: (Double, Double, String) -> Unit = { _, _, _ -> }
) {
    // RouteViewModel owns the selected date; the Export tab needs it for its
    // route argument. Child screens resolve their own ViewModels via
    // hiltViewModel(); because they share this NavBackStackEntry's
    // ViewModelStoreOwner, StationListScreen and MapScreen receive the SAME
    // RouteViewModel instance, preserving cross-tab route/date state.
    val routeViewModel: RouteViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val selectedDate by routeViewModel.selectedDate.collectAsState()
    // rememberSaveable so the selected tab survives process death / config change.
    // MainTab is an enum (Serializable), so no custom Saver is required.
    var currentTab by rememberSaveable { mutableStateOf(MainTab.STATION_LIST) }

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
                // ── Tab: Work / Station List ──────────────────────────────────
                MainTab.STATION_LIST -> StationListScreen(
                    onStationClick  = onStationClick,
                    onNavigateToPoi = onNavigateToPoi,
                    onNavigateToMap = { currentTab = MainTab.MAP },
                    contentPadding  = paddingValues
                )

                // ── Tab: Map ──────────────────────────────────────────────────
                MainTab.MAP -> MapScreen(
                    contentPadding = paddingValues
                )

                // ── Tab: Export ───────────────────────────────────────────────
                MainTab.EXPORT -> ExportScreen(
                    dateStr         = selectedDate?.toString() ?: "",
                    onBackClick     = { currentTab = MainTab.STATION_LIST },
                    onExportSuccess = { currentTab = MainTab.STATION_LIST },
                    contentPadding  = paddingValues
                )

                // ── Tab: Settings ─────────────────────────────────────────────
                MainTab.SETTINGS -> SettingsScreen(
                    onNavigateBack = { currentTab = MainTab.STATION_LIST },
                    contentPadding = paddingValues
                )
            }
        }
    }
}
