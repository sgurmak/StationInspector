package com.example.stationinspector.ui.inspection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.stationinspector.domain.model.PhotoType
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneGalleryScreen(
    viewModel: ZoneInspectionViewModel = hiltViewModel<ZoneInspectionViewModel>(),
    /**
     * TopAppBar back arrow → goes back to ZoneListScreen.
     * Wired in NavGraph as popBackStack("zone_list/$stationId").
     */
    onNavigateBack: () -> Unit,
    /**
     * "Додати фото" button → goes to CameraScreen for this zone.
     * Wired in NavGraph as navigate("camera/$stationId/$zoneName").
     */
    onAddPhoto: () -> Unit,
    /** Kept for NavGraph compatibility (unused — autosave replaces explicit confirm). */
    onConfirmZone: () -> Unit = {}
) {
    val photos by viewModel.photos.collectAsState()

    val regularCount = photos.count { it.type == PhotoType.CLIENT_REPORT }
    val defectCount  = photos.count { it.type == PhotoType.INTERNAL_DEFECT }

    // ── Auto-select the tab that has photos ───────────────────────────────────
    var selectedTab by remember { mutableStateOf(PhotoType.CLIENT_REPORT) }
    LaunchedEffect(regularCount, defectCount) {
        selectedTab = when {
            regularCount > 0 -> PhotoType.CLIENT_REPORT
            defectCount  > 0 -> PhotoType.INTERNAL_DEFECT
            else             -> PhotoType.CLIENT_REPORT
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text       = "Фотозвіт зони",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 17.sp,
                            color      = Color(0xFF1E293B)
                        )
                        Text(
                            text     = "Перегляд та управління фото",
                            fontSize = 12.sp,
                            color    = Color(0xFF64748B)
                        )
                    }
                },
                navigationIcon = {
                    // Back arrow → ZoneListScreen (NOT camera)
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад до списку зон"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onConfirmZone,
                        enabled = photos.isNotEmpty(),
                        modifier = Modifier.background(
                            color = if (photos.isNotEmpty()) Color.Green.copy(alpha = 0.15f) else Color.Transparent,
                            shape = CircleShape
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Zone Complete",
                            tint = if (photos.isNotEmpty()) Color.Green else Color.Gray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF9FAFB)
                )
            )
        },
        bottomBar = {
            Surface(
                color    = Color(0xFFF9FAFB),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Button(
                        onClick  = onAddPhoto,   // → CameraScreen
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape  = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor   = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddAPhoto,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text       = "Додати фото",
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(Color(0xFFF9FAFB))
        ) {
            // ── Category tabs with counters ───────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CategoryTab(
                    label       = "Звичайні фото",
                    count       = regularCount,
                    isSelected  = selectedTab == PhotoType.CLIENT_REPORT,
                    isEnabled   = regularCount > 0,
                    activeColor = Color(0xFF3B82F6),
                    onClick     = { selectedTab = PhotoType.CLIENT_REPORT },
                    modifier    = Modifier.weight(1f)
                )
                CategoryTab(
                    label       = "Косяки",
                    count       = defectCount,
                    isSelected  = selectedTab == PhotoType.INTERNAL_DEFECT,
                    isEnabled   = defectCount > 0,
                    activeColor = Color(0xFFEF4444),
                    onClick     = { selectedTab = PhotoType.INTERNAL_DEFECT },
                    modifier    = Modifier.weight(1f)
                )
            }

            // ── Photo grid / empty state ──────────────────────────────────
            val filteredPhotos = photos.filter { it.type == selectedTab }

            if (filteredPhotos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "📷", fontSize = 48.sp)
                        Text(
                            text       = "Фото відсутні",
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color      = Color(0xFF64748B)
                        )
                        Text(
                            text     = "Натисніть «Додати фото» для зйомки",
                            fontSize = 13.sp,
                            color    = Color(0xFF94A3B8)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredPhotos, key = { it.id }) { photo ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFE2E8F0))
                        ) {
                            AsyncImage(
                                model              = File(photo.localPath),
                                contentDescription = "Фото",
                                modifier           = Modifier.fillMaxSize(),
                                contentScale       = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(26.dp)
                                    .background(Color.White.copy(alpha = 0.85f), CircleShape)
                                    .clickable { viewModel.deletePhoto(photo.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Видалити",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Category Tab ──────────────────────────────────────────────────────────────

@Composable
private fun CategoryTab(
    label: String,
    count: Int,
    isSelected: Boolean,
    isEnabled: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected && isEnabled) activeColor.copy(alpha = 0.12f)
    else Color(0xFFF1F5F9)
    val borderColor    = if (isSelected && isEnabled) activeColor else Color.Transparent
    val textColor      = when {
        !isEnabled -> Color(0xFFCBD5E1)
        isSelected -> activeColor
        else       -> Color(0xFF64748B)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .then(if (isEnabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text       = label,
                fontSize   = 13.sp,
                fontWeight = if (isSelected && isEnabled) FontWeight.Bold else FontWeight.Medium,
                color      = textColor
            )
            Box(
                modifier = Modifier
                    .background(
                        color = if (isEnabled) activeColor else Color(0xFFE2E8F0),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = count.toString(),
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color      = if (isEnabled) Color.White else Color(0xFF94A3B8)
                )
            }
        }
    }
}
