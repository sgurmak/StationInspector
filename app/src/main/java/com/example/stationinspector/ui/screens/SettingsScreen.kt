package com.example.stationinspector.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stationinspector.ui.theme.AccentPink
import com.example.stationinspector.ui.theme.CardContent
import com.example.stationinspector.ui.theme.ContentLight
import com.example.stationinspector.ui.theme.DestructiveBg as DestructiveBgToken

// ─────────────────────────────────────────────────────────────────────────────
//  Settings Screen
//  Background is transparent — gradient comes from MainAppScreen's Scaffold.
// ─────────────────────────────────────────────────────────────────────────────

// Design tokens local to this screen
private val SettingsText      = ContentLight
private val ButtonBg          = ContentLight        // Import CSV button bg
private val ButtonContent     = CardContent         // Import CSV icon + text
private val DestructiveBg     = DestructiveBgToken  // Clear DB button bg (pale pink)
private val DestructiveAccent = AccentPink          // Clear DB icon + text

@Composable
fun SettingsScreen(
    viewModel:      StationListViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current

    // ── CSV import launcher ───────────────────────────────────────────────────
    // Opening the content stream is the UI layer's job; the ViewModel/use case
    // only consumes an already-opened InputStream (no Android Context leaks in).
    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            context.contentResolver.openInputStream(selectedUri)?.let { stream ->
                viewModel.importStationsFromCsv(stream)
            }
        }
    }

    SettingsScreenContent(
        onNavigateBack = onNavigateBack,
        onImportCsvClick = { csvLauncher.launch("*/*") },
        onClearStorageClick = { viewModel.clearAllData() },
        contentPadding = contentPadding
    )
}

@Composable
fun SettingsScreenContent(
    onNavigateBack: () -> Unit,
    onImportCsvClick: () -> Unit,
    onClearStorageClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    // ── Clear-all dialog state ────────────────────────────────────────────────
    var showClearDialog by remember { mutableStateOf(false) }

    // ── Material 3 AlertDialog ─────────────────────────────────────────────
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = {
                Icon(
                    imageVector        = Icons.Filled.Delete,
                    contentDescription = null,
                    tint               = DestructiveAccent,
                    modifier           = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text       = "Delete all data?",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = ButtonContent
                )
            },
            text = {
                Text(
                    text      = "All stations and photos will be deleted from the database. This action cannot be undone.",
                    fontSize  = 14.sp,
                    color     = ButtonContent
                )
            },
            containerColor = ButtonBg,
            confirmButton = {
                Button(
                    onClick = {
                        onClearStorageClick()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DestructiveAccent,
                        contentColor   = ButtonBg
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearDialog = false },
                    border  = androidx.compose.foundation.BorderStroke(
                        1.dp, CardContent
                    )
                ) {
                    Text(
                        text  = "Cancel",
                        color = CardContent
                    )
                }
            }
        )
    }

    // ── Screen content ────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {

        // ── Top bar: back arrow + title ───────────────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint               = SettingsText,
                    modifier           = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text       = "Settings",
                fontSize   = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color      = SettingsText
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Buttons column ────────────────────────────────────────────────────
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Button 1: Import CSV ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(ButtonBg, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onImportCsvClick() },
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier          = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector        = Icons.Filled.FileDownload,
                        contentDescription = "Import CSV",
                        tint               = ButtonContent,
                        modifier           = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text       = "Import CSV",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = ButtonContent
                    )
                }
            }

            // ── Button 2: Clear Storage ───────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(DestructiveBg, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showClearDialog = true },
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier          = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Delete,
                        contentDescription = "Clear storage",
                        tint               = DestructiveAccent,
                        modifier           = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text       = "Clear Storage",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = DestructiveAccent
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF13111B)
@Composable
fun SettingsScreenContentPreview() {
    SettingsScreenContent(
        onNavigateBack = {},
        onImportCsvClick = {},
        onClearStorageClick = {}
    )
}

