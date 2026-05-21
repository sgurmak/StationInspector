package com.example.stationinspector.ui.zone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AirlineSeatReclineNormal
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stationinspector.domain.model.PhotoZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneListScreen(
    viewModel: ZoneListViewModel = hiltViewModel<ZoneListViewModel>(),
    onBackClick: () -> Unit,
    onZoneCameraClick: (zoneName: String) -> Unit,
    onZoneGalleryClick: (zoneName: String) -> Unit,
    onConfirmInspection: () -> Unit
) {
    val stationName by viewModel.stationName.collectAsState()
    val zones       by viewModel.zonesWithCounts.collectAsState()
    val totalPhotos by viewModel.totalPhotoCount.collectAsState()

    // ── Snackbar state ────────────────────────────────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope    = rememberCoroutineScope()

    Scaffold(
        // Snackbar anchored at the TOP by wrapping SnackbarHost in a Box
        // positioned at the top of the screen.
        snackbarHost = {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData   = data,
                        containerColor = Color(0xFF16A34A),
                        contentColor   = Color.White,
                        shape          = RoundedCornerShape(10.dp),
                        modifier       = Modifier.padding(16.dp)
                    )
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text       = stationName,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 17.sp,
                            color      = Color(0xFF1E293B)
                        )
                        Text(
                            text     = "Інспекція зон",
                            fontSize = 12.sp,
                            color    = Color(0xFF64748B)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF9FAFB)
                )
            )
        },
        bottomBar = {
            ConfirmInspectionBar(
                enabled = totalPhotos > 0,
                onClick = {
                    onConfirmInspection()
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF9FAFB))
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(zones, key = { it.zone.name }) { zoneStats ->
                ZoneCard(
                    stats = zoneStats,
                    onInfoAreaClick = {
                        if ((zoneStats.photoCount + zoneStats.issueCount) > 0) {
                            onZoneGalleryClick(zoneStats.zone.name)
                        }
                    },
                    onCameraClick = { onZoneCameraClick(zoneStats.zone.name) }
                )
            }
        }
    }
}

// ── Zone Card ─────────────────────────────────────────────────────────────────

@Composable
fun ZoneCard(
    stats: ZoneWithStats,
    onInfoAreaClick: () -> Unit,
    onCameraClick: () -> Unit
) {
    val hasPhotos  = (stats.photoCount + stats.issueCount) > 0
    val hasDefects = stats.issueCount > 0
    val cardBorder = if (hasDefects) Color(0xFFEF4444) else Color(0xFFE2E8F0)
    val borderWidth = if (hasDefects) 1.5.dp else 1.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(borderWidth, cardBorder, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // ── LEFT / INFO area (navigates to Gallery) ────────────────────
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        enabled = hasPhotos,
                        onClick = onInfoAreaClick
                    )
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = stats.zone.toIcon(),
                        contentDescription = null,
                        tint = if (hasPhotos) Color(0xFF334155) else Color(0xFF94A3B8),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = stats.displayName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp,
                        color      = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CounterChip(
                            label       = "Фото",
                            count       = stats.photoCount,
                            accentColor = Color(0xFF3B82F6)
                        )
                        CounterChip(
                            label       = "Косяки",
                            count       = stats.issueCount,
                            accentColor = Color(0xFFEF4444)
                        )
                    }
                }

                if (hasPhotos) {
                    Icon(
                        imageVector = Icons.Filled.Photo,
                        contentDescription = "Переглянути галерею",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── Vertical divider ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(64.dp)
                    .background(Color(0xFFF1F5F9))
            )

            // ── RIGHT / CAMERA action ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart    = 0.dp,
                            topEnd      = 14.dp,
                            bottomStart = 0.dp,
                            bottomEnd   = 14.dp
                        )
                    )
                    .clickable(onClick = onCameraClick)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Відкрити камеру",
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text     = "Знято",
                        fontSize = 10.sp,
                        color    = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

// ── Small counter chip ────────────────────────────────────────────────────────

@Composable
fun CounterChip(label: String, count: Int, accentColor: Color) {
    val active = count > 0
    Row(
        modifier = Modifier
            .background(
                color = if (active) accentColor.copy(alpha = 0.10f) else Color(0xFFF8FAFC),
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                width = 0.5.dp,
                color = if (active) accentColor.copy(alpha = 0.35f) else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text       = count.toString(),
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            color      = if (active) accentColor else Color(0xFFCBD5E1)
        )
        Text(
            text     = label,
            fontSize = 11.sp,
            color    = if (active) accentColor.copy(alpha = 0.75f) else Color(0xFFCBD5E1)
        )
    }
}

// ── Confirm Inspection bottom bar ─────────────────────────────────────────────

@Composable
fun ConfirmInspectionBar(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        color    = Color(0xFFF9FAFB),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Button(
                onClick  = onClick,
                enabled  = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor         = Color(0xFF16A34A),
                    contentColor           = Color.White,
                    disabledContainerColor = Color(0xFFD1D5DB),
                    disabledContentColor   = Color(0xFF9CA3AF)
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text       = "Підтвердити інспекцію",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Zone icon helper ──────────────────────────────────────────────────────────

fun PhotoZone.toIcon(): ImageVector = when (this) {
    PhotoZone.ENTRANCE -> Icons.Filled.AccountBalance
    PhotoZone.PLATFORM -> Icons.Filled.AirlineSeatReclineNormal
    PhotoZone.RESTROOM -> Icons.Filled.Wc
    else               -> Icons.Filled.AccountBalance
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFF9FAFB)
@Composable
private fun ZoneCardPreview() {
    val sampleZones = listOf(
        ZoneWithStats(PhotoZone.ENTRANCE, "Вокзал",            photoCount = 7, issueCount = 0),
        ZoneWithStats(PhotoZone.PLATFORM, "Зона очікування",   photoCount = 3, issueCount = 2),
        ZoneWithStats(PhotoZone.RESTROOM, "WC",                photoCount = 0, issueCount = 0)
    )
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        sampleZones.forEach { stats ->
            ZoneCard(stats = stats, onInfoAreaClick = {}, onCameraClick = {})
        }
        ConfirmInspectionBar(enabled = true, onClick = {})
    }
}
