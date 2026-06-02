package com.example.stationinspector.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stationinspector.domain.model.Shortcut
import com.example.stationinspector.ui.components.MapWidget
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import kotlinx.coroutines.launch
import com.example.stationinspector.ui.theme.ContentDark
import com.example.stationinspector.ui.theme.ContentLight
import com.example.stationinspector.ui.theme.ContentLightSecondary
import com.example.stationinspector.ui.theme.CardContent
import com.example.stationinspector.ui.theme.AccentGreenConfirm
import com.example.stationinspector.ui.theme.AccentPink

// ── Local Screen Design Tokens ───────────────────────────────────────────────
private val MapDark        = ContentDark
private val MapLight       = ContentLight
private val MapAccent      = AccentPink
private val MapTextLight   = ContentLightSecondary
private val MapCardContent = CardContent

@Composable
fun MapScreen(
    routeViewModel: RouteViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel(),
    shortcutsViewModel: ShortcutsViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues()
) {
    val routeItems by routeViewModel.routeItems.collectAsState()
    val routeInfo by routeViewModel.routeInfo.collectAsState()
    val isOptimizing by routeViewModel.isOptimizing.collectAsState()
    val searchQuery by searchViewModel.searchQuery.collectAsState()
    val searchState by searchViewModel.searchState.collectAsState()
    val shortcuts by shortcutsViewModel.shortcuts.collectAsState()
    val isRoundTrip by routeViewModel.isRoundTripEnabled.collectAsState()
    val editingPoi by routeViewModel.editingPoi.collectAsState()

    MapScreenContent(
        routeItems = routeItems,
        routeInfo = routeInfo,
        isOptimizing = isOptimizing,
        searchQuery = searchQuery,
        searchState = searchState,
        shortcuts = shortcuts,
        isRoundTrip = isRoundTrip,
        editingPoi = editingPoi,
        onSearchQueryChanged = searchViewModel::onSearchQueryChanged,
        // Adding to the route clears the search box (previously done inside the VM).
        onAddPoiToRoute = { poi ->
            routeViewModel.addPoiToRoute(poi)
            searchViewModel.clearSearch()
        },
        onAddShortcutToRoute = { shortcutId, poi ->
            routeViewModel.addShortcutToRoute(shortcutId, poi)
            searchViewModel.clearSearch()
        },
        onReorderItems = routeViewModel::reorderItems,
        onToggleHidePoi = routeViewModel::toggleHidePoi,
        onDeletePoi = routeViewModel::deletePoi,
        onStartEditingPoi = routeViewModel::startEditingPoi,
        onCancelEditingPoi = routeViewModel::cancelEditingPoi,
        onSaveEditedPoi = routeViewModel::saveEditedPoi,
        onOptimizeRoute = routeViewModel::optimizeRoute,
        onSetRoundTripEnabled = routeViewModel::setRoundTripEnabled,
        onUpdateShortcut = shortcutsViewModel::updateShortcut,
        onCreateNewShortcut = shortcutsViewModel::createNewShortcut,
        onDeleteShortcut = shortcutsViewModel::deleteShortcut,
        contentPadding = contentPadding
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MapScreenContent(
    routeItems: List<RouteListItem>,
    routeInfo: DailyRouteInfo,
    isOptimizing: Boolean,
    searchQuery: String,
    searchState: SearchUiState,
    shortcuts: List<ShortcutUiModel>,
    isRoundTrip: Boolean,
    editingPoi: RouteListItem?,
    onSearchQueryChanged: (String) -> Unit,
    onAddPoiToRoute: (PoiItem) -> Unit,
    onAddShortcutToRoute: (String, PoiItem) -> Unit,
    onReorderItems: (List<RouteListItem>) -> Unit,
    onToggleHidePoi: (String) -> Unit,
    onDeletePoi: (String) -> Unit,
    onStartEditingPoi: (RouteListItem) -> Unit,
    onCancelEditingPoi: () -> Unit,
    onSaveEditedPoi: (RouteListItem, Double, Double) -> Unit,
    onOptimizeRoute: () -> Unit,
    onSetRoundTripEnabled: (Boolean) -> Unit,
    onUpdateShortcut: (String, PoiItem?, String?) -> Unit,
    onCreateNewShortcut: (PoiItem?, String?) -> Unit,
    onDeleteShortcut: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val isSheetExpanded = sheetState.targetValue == SheetValue.Expanded

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    var isSearchFocused by remember { mutableStateOf(false) }

    var activeShortcutSearchId by remember { mutableStateOf<String?>(null) }
    var editingShortcut by remember { mutableStateOf<ShortcutUiModel?>(null) }
    var shortcutToConfirm by remember { mutableStateOf<Pair<String, PoiItem>?>(null) }
    var pendingShortcutActivation by remember { mutableStateOf(false) }

    if (shortcutToConfirm != null) {
        val (shortcutId, poi) = shortcutToConfirm!!
        ShortcutConfirmationDialog(
            shortcutId = shortcutId,
            poi = poi,
            onConfirm = { confirmName ->
                if (shortcutId == Shortcut.ID_NEW) {
                    onCreateNewShortcut(poi, confirmName)
                } else {
                    onUpdateShortcut(shortcutId, poi, confirmName)
                }
                shortcutToConfirm = null
                activeShortcutSearchId = null
                focusManager.clearFocus()
                onSearchQueryChanged("")
            },
            onDismiss = { shortcutToConfirm = null }
        )
    }

    BackHandler(enabled = isSearchFocused || searchQuery.isNotEmpty()) {
        if (isSearchFocused) {
            focusManager.clearFocus()
        } else {
            onSearchQueryChanged("")
        }
        activeShortcutSearchId = null
    }

    val cornerRadius by animateDpAsState(
        targetValue = if (isSheetExpanded) 0.dp else 24.dp,
        label = "SheetCornerRadius"
    )

    var currentMapCenter by remember { mutableStateOf<org.osmdroid.util.GeoPoint?>(null) }
    var getMapCenter by remember { mutableStateOf<(() -> org.osmdroid.util.GeoPoint)?>(null) }
    var stationEditToConfirm by remember { mutableStateOf<Pair<RouteListItem, org.osmdroid.util.GeoPoint>?>(null) }

    if (stationEditToConfirm != null) {
        val (poi, center) = stationEditToConfirm!!
        StationEditConfirmationDialog(
            poi = poi,
            newLocation = center,
            onConfirm = {
                onSaveEditedPoi(poi, center.latitude, center.longitude)
                stationEditToConfirm = null
            },
            onDismiss = { stationEditToConfirm = null }
        )
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetShape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
        sheetContainerColor = Color.Transparent,
        containerColor = Color.Transparent,
        sheetDragHandle = null,
        sheetPeekHeight = if (editingPoi != null) 0.dp else 290.dp,
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = {
            if (editingPoi == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MapDark)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .height(72.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val distanceText = if (routeInfo.totalDistanceKm > 0.0) String.format(java.util.Locale.US, "%.1f km", routeInfo.totalDistanceKm) else "– km"
                    val timeText = if (routeInfo.totalTimeMins > 0) routeInfo.formattedDuration else "–"
                    val stopsText = if (routeInfo.waypointCount > 0) "${routeInfo.waypointCount} stops" else "–"

                    MapInfoBlock(Icons.Default.Map, distanceText, "Distance")
                    MapInfoBlock(Icons.Default.Schedule, timeText, "Time")
                    MapInfoBlock(Icons.Default.Place, stopsText, "Waypoints")
                }
            }
        },
        sheetContent = {
            if (editingPoi == null) {
                if (editingShortcut != null) {
                    ModalBottomSheet(onDismissRequest = { editingShortcut = null }, containerColor = MapDark) {
                        var text by remember { mutableStateOf(editingShortcut!!.customName ?: editingShortcut!!.label) }
                        val currentPoi = editingShortcut!!.poiItem
                        
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            activeShortcutSearchId = editingShortcut!!.id
                                            pendingShortcutActivation = true
                                            editingShortcut = null
                                            focusRequester.requestFocus()
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Address", tint = MapTextLight.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (currentPoi != null) {
                                            val cityAddr = listOfNotNull(currentPoi.city, currentPoi.address).joinToString(", ")
                                            Text(
                                                text = if (cityAddr.isNotBlank()) cityAddr else currentPoi.name,
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else {
                                            Text(
                                                text = "No Address Assigned",
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                IconButton(
                                    onClick = {
                                        onDeleteShortcut(editingShortcut!!.id)
                                        editingShortcut = null
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MapAccent)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = text,
                                onValueChange = { if (it.length <= 10) text = it },
                                label = { Text("Shortcut Name", color = MapTextLight) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MapTextLight,
                                    unfocusedTextColor = MapTextLight
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        val shortcut = editingShortcut!!
                                        onUpdateShortcut(shortcut.id, currentPoi, text)
                                        editingShortcut = null
                                    }) {
                                        Icon(Icons.Default.Check, contentDescription = "Save Name", tint = MapTextLight)
                                    }
                                }
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            if (editingShortcut!!.id == Shortcut.ID_HOME) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                    Text("Round Trip (Start & End)", color = Color.White, modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = isRoundTrip,
                                        onCheckedChange = { onSetRoundTripEnabled(it) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = MapAccent, checkedTrackColor = MapAccent.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    }
                }

                val configuration = LocalConfiguration.current
                val screenHeight = configuration.screenHeightDp.dp
                val sheetMaxHeight = screenHeight - 72.dp - WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sheetMaxHeight)
                        .background(MapDark.copy(alpha = 0.9f))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .width(56.dp)
                                .height(4.dp)
                                .background(MapTextLight, RoundedCornerShape(2.dp))
                                .align(Alignment.CenterHorizontally)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .padding(top = 12.dp)
                                .height(40.dp)
                                .background(MapLight, RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MapCardContent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChanged,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { focusState ->
                                        isSearchFocused = focusState.isFocused
                                        if (focusState.isFocused) {
                                            if (pendingShortcutActivation) {
                                                pendingShortcutActivation = false
                                            } else {
                                                activeShortcutSearchId = null
                                            }
                                            coroutineScope.launch { scaffoldState.bottomSheetState.expand() }
                                        }
                                    },
                                textStyle = androidx.compose.ui.text.TextStyle(color = MapCardContent, fontSize = 14.sp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text("Where to?", color = MapCardContent.copy(alpha = 0.5f), fontSize = 14.sp)
                                    }
                                    innerTextField()
                                }
                            )
                            if (isSearchFocused) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = MapCardContent.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable {
                                            if (searchQuery.isEmpty()) {
                                                focusManager.clearFocus()
                                            } else {
                                                onSearchQueryChanged("")
                                            }
                                            activeShortcutSearchId = null
                                        }
                                )
                            }
                        }
                        
                        if (isSearchFocused || searchQuery.isNotEmpty()) {
                            SearchResultsList(
                                searchState = searchState,
                                isShortcutMode = activeShortcutSearchId != null,
                                onResultClick = { poi ->
                                    focusManager.clearFocus()
                                    onAddPoiToRoute(poi)
                                },
                                onSaveToShortcut = { poi ->
                                    activeShortcutSearchId?.let { shortcutToConfirm = it to poi }
                                },
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            )
                        } else {
                            ShortcutsRow(
                                shortcuts = shortcuts,
                                onShortcutClick = { shortcut ->
                                    val poi = shortcut.poiItem
                                    if (poi == null) {
                                        activeShortcutSearchId = shortcut.id
                                        pendingShortcutActivation = true
                                        focusRequester.requestFocus()
                                    } else {
                                        onAddShortcutToRoute(shortcut.id, poi)
                                    }
                                },
                                onShortcutLongClick = { shortcut -> editingShortcut = shortcut },
                                onAddNewClick = {
                                    activeShortcutSearchId = Shortcut.ID_NEW
                                    pendingShortcutActivation = true
                                    focusRequester.requestFocus()
                                },
                                modifier = Modifier.padding(top = 16.dp)
                            )
                            
                            Text(
                                text = "List of points",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MapTextLight,
                                modifier = Modifier.padding(top = 16.dp, start = 14.dp, bottom = 6.dp)
                            )
                            
                            ReorderableMapList(
                                items = routeItems,
                                isSheetExpanded = isSheetExpanded,
                                onReorderItems = onReorderItems,
                                onToggleHidePoi = onToggleHidePoi,
                                onDeletePoi = onDeletePoi,
                                onStartEditingPoi = onStartEditingPoi
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(1.dp))
            }
        },
        content = {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val mapTopPaddingPx = with(density) { (if (editingPoi != null) 0.dp else 72.dp + statusBarHeightDp).toPx().toInt() }
            val bottomSheetHeightDp = if (isSheetExpanded) 400.dp else 290.dp
            val mapBottomPaddingPx = with(density) { (if (editingPoi != null) 32.dp else bottomSheetHeightDp + 32.dp).toPx().toInt() }

            Box(modifier = Modifier.fillMaxSize()) {
                MapWidget(
                    routeItems = routeItems,
                    routeInfo = routeInfo,
                    isMapExpanded = true,
                    editingPoi = editingPoi,
                    onMapCenterChange = { center -> currentMapCenter = center },
                    onGetCenterCallback = { getMapCenter = it },
                    topPaddingPx = mapTopPaddingPx,
                    bottomPaddingPx = mapBottomPaddingPx,
                    modifier = Modifier.fillMaxSize()
                )

                if (editingPoi == null && routeItems.size >= 3) {
                    OptimizeRouteButton(
                        isOptimizing = isOptimizing,
                        onClick = onOptimizeRoute,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = 16.dp)
                    )
                }

                if (editingPoi != null) {
                    EditingPoiOverlay(
                        displayName = editingPoi.name,
                        onCancel = onCancelEditingPoi,
                        onConfirm = {
                            val center = getMapCenter?.invoke() ?: currentMapCenter
                            center?.let { c ->
                                if (editingPoi.isStation) {
                                    stationEditToConfirm = editingPoi to c
                                } else {
                                    onSaveEditedPoi(editingPoi, c.latitude, c.longitude)
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    )
}

@Composable
fun MapInfoBlock(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = MapTextLight, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                color = MapTextLight,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = label,
                color = MapTextLight.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ShortcutConfirmationDialog(
    shortcutId: String,
    poi: PoiItem,
    onConfirm: (confirmName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var confirmName by remember(poi) { mutableStateOf((poi.city ?: poi.name).take(10)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save to Shortcut", color = MapTextLight) },
        text = { 
            Column {
                Text("Do you want to bind ${poi.name} to this shortcut slot?", color = MapTextLight.copy(alpha = 0.8f))
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = confirmName,
                    onValueChange = { if (it.length <= 10) confirmName = it },
                    label = { Text("Shortcut Name", color = MapTextLight) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MapTextLight,
                        unfocusedTextColor = MapTextLight
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        containerColor = MapDark,
        confirmButton = {
            TextButton(onClick = { onConfirm(confirmName) }) {
                Text("Confirm", color = MapAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

@Composable
fun StationEditConfirmationDialog(
    poi: RouteListItem,
    newLocation: org.osmdroid.util.GeoPoint,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Move", color = MapTextLight) },
        text = { Text("Are you sure you want to permanently update the coordinates for this station?", color = MapTextLight.copy(alpha = 0.8f)) },
        containerColor = MapDark,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Confirm", color = MapAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

@Composable
fun OptimizeRouteButton(
    isOptimizing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MapDark.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
            .clickable(enabled = !isOptimizing) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isOptimizing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MapLight,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AutoGraph,
                    contentDescription = "Optimize",
                    tint = MapLight,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text("Optimize", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MapLight)
        }
    }
}

@Composable
fun EditingPoiOverlay(
    displayName: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .shadow(8.dp, RoundedCornerShape(24.dp))
                    .background(MapDark.copy(alpha = 0.9f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .size(36.dp)
                        .background(MapAccent.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = MapAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = displayName,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                IconButton(
                    onClick = onConfirm,
                    modifier = Modifier
                        .size(36.dp)
                        .background(AccentGreenConfirm.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirm",
                        tint = AccentGreenConfirm,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Default.LocationOn, 
                contentDescription = "Pin", 
                tint = MapAccent, 
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderableMapList(
    items: List<RouteListItem>,
    isSheetExpanded: Boolean,
    onReorderItems: (List<RouteListItem>) -> Unit,
    onToggleHidePoi: (String) -> Unit,
    onDeletePoi: (String) -> Unit,
    onStartEditingPoi: (RouteListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var localItems by remember { mutableStateOf(items) }

    val state = rememberReorderableLazyListState(
        onMove = { from, to ->
            if (from.index in localItems.indices && to.index in localItems.indices) {
                localItems = localItems.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                }
            }
        },
        onDragEnd = { _, _ ->
            onReorderItems(localItems)
        }
    )

    LaunchedEffect(items) {
        if (state.draggingItemKey == null) {
            localItems = items
        }
    }

    LazyColumn(
        state = state.listState,
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 90.dp)
            .reorderable(state)
    ) {
        items(localItems, key = { it.stableId }) { item ->
            ReorderableItem(state, key = item.stableId) { isDragging ->
                val elevation = animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                val backgroundColor by animateColorAsState(if (isDragging) MapDark else Color.Transparent, label = "bgColor")
                
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { dismissValue ->
                        if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                            if (item.isStation) {
                                onToggleHidePoi(item.id)
                                false
                            } else {
                                onDeletePoi(item.id)
                                true
                            }
                        } else false
                    }
                )

                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                            val iconColor = MapAccent
                            val icon = if (!item.isStation) Icons.Default.Delete else if (item.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            val progress = dismissState.progress.coerceIn(0f, 1f)

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Transparent)
                                    .padding(end = 24.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = icon, 
                                    contentDescription = "Action", 
                                    tint = iconColor.copy(alpha = progress),
                                    modifier = Modifier.graphicsLayer(scaleX = 0.8f + progress * 0.2f, scaleY = 0.8f + progress * 0.2f)
                                )
                            }
                        }
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation.value)
                            .background(backgroundColor)
                            .then(
                                if (isSheetExpanded) Modifier.detectReorderAfterLongPress(state) else Modifier
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .height(64.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(12.dp).background(MapTextLight, CircleShape))
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Text(
                                text = (localItems.indexOf(item) + 1).toString(),
                                color = MapTextLight.copy(alpha = if (item.isHidden) 0.5f else 1f),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            if (item.isStation) {
                                val stationIcon = if (item.isHidden) Icons.Default.VisibilityOff else Icons.Default.Train
                                Icon(stationIcon, contentDescription = null, tint = MapTextLight.copy(alpha = if (item.isHidden) 0.5f else 1f), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            
                            Text(
                                text = item.name,
                                color = MapTextLight.copy(alpha = if (item.isHidden) 0.5f else 1f),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = if (item.isHidden) TextDecoration.LineThrough else TextDecoration.None
                            )
                            
                            Spacer(modifier = Modifier.weight(1f))
                            
                            Icon(
                                Icons.Default.EditLocationAlt, 
                                contentDescription = null, 
                                tint = MapTextLight, 
                                modifier = Modifier.size(24.dp).clickable { onStartEditingPoi(item) }
                            )
                        }
                        HorizontalDivider(color = MapLight.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 14.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SearchResultsList — stateless geocoding-results list for the map sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SearchResultsList(
    searchState: SearchUiState,
    isShortcutMode: Boolean,
    onResultClick: (PoiItem) -> Unit,
    onSaveToShortcut: (PoiItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp)
    ) {
        when (val state = searchState) {
            is SearchUiState.Idle -> {
                item { Text("Type to search...", color = MapTextLight.copy(alpha = 0.5f), modifier = Modifier.padding(16.dp)) }
            }
            is SearchUiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MapLight)
                    }
                }
            }
            is SearchUiState.Error -> {
                item { Text("Error: ${state.message}", color = Color.Red, modifier = Modifier.padding(16.dp)) }
            }
            is SearchUiState.Success -> {
                if (state.results.isEmpty()) {
                    item { Text("No results found.", color = MapTextLight.copy(alpha = 0.5f), modifier = Modifier.padding(16.dp)) }
                } else {
                    items(state.results) { poi ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isShortcutMode) { onResultClick(poi) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AddLocation, contentDescription = "Add to list", tint = MapTextLight, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(poi.name, color = MapTextLight, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

                                val cityAddr = listOfNotNull(poi.city, poi.address).joinToString(", ").takeIf { it.isNotBlank() }
                                if (cityAddr != null) {
                                    Text(
                                        text = cityAddr,
                                        color = MapTextLight.copy(alpha = 0.7f),
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (!poi.region.isNullOrBlank()) {
                                    Text(
                                        text = poi.region,
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (isShortcutMode) {
                                TextButton(onClick = { onSaveToShortcut(poi) }) {
                                    Text("+ Save", color = MapAccent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        HorizontalDivider(color = MapLight.copy(alpha = 0.1f))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ShortcutsRow — stateless horizontal row of shortcut chips + "Add new"
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortcutsRow(
    shortcuts: List<ShortcutUiModel>,
    onShortcutClick: (ShortcutUiModel) -> Unit,
    onShortcutLongClick: (ShortcutUiModel) -> Unit,
    onAddNewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedShortcuts = shortcuts.sortedWith(compareBy({ it.id != Shortcut.ID_HOME && it.id != Shortcut.ID_WORK }, { it.id }))
    LazyRow(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        items(sortedShortcuts) { shortcut ->
            val label = shortcut.customName ?: shortcut.label
            val icon = when {
                shortcut.poiItem == null -> Icons.Default.Add
                shortcut.label == Shortcut.NAME_HOME -> Icons.Default.Home
                shortcut.label == Shortcut.NAME_WORK -> Icons.Default.Work
                else -> Icons.Default.Place
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .size(width = 80.dp, height = 60.dp)
                    .background(Color.Transparent, RoundedCornerShape(12.dp))
                    .border(1.dp, MapLight, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = { onShortcutClick(shortcut) },
                        onLongClick = { onShortcutLongClick(shortcut) }
                    )
                    .padding(4.dp)
            ) {
                Icon(imageVector = icon, contentDescription = label, tint = MapLight, modifier = Modifier.size(30.dp))
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = label,
                    color = MapLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .size(width = 80.dp, height = 60.dp)
                    .background(Color.Transparent, RoundedCornerShape(12.dp))
                    .border(1.dp, MapLight, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onAddNewClick() }
                    .padding(4.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add New", tint = MapLight, modifier = Modifier.size(30.dp))
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Add new",
                    color = MapLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF13111B)
@Composable
fun SearchResultsListPreview() {
    SearchResultsList(
        searchState = SearchUiState.Success(
            listOf(
                PoiItem("1", "Praha hlavní nádraží", "Praha", "Wilsonova 8", "Praha", 50.08, 14.43),
                PoiItem("2", "Brno hlavní nádraží", "Brno", "Nádražní 1", "Jihomoravský", 49.19, 16.61)
            )
        ),
        isShortcutMode = false,
        onResultClick = {},
        onSaveToShortcut = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF13111B)
@Composable
fun ShortcutsRowPreview() {
    ShortcutsRow(
        shortcuts = listOf(
            ShortcutUiModel("1", "Home", null, null, true, false),
            ShortcutUiModel("2", "Work", null, null, true, false)
        ),
        onShortcutClick = {},
        onShortcutLongClick = {},
        onAddNewClick = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF13111B)
@Composable
fun MapScreenContentPreview() {
    val sampleRouteItems = listOf(
        PoiItem("1", "Station Alpha", "City A", "123 Main St", "Region A", 50.0, 14.0),
        PoiItem("2", "Station Beta", "City B", "456 Oak Rd", "Region B", 50.1, 14.1)
    )
    val sampleRouteInfo = DailyRouteInfo(
        totalDistanceKm = 12.5,
        totalTimeMins = 35,
        waypointCount = 2,
        polylinePoints = emptyList()
    )
    val sampleShortcuts = listOf(
        ShortcutUiModel("1", "Home", null, null, true, false),
        ShortcutUiModel("2", "Work", null, null, true, false)
    )

    MapScreenContent(
        routeItems = sampleRouteItems,
        routeInfo = sampleRouteInfo,
        isOptimizing = false,
        searchQuery = "",
        searchState = SearchUiState.Idle,
        shortcuts = sampleShortcuts,
        isRoundTrip = false,
        editingPoi = null,
        onSearchQueryChanged = {},
        onAddPoiToRoute = {},
        onAddShortcutToRoute = { _, _ -> },
        onReorderItems = {},
        onToggleHidePoi = {},
        onDeletePoi = {},
        onStartEditingPoi = {},
        onCancelEditingPoi = {},
        onSaveEditedPoi = { _, _, _ -> },
        onOptimizeRoute = {},
        onSetRoundTripEnabled = {},
        onUpdateShortcut = { _, _, _ -> },
        onCreateNewShortcut = { _, _ -> },
        onDeleteShortcut = {}
    )
}
