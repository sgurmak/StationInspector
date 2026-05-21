package com.example.stationinspector.ui.inspection

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirlineSeatReclineExtra
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.stationinspector.domain.model.PhotoType
import com.example.stationinspector.domain.model.PhotoZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
//  Zone display metadata — exactly 3 Czech zones
// ─────────────────────────────────────────────────────────────────────────────

internal data class ZoneMeta(
    val zone:  PhotoZone,
    val label: String,
    val icon:  ImageVector
)

internal val ZONE_LIST = listOf(
    ZoneMeta(PhotoZone.ENTRANCE, "Nádraží", Icons.Filled.Train),
    ZoneMeta(PhotoZone.PLATFORM, "Čekárna", Icons.Filled.AirlineSeatReclineExtra),
    ZoneMeta(PhotoZone.RESTROOM, "WC",      Icons.Filled.Wc)
)

// ─────────────────────────────────────────────────────────────────────────────
//  Design tokens
// ─────────────────────────────────────────────────────────────────────────────

private val CamLight  = Color(0xFFFBF7FF)
private val CamDark   = Color(0xFF13111B)
private val CamAccent = Color(0xFFCA065E)
private val CamGreen  = Color(0xFF4ADE80)

// Thumbnail / button assembly height (same to align vertically)
private val CONTROL_HEIGHT = 102.dp

