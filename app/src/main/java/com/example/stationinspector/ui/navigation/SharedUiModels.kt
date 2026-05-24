package com.example.stationinspector.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stationinspector.domain.model.PhotoZone
import com.example.stationinspector.ui.theme.CardContent
import com.example.stationinspector.ui.theme.ContentDark
import com.example.stationinspector.ui.theme.ContentLight
import com.example.stationinspector.ui.theme.clickableNoRipple

// ─────────────────────────────────────────────────────────────────────────────
//  Bottom Navigation Item & Bar
// ─────────────────────────────────────────────────────────────────────────────

enum class MainTab {
    STATION_LIST,
    MAP,
    EXPORT,
    SETTINGS
}

data class BottomNavItem(
    val tab:   MainTab,
    val label: String,
    val icon:  ImageVector
)

@Composable
fun BottomNavBar(
    selectedTab:   MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    val items = listOf(
        BottomNavItem(MainTab.STATION_LIST, "Work",     Icons.Default.Home),
        BottomNavItem(MainTab.MAP,          "Map",      Icons.Default.Map),
        BottomNavItem(MainTab.EXPORT,       "Export",   Icons.Default.Share),
        BottomNavItem(MainTab.SETTINGS,     "Settings", Icons.Default.Settings)
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color     = ContentLight.copy(alpha = 0.3f)
        )

        NavigationBar(
            containerColor = ContentDark,
            tonalElevation = 0.dp,
            modifier = Modifier.height(76.dp)
        ) {
            items.forEach { item ->
                val isSelected = item.tab == selectedTab
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabSelected(item.tab) },
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = item.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CardContent,
                        unselectedIconColor = ContentLight,
                        selectedTextColor = ContentLight,
                        unselectedTextColor = ContentLight.copy(alpha = 0.6f),
                        indicatorColor = ContentLight
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Zone display metadata — exactly 3 Czech zones
// ─────────────────────────────────────────────────────────────────────────────

data class ZoneMeta(
    val zone:  PhotoZone,
    val label: String,
    val icon:  ImageVector
)

val ZONE_LIST = listOf(
    ZoneMeta(PhotoZone.ENTRANCE, "Nádraží", Icons.Filled.Train),
    ZoneMeta(PhotoZone.PLATFORM, "Čekárna", Icons.Filled.AirlineSeatReclineExtra),
    ZoneMeta(PhotoZone.RESTROOM, "WC",      Icons.Filled.Wc)
)
