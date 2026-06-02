package com.example.stationinspector.ui.export

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stationinspector.domain.model.Photo
import com.example.stationinspector.domain.model.PhotoType
import com.example.stationinspector.ui.theme.StationInspectorTheme
import com.example.stationinspector.ui.theme.clickableNoRipple
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import com.example.stationinspector.ui.theme.ContentLight
import com.example.stationinspector.ui.theme.CardContent
import com.example.stationinspector.ui.theme.AccentPink
import com.example.stationinspector.ui.theme.AccentRed
import com.example.stationinspector.ui.theme.AccentGreenAlt

// ─────────────────────────────────────────────────────────────────────────────
//  Design tokens
// ─────────────────────────────────────────────────────────────────────────────

private val ExLight       = ContentLight   // Text on dark bg, icons on dark bg
private val ExDark        = CardContent    // Card content, button bg
private val ExAccent      = AccentPink     // Defect accent, progress indicator
private val ExCardBg      = ContentLight   // Info card background

private data class ExportStats(
    val defectPhotos: List<Photo>,
    val normalPhotos: List<Photo>,
    val defectStationCount: Int,
    val normalStationCount: Int
)

// ─────────────────────────────────────────────────────────────────────────────
//  ExportScreen — redesigned per spec (transparent bg, gradient from parent)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ExportScreen(
    dateStr:         String = "",
    viewModel:       ExportViewModel = hiltViewModel(),
    onBackClick:     () -> Unit,
    onExportSuccess: () -> Unit,
    contentPadding:  PaddingValues = PaddingValues()
) {
    val context           = LocalContext.current
    val state             by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Date setup ────────────────────────────────────────────────────────────
    LaunchedEffect(dateStr) {
        if (dateStr.isNotEmpty()) viewModel.setSelectedDate(dateStr)
    }

    // ── Export result handler (preserved exactly from original) ───────────────
    LaunchedEffect(state.exportState) {
        when (state.exportState) {
            ExportState.SUCCESS -> {
                launch {
                    snackbarHostState.showSnackbar(
                        message  = "Archive created successfully",
                        duration = SnackbarDuration.Short
                    )
                }
                state.zipUri?.let { uriString ->
                    try {
                        val uri         = Uri.parse(uriString)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type      = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData  = android.content.ClipData.newRawUri("", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Send report"))
                    } catch (e: Exception) {
                        Log.w("ExportScreen", "Failed to launch share intent", e)
                    }
                }
                onExportSuccess()
                viewModel.resetState()
            }
            ExportState.ERROR -> {
                snackbarHostState.showSnackbar(
                    message  = "An error occurred. Check your connection or try again.",
                    duration = SnackbarDuration.Short
                )
            }
            else -> {}
        }
    }

    ExportScreenContent(
        dateStr           = dateStr,
        exportState       = state.exportState,
        photos            = state.photos,
        snackbarHostState = snackbarHostState,
        onBackClick       = onBackClick,
        onExportClick     = { viewModel.startExport() },
        contentPadding    = contentPadding
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  ExportScreenContent — Stateless screen layout
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ExportScreenContent(
    dateStr:           String,
    exportState:       ExportState,
    photos:            List<Photo>,
    snackbarHostState: SnackbarHostState,
    onBackClick:       () -> Unit,
    onExportClick:     () -> Unit,
    modifier:          Modifier = Modifier,
    contentPadding:    PaddingValues = PaddingValues()
) {
    // ── Statistics (computed from photo list using remember) ──────────────────
    val stats = remember(photos) {
        val defect = photos.filter { it.type == PhotoType.INTERNAL_DEFECT }
        val normal = photos.filter { it.type != PhotoType.INTERNAL_DEFECT }
        val defectCount = defect.map { it.stationId }.distinct().count()
        val normalCount = normal.map { it.stationId }.distinct().count()
        ExportStats(
            defectPhotos       = defect,
            normalPhotos       = normal,
            defectStationCount = defectCount,
            normalStationCount = normalCount
        )
    }

    // ── Formatted date header (dd.MM) ─────────────────────────────────────────
    val formattedDate: String = remember(dateStr) {
        try {
            LocalDate.parse(dateStr).format(DateTimeFormatter.ofPattern("dd.MM"))
        } catch (e: Exception) { "" }
    }

    // ── Root: transparent — gradient comes from MainAppScreen ─────────────────
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {

            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back arrow
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint               = ExLight,
                        modifier           = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Title + Date sub-header
                Column {
                    Text(
                        text       = "Export",
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = ExLight
                    )
                    if (formattedDate.isNotEmpty()) {
                        Text(
                            text       = formattedDate,
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color      = ExLight
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Main Info Card ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ExCardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    // Card title — centered
                    Text(
                        text       = "Ready to export",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = ExDark,
                        textAlign  = TextAlign.Center,
                        modifier   = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Ordinary photos block
                    Column {
                        Text(
                            text       = "Ordinary photos:",
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = ExDark
                        )
                        Text(
                            text       = "${stats.normalPhotos.size} photos \u2022 ${stats.normalStationCount} rail station",
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = ExDark
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Defects block
                    Column {
                        Text(
                            text       = "Defects:",
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = ExAccent
                        )
                        Text(
                            text       = "${stats.defectPhotos.size} photos \u2022 ${stats.defectStationCount} rail station",
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = ExAccent
                        )
                    }

                    // Export button — centred, no weight() needed with wrapContentHeight card
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier         = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(208.dp)
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (exportState == ExportState.RUNNING)
                                        ExDark.copy(alpha = 0.5f) else ExDark
                                )
                                .then(
                                    if (exportState != ExportState.RUNNING)
                                        Modifier.clickableNoRipple(onClick = onExportClick)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector        = Icons.Filled.FileDownload,
                                    contentDescription = "Generate ZIP",
                                    tint               = ExLight,
                                    modifier           = Modifier.size(24.dp)
                                )
                                Text(
                                    text       = "Generate ZIP-archive",
                                    fontSize   = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = ExLight
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(52.dp))

            // ── Footer text ───────────────────────────────────────────────────
            Text(
                text       = "Generating the archive may take a few seconds. Please do not close the application.",
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
                color      = ExLight,
                textAlign  = TextAlign.Center,
                modifier   = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
        }

        // ── Snackbar (success / error) ────────────────────────────────────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.TopCenter)
        ) { data ->
            val snackColor = if (exportState == ExportState.ERROR)
                AccentRed else AccentGreenAlt
            Snackbar(
                snackbarData   = data,
                containerColor = snackColor,
                contentColor   = Color.White
            )
        }

        // ── Loading overlay ───────────────────────────────────────────────────
        if (exportState == ExportState.RUNNING) {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ExAccent)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Preview
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun ExportScreenContentPreview() {
    StationInspectorTheme {
        ExportScreenContent(
            dateStr           = "2026-05-21",
            exportState       = ExportState.IDLE,
            photos            = emptyList(),
            snackbarHostState = remember { SnackbarHostState() },
            onBackClick       = {},
            onExportClick     = {}
        )
    }
}
