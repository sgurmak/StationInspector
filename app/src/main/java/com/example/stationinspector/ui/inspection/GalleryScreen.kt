package com.example.stationinspector.ui.inspection

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.stationinspector.domain.model.PhotoType
import com.example.stationinspector.domain.model.PhotoZone
import com.example.stationinspector.ui.navigation.ZONE_LIST
import com.example.stationinspector.ui.navigation.ZoneMeta
import com.example.stationinspector.ui.theme.AccentPink
import com.example.stationinspector.ui.theme.AccentGreenM3
import com.example.stationinspector.ui.theme.AppGradientTop
import com.example.stationinspector.ui.theme.AppGradientBottom
import com.example.stationinspector.ui.theme.ContentLight
import com.example.stationinspector.ui.theme.ContentDark
import com.example.stationinspector.ui.theme.clickableNoRipple
import java.io.File

@Composable
fun GalleryScreen(
    viewModel: ZoneInspectionViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onAddPhotoClick: (PhotoZone) -> Unit,
    onConfirmInspection: () -> Unit
) {
    val stationName by viewModel.stationName.collectAsState()
    val zoneCounts by viewModel.zoneCounts.collectAsState()
    val photos by viewModel.photos.collectAsState()
    val selectedZone by viewModel.selectedZone.collectAsState()

    var currentMode by rememberSaveable { mutableStateOf(PhotoType.CLIENT_REPORT) }
    var showSuccessBanner by rememberSaveable { mutableStateOf(false) }
    
    // Derived filtered photos for current zone & current mode
    val gridPhotos = photos.filter { it.type == currentMode }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppGradientTop, AppGradientBottom)))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 2. Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ContentLight
                    )
                }
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = stationName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Default,
                        color = ContentLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Gallery",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Default,
                        color = ContentLight
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickableNoRipple(onClick = { onAddPhotoClick(selectedZone) })
                        .padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddAPhoto,
                        contentDescription = "Add Photo",
                        tint = ContentLight,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add Photo",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Default,
                        color = ContentLight
                    )
                }
            }

            // 3. Zone Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ZONE_LIST.forEach { meta ->
                    val count = zoneCounts.find { it.zone == meta.zone }
                    val ordCount = count?.ordinaryCount ?: 0
                    val defCount = count?.defectCount ?: 0
                    val isActive = selectedZone == meta.zone

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickableNoRipple { viewModel.selectZone(meta.zone) }
                            .then(
                                if (isActive) Modifier.background(ContentLight, RoundedCornerShape(20.dp))
                                else Modifier
                            )
                            .padding(vertical = 8.dp, horizontal = 6.dp)
                            .width(86.dp)
                    ) {
                        // Top Part (Name and Icon)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = meta.label,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isActive) ContentDark else ContentLight
                            )
                            Spacer(Modifier.height(4.dp))
                            Icon(
                                imageVector = meta.icon,
                                contentDescription = null,
                                tint = if (isActive) ContentDark else ContentLight,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        // Counters format: [PhotoIcon] count | [DangerIcon] count
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = if (isActive) ContentDark else ContentLight,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = ordCount.toString(),
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                color = if (isActive) ContentDark else ContentLight
                            )
                            
                            Text(
                                text = "|",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 4.dp),
                                color = if (isActive) ContentDark.copy(alpha = 0.5f) else ContentLight.copy(alpha = 0.5f)
                            )

                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = AccentPink,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = defCount.toString(),
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                color = AccentPink
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 4. Gallery Mode Toggle
            val ordTotal = photos.count { it.type == PhotoType.CLIENT_REPORT }
            val defTotal = photos.count { it.type == PhotoType.INTERNAL_DEFECT }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Photos
                val isPhotosActive = currentMode == PhotoType.CLIENT_REPORT
                Box(
                    modifier = Modifier
                        .clickableNoRipple { currentMode = PhotoType.CLIENT_REPORT }
                        .then(
                            if (isPhotosActive) Modifier.background(ContentLight, RoundedCornerShape(16.dp))
                            else Modifier
                        )
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Photos: $ordTotal",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Default,
                        color = if (isPhotosActive) ContentDark else ContentLight
                    )
                }

                // Defects
                val isDefectsActive = currentMode == PhotoType.INTERNAL_DEFECT
                Box(
                    modifier = Modifier
                        .clickableNoRipple { currentMode = PhotoType.INTERNAL_DEFECT }
                        .then(
                            if (isDefectsActive) Modifier.background(ContentLight, RoundedCornerShape(16.dp))
                            else Modifier
                        )
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Defects: $defTotal",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Default,
                        color = AccentPink
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 5. Photo Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(gridPhotos, key = { it.id }) { photo ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.DarkGray)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(photo.localPath))
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = "Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // Trash icon overlay
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = AccentPink,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(24.dp)
                                .clickable { viewModel.deletePhoto(photo.id) }
                        )
                    }
                }
            }
        }

        // 6. Bottom Confirmation Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 16.dp)
                .height(44.dp)
                .background(ContentLight, RoundedCornerShape(12.dp))
                .clickable {
                    if (showSuccessBanner) return@clickable
                    showSuccessBanner = true
                    scope.launch {
                        delay(1500)
                        onConfirmInspection()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = AccentGreenM3,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Confirm inspection",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Default,
                    color = AccentGreenM3
                )
            }
        }

        // Success Banner Overlay
        AnimatedVisibility(
            visible = showSuccessBanner,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -40 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -40 }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .background(ContentDark, RoundedCornerShape(12.dp))
                    .border(1.dp, ContentLight.copy(alpha=0.1f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Success",
                    tint = AccentGreenM3,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Your report has been successfully saved.",
                    color = ContentLight,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Default
                )
            }
        }
    }
}
