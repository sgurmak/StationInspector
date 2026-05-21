package com.example.stationinspector.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stationinspector.domain.model.PhotoZone
import com.example.stationinspector.ui.theme.CardContent
import com.example.stationinspector.ui.theme.ContentLight
import com.example.stationinspector.ui.theme.NavBarBg
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavBarBg)
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color     = ContentLight.copy(alpha = 0.3f)
        )

        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .height(60.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { item ->
                val isSelected = item.tab == selectedTab
                val clickAction: () -> Unit = { onTabSelected(item.tab) }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickableNoRipple(onClick = clickAction),
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = if (isSelected) {
                            Modifier
                                .width(50.dp)
                                .height(26.dp)
                                .background(ContentLight, RoundedCornerShape(12.dp))
                        } else {
                            Modifier.width(50.dp).height(26.dp)
                        }
                    ) {
                        Icon(
                            imageVector        = item.icon,
                            contentDescription = item.label,
                            tint               = if (isSelected) CardContent else ContentLight,
                            modifier           = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text       = item.label,
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color      = ContentLight,
                        maxLines   = 1
                    )
                }
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