// ─────────────────────────────────────────────────────────────────────────────
//  CameraScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CameraScreen(
    viewModel:           ZoneInspectionViewModel = hiltViewModel(),
    onNavigateToGallery: () -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope          = rememberCoroutineScope()

    val selectedZone by viewModel.selectedZone.collectAsState()
    val zoneCounts   by viewModel.zoneCounts.collectAsState()
    val photos       by viewModel.photos.collectAsState()
    val stationName  by viewModel.stationName.collectAsState()

    var isFlashing by remember { mutableStateOf(false) }
    var flashMode  by remember { mutableStateOf(ImageCapture.FLASH_MODE_AUTO) }
    // ── Camera permission ─────────────────────────────────────────────────────
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCameraPermission = it }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Root — strictly black, full screen
    // ─────────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        // ── Camera preview — full-screen, untouched ───────────────────────────
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FIT_CENTER
                        viewModel.initializeCamera(lifecycleOwner, surfaceProvider)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoomChange, _ ->
                            viewModel.onZoomChange(zoomChange)
                        }
                    }
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Camera permission required", color = Color.White)
            }
        }

        // ── White flash overlay ───────────────────────────────────────────────
        if (isFlashing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.5f))
            )
        }

        // ─────────────────────────────────────────────────────────────────────
        //  TOP NAVIGATION BAR
        //  [Flash(fixed left)] [Zones — SpaceEvenly, full remaining width] [✓(fixed right)]
        // ─────────────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Flash toggle (fixed left anchor) ──────────────────────────────
            IconButton(
                onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON   -> ImageCapture.FLASH_MODE_OFF
                        else                         -> ImageCapture.FLASH_MODE_AUTO
                    }
                    viewModel.setFlashMode(flashMode)
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON  -> Icons.Filled.FlashOn
                        ImageCapture.FLASH_MODE_OFF -> Icons.Filled.FlashOff
                        else                        -> Icons.Filled.FlashAuto
                    },
                    contentDescription = "Flash",
                    tint               = CamLight,
                    modifier           = Modifier.size(24.dp)
                )
            }

            // ── Zone selector — fills all space between flash and checkmark ───
            Row(
                modifier              = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                ZONE_LIST.forEach { meta ->
                    val count    = zoneCounts.find { it.zone == meta.zone }
                    val isActive = meta.zone == selectedZone
                    ZoneItem(
                        meta       = meta,
                        ordCount   = count?.ordinaryCount ?: 0,
                        defCount   = count?.defectCount ?: 0,
                        isSelected = isActive,
                        onClick    = { viewModel.selectZone(meta.zone) }
                    )
                }
            }

            // ── Done / gallery checkmark (fixed right anchor) ─────────────────
            IconButton(
                onClick  = onNavigateToGallery,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector        = Icons.Filled.CheckCircle,
                    contentDescription = "Finish",
                    tint               = CamGreen,
                    modifier           = Modifier.size(36.dp)
                )
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        //  BOTTOM AREA — station name + controls row
        //  navigationBarsPadding() + bottom padding keeps it above system UI
        // ─────────────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Station name — centered, above controls, clear of viewfinder ──
            if (stationName.isNotEmpty()) {
                Text(
                    text       = stationName,
                    fontSize   = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = CamLight,
                    textAlign  = TextAlign.Center,
                    maxLines   = 1,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .padding(bottom = 14.dp)
                )
            }

            // ── Bottom controls row ───────────────────────────────────────────
            val lastPhoto = photos.maxByOrNull { it.timestamp }

            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .height(CONTROL_HEIGHT)
                    .padding(start = 20.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Thumbnail (102×102, left, 20dp from edge) ─────────────────
                Box(
                    modifier = Modifier
                        .size(CONTROL_HEIGHT)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    if (lastPhoto != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(lastPhoto.localPath))
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = "Last photo",
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize()
                        )
                        // Delete icon — #CA065E
                        Icon(
                            imageVector        = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint               = CamAccent,
                            modifier           = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(24.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication        = null
                                ) { viewModel.deletePhoto(lastPhoto.id) }
                        )
                    } else {
                        Icon(
                            imageVector        = Icons.Filled.Collections,
                            contentDescription = "No photos",
                            tint               = CamLight.copy(alpha = 0.35f),
                            modifier           = Modifier.size(32.dp)
                        )
                    }
                }

                // Push buttons to the right
                Spacer(Modifier.weight(1f))

                // ── Capture buttons — Defects | 40dp | Photo ──────────────────
                Row(
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    ShutterButton(
                        outerBorderColor = CamLight,
                        innerFillColor   = CamAccent,
                        label            = "Defects",
                        labelColor       = CamAccent,
                        onClick          = {
                            viewModel.capturePhoto(PhotoType.INTERNAL_DEFECT)
                            scope.launch {
                                isFlashing = true; delay(80); isFlashing = false
                            }
                        }
                    )
                    ShutterButton(
                        outerBorderColor = CamLight,
                        innerFillColor   = CamLight,
                        label            = "Photo",
                        labelColor       = CamLight,
                        onClick          = {
                            viewModel.capturePhoto(PhotoType.CLIENT_REPORT)
                            scope.launch {
                                isFlashing = true; delay(80); isFlashing = false
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ZoneItem — two-story design
//
//  Story 1 (Pill): fixed 86×64dp Column — Name on top, Icon below.
//    • Selected:   white (#FBF7FF) pill bg, content #13111B.
//    • Unselected: transparent bg, content #FBF7FF.
//
//  Story 2 (Counters): below the pill, static colors regardless of selection.
//    • Ordinary count + "|" separator → always #FBF7FF.
//    • Defect count                   → always #CA065E.
//    • Fixed "|" anchor: numbers grow outward (widthIn min=20dp).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ZoneItem(
    meta:       ZoneMeta,
    ordCount:   Int,
    defCount:   Int,
    isSelected: Boolean,
    onClick:    () -> Unit
) {
    val contentColor = if (isSelected) CamDark else CamLight

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick
            )
    ) {
        // ── Story 1: Pill ─────────────────────────────────────────────────────
        // Fixed dimensions guarantee all three zones are the same size,
        // even "WC" (2 chars) matches "Nádraží" (7 chars) visually.
        Column(
            modifier = Modifier
                .width(86.dp)
                .height(64.dp)
                .then(
                    if (isSelected)
                        Modifier.background(CamLight, RoundedCornerShape(50))
                    else
                        Modifier
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Zone name — top of pill
            Text(
                text       = meta.label,
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color      = contentColor,
                maxLines   = 1,
                textAlign  = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            // Zone icon — bottom of pill
            Icon(
                imageVector        = meta.icon,
                contentDescription = meta.label,
                tint               = contentColor,
                modifier           = Modifier.size(30.dp)
            )
        }

        Spacer(Modifier.height(4.dp))

        // ── Story 2: Counters (below pill, static colors) ─────────────────────
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Ordinary count — right-aligned, grows LEFT — always #FBF7FF
            Text(
                text      = ordCount.toString(),
                fontSize  = 14.sp,
                color     = CamLight,
                textAlign = TextAlign.End,
                modifier  = Modifier.widthIn(min = 20.dp)
            )
            // Fixed "|" separator — always #FBF7FF (dimmed)
            Text(
                text     = " | ",
                fontSize = 14.sp,
                color    = CamLight.copy(alpha = 0.45f)
            )
            // Defect count — left-aligned, grows RIGHT — always #CA065E
            Text(
                text      = defCount.toString(),
                fontSize  = 14.sp,
                color     = CamAccent,
                textAlign = TextAlign.Start,
                modifier  = Modifier.widthIn(min = 20.dp)
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  ShutterButton
//  Total height of Column == CONTROL_HEIGHT (102.dp) to match thumbnail.
//  Outer ring 60dp / inner filled circle 48dp.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShutterButton(
    outerBorderColor: Color,
    innerFillColor:   Color,
    label:            String,
    labelColor:       Color,
    onClick:          () -> Unit
) {
    Box(
        modifier         = Modifier.height(CONTROL_HEIGHT),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Outer ring (60dp)
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .border(2.5.dp, outerBorderColor, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null,
                        onClick           = onClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Inner filled circle (48dp)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(innerFillColor, CircleShape)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text       = label,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Normal,
                color      = labelColor
            )
        }
    }
}