package com.example.stationinspector.ui.navigation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.stationinspector.ui.screens.StationListViewModel
import com.example.stationinspector.ui.export.ExportScreen
import com.example.stationinspector.ui.screens.MainAppScreen
import com.example.stationinspector.ui.inspection.CameraScreen
import com.example.stationinspector.ui.inspection.GalleryScreen
import com.example.stationinspector.domain.model.PhotoZone

@Composable
fun StationInspectorNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController    = navController,
        startDestination = "station_list?banner={banner}"
    ) {
        // ── Station List ─────────────────────────────────────────────────────
        composable(
            route = "station_list?banner={banner}",
            arguments = listOf(navArgument("banner") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            // MainAppScreen owns the Scaffold, gradient background, and BottomNavBar.
            // It receives navigation callbacks and forwards them to child screens.
            val context = LocalContext.current
            val vm: StationListViewModel = hiltViewModel()

            // Show success banner when returning from GalleryScreen confirm.
            val bannerResult by backStackEntry.savedStateHandle
                .getStateFlow<String?>("banner_result", null)
                .collectAsState()
            LaunchedEffect(bannerResult) {
                if (bannerResult == "success") {
                    vm.onInspectionConfirmed()
                    backStackEntry.savedStateHandle.remove<String>("banner_result")
                }
            }

            MainAppScreen(
                onStationClick = { stationId, totalPhotos ->
                    if (totalPhotos > 0) {
                        navController.navigate("gallery/$stationId/${PhotoZone.ENTRANCE.name}")
                    } else {
                        navController.navigate("camera/$stationId/${PhotoZone.ENTRANCE.name}")
                    }
                },
                onExportClick   = { dateStr -> navController.navigate("export/$dateStr") },
                onNavigateToPoi = { lat, lon, name ->
                    val uri       = Uri.parse("geo:$lat,$lon?q=${Uri.encode(name)}")
                    val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                    try {
                        context.startActivity(mapIntent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "Navigation app not found", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }


        // ── Camera ───────────────────────────────────────────────────────────
        composable(
            route = "camera/{stationId}/{zoneName}",
            arguments = listOf(
                navArgument("stationId") { type = NavType.StringType },
                navArgument("zoneName")  { type = NavType.StringType }
            )
        ) {
            val stationId = it.arguments?.getString("stationId") ?: ""
            val zoneName  = it.arguments?.getString("zoneName")  ?: ""
            CameraScreen(
                onNavigateToGallery = {
                    navController.navigate("gallery/$stationId/$zoneName") {
                        popUpTo("camera/$stationId/$zoneName") { inclusive = true }
                    }
                }
            )
        }

        // ── Gallery ──────────────────────────────────────────────────────────
        composable(
            route = "gallery/{stationId}/{zoneName}",
            arguments = listOf(
                navArgument("stationId") { type = NavType.StringType },
                navArgument("zoneName")  { type = NavType.StringType }
            )
        ) {
            val stationId = it.arguments?.getString("stationId") ?: ""
            val zoneName  = it.arguments?.getString("zoneName")  ?: ""
            GalleryScreen(
                // Back arrow → pop back stack
                onNavigateBack = {
                    navController.popBackStack()
                },
                // "Додати фото" → open CameraScreen for this zone
                onAddPhotoClick = {
                    navController.navigate("camera/$stationId/$zoneName") {
                        popUpTo("gallery/$stationId/$zoneName") { inclusive = true }
                    }
                },
                onConfirmInspection = {
                    navController
                        .getBackStackEntry("station_list?banner={banner}")
                        .savedStateHandle["banner_result"] = "success"
                    navController.popBackStack(
                        route     = "station_list?banner={banner}",
                        inclusive = false
                    )
                }
            )
        }

        // ── Export ───────────────────────────────────────────────────────────
        composable(
            route = "export/{date}",
            arguments = listOf(navArgument("date") { type = NavType.StringType })
        ) { backStackEntry ->
            val dateStr = backStackEntry.arguments?.getString("date") ?: ""
            ExportScreen(
                dateStr = dateStr,
                onBackClick = { navController.popBackStack() },
                onExportSuccess = {
                    navController.navigate("station_list?banner=success") {
                        popUpTo("station_list") { inclusive = true }
                    }
                }
            )
        }
    }
}