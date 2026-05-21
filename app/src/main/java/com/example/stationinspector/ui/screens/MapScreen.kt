package com.example.stationinspector.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stationinspector.ui.components.MapWidget
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import com.example.stationinspector.data.local.entity.ShortcutEntity
import com.google.gson.Gson
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.border

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MapScreen(
    viewModel: StationListViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues()
) {
    val routeItems by viewModel.routeItems.collectAsState()
    val routeInfo by viewModel.routeInfo.collectAsState()
    val isOptimizing by viewModel.isOptimizing.collectAsState()

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val isSheetExpanded = sheetState.targetValue == SheetValue.Expanded

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    var isSearchFocused by remember { mutableStateOf(false) }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val shortcuts by viewModel.shortcuts.collectAsState()
    
    var activeShortcutSearchId by remember { mutableStateOf<String?>(null) }
    var editingShortcut by remember { mutableStateOf<ShortcutEntity?>(null) }
    var shortcutToConfirm by remember { mutableStateOf<Pair<String, PoiItem>?>(null) }
    // Set to true immediately before every programmatic focusRequester.requestFocus() call
    // so that onFocusChanged can tell shortcut-initiated focus apart from a direct user tap.
    var pendingShortcutActivation by remember { mutableStateOf(false) }

    val isRoundTrip by viewModel.isRoundTripEnabled.collectAsState()

    if (shortcutToConfirm != null) {
        var confirmName by remember(shortcutToConfirm) { mutableStateOf((shortcutToConfirm!!.second.city ?: shortcutToConfirm!!.second.name).take(10)) }

        AlertDialog(
            onDismissRequest = { shortcutToConfirm = null },
            title = { Text("Save to Shortcut", color = Color(0xFFF5EDFF)) },
            text = { 
                Column {
                    Text("Do you want to bind ${shortcutToConfirm!!.second.name} to this shortcut slot?", color = Color(0xFFF5EDFF).copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = confirmName,
                        onValueChange = { if (it.length <= 10) confirmName = it },
                        label = { Text("Shortcut Name", color = Color(0xFFF5EDFF)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFF5EDFF),
                            unfocusedTextColor = Color(0xFFF5EDFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            containerColor = Color(0xFF13111B),
            confirmButton = {
                TextButton(onClick = {
                    val poi = shortcutToConfirm!!.second
                    val shortcutId = shortcutToConfirm!!.first
                    if (shortcutId == "NEW") {
                        viewModel.createNewShortcut(poi, confirmName)
                    } else {
                        viewModel.updateShortcut(shortcutId, poi, confirmName)
                    }
                    shortcutToConfirm = null
                    activeShortcutSearchId = null
                    focusManager.clearFocus()
                    viewModel.onSearchQueryChanged("")
                }) { Text("Confirm", color = Color(0xFFCA065E), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { shortcutToConfirm = null }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    BackHandler(enabled = isSearchFocused || searchQuery.isNotEmpty()) {
        if (isSearchFocused) {
            focusManager.clearFocus()
        } else {
            viewModel.onSearchQueryChanged("")
        }
        // Always exit shortcut-creation mode when the user presses back.
        activeShortcutSearchId = null
    }

    val cornerRadius by animateDpAsState(
        targetValue = if (isSheetExpanded) 0.dp else 24.dp,
        label = "SheetCornerRadius"
    )

    val editingPoi by viewModel.editingPoi.collectAsState()
    var currentMapCenter by remember { mutableStateOf<org.osmdroid.util.GeoPoint?>(null) }
    var getMapCenter by remember { mutableStateOf<(() -> org.osmdroid.util.GeoPoint)?>(null) }
    var stationEditToConfirm by remember { mutableStateOf<Pair<RouteListItem, org.osmdroid.util.GeoPoint>?>(null) }

    if (stationEditToConfirm != null) {
        AlertDialog(
            onDismissRequest = { stationEditToConfirm = null },
            title = { Text("Confirm Move", color = Color(0xFFF5EDFF)) },
            text = { Text("Are you sure you want to permanently update the coordinates for this station?", color = Color(0xFFF5EDFF).copy(alpha = 0.8f)) },
            containerColor = Color(0xFF13111B),
            confirmButton = {
                TextButton(onClick = {
                    val poi = stationEditToConfirm!!.first
                    val center = stationEditToConfirm!!.second
                    viewModel.saveEditedPoi(poi, center.latitude, center.longitude)
                    stationEditToConfirm = null
                }) { Text("Confirm", color = Color(0xFFCA065E), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { stationEditToConfirm = null }) { Text("Cancel", color = Color.Gray) }
            }
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
                    .background(Color(0xFF13111B))
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
                ModalBottomSheet(onDismissRequest = { editingShortcut = null }, containerColor = Color(0xFF13111B)) {
                    var text by remember { mutableStateOf(editingShortcut!!.customName ?: editingShortcut!!.label) }
                    val currentPoi = editingShortcut!!.poiItemJson?.let { Gson().fromJson(it, PoiItem::class.java) }
                    
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
                                Icon(Icons.Default.Edit, contentDescription = "Edit Address", tint = Color(0xFFF5EDFF).copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
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
                                    viewModel.deleteShortcut(editingShortcut!!.id)
                                    editingShortcut = null
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFCA065E))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = text,
                            onValueChange = { if (it.length <= 10) text = it },
                            label = { Text("Shortcut Name", color = Color(0xFFF5EDFF)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFFF5EDFF),
                                unfocusedTextColor = Color(0xFFF5EDFF)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    val shortcut = editingShortcut!!
                                    viewModel.updateShortcut(shortcut.id, currentPoi, text)
                                    editingShortcut = null
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = "Save Name", tint = Color(0xFFF5EDFF))
                                }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (editingShortcut!!.id == "1") {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text("Round Trip (Start & End)", color = Color.White, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = isRoundTrip,
                                    onCheckedChange = { viewModel.setRoundTripEnabled(it) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFCA065E), checkedTrackColor = Color(0xFFCA065E).copy(alpha = 0.5f))
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }

            val configuration = LocalConfiguration.current
            val screenHeight = configuration.screenHeightDp.dp
            val sheetMaxHeight = screenHeight - 72.dp - WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            // Sheet Surface
            Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sheetMaxHeight)
                        .background(Color(0xFF13111B).copy(alpha = 0.9f))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                    // Drag Handle
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .width(56.dp)
                            .height(4.dp)
                            .background(Color(0xFFF5EDFF), RoundedCornerShape(2.dp))
                            .align(Alignment.CenterHorizontally)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Info Bar removed and moved to Top Overlay
                    
                    // Search Box
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(top = 12.dp)
                            .height(40.dp)
                            .background(Color(0xFFFBF7FF), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF261937), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .onFocusChanged { focusState ->
                                    isSearchFocused = focusState.isFocused
                                    if (focusState.isFocused) {
                                        if (pendingShortcutActivation) {
                                            // Focus was requested by a shortcut button — preserve
                                            // activeShortcutSearchId and consume the flag.
                                            pendingShortcutActivation = false
                                        } else {
                                            // User tapped the search bar directly — this is a normal
                                            // search; any stale shortcut context must be cleared.
                                            activeShortcutSearchId = null
                                        }
                                        coroutineScope.launch { scaffoldState.bottomSheetState.expand() }
                                    }
                                },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF261937), fontSize = 14.sp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text("Where to?", color = Color(0xFF261937).copy(alpha = 0.5f), fontSize = 14.sp)
                                }
                                innerTextField()
                            }
                        )
                        if (isSearchFocused) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = Color(0xFF261937).copy(alpha = 0.5f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        if (searchQuery.isEmpty()) {
                                            focusManager.clearFocus()
                                        } else {
                                            viewModel.onSearchQueryChanged("")
                                        }
                                        // Tapping the close icon always exits shortcut-creation mode.
                                        activeShortcutSearchId = null
                                    }
                            )
                        }
                    }
                    
                    if (isSearchFocused || searchQuery.isNotEmpty()) {
                        // Search Results List
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            when (val state = searchState) {
                                is SearchUiState.Idle -> {
                                    item { Text("Type to search...", color = Color(0xFFF5EDFF).copy(alpha = 0.5f), modifier = Modifier.padding(16.dp)) }
                                }
                                is SearchUiState.Loading -> {
                                    item { CircularProgressIndicator(color = Color(0xFFFBF7FF), modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally)) }
                                }
                                is SearchUiState.Error -> {
                                    item { Text("Error: ${state.message}", color = Color.Red, modifier = Modifier.padding(16.dp)) }
                                }
                                is SearchUiState.Success -> {
                                    if (state.results.isEmpty()) {
                                        item { Text("No results found.", color = Color(0xFFF5EDFF).copy(alpha = 0.5f), modifier = Modifier.padding(16.dp)) }
                                    } else {
                                        items(state.results) { poi ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable(enabled = activeShortcutSearchId == null) { 
                                                        focusManager.clearFocus()
                                                        viewModel.addPoiToRoute(poi)
                                                    }
                                                    .padding(vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.AddLocation, contentDescription = "Add to list", tint = Color(0xFFF5EDFF), modifier = Modifier.size(24.dp))
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(poi.name, color = Color(0xFFF5EDFF), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    
                                                    val cityAddr = listOfNotNull(poi.city, poi.address).joinToString(", ").takeIf { it.isNotBlank() }
                                                    if (cityAddr != null) {
                                                        Text(
                                                            text = cityAddr,
                                                            color = Color(0xFFF5EDFF).copy(alpha = 0.7f),
                                                            fontSize = 14.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    
                                                    if (!poi.region.isNullOrBlank()) {
                                                        Text(
                                                            text = poi.region!!,
                                                            color = Color.Gray,
                                                            fontSize = 12.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                
                                                if (activeShortcutSearchId != null) {
                                                    TextButton(onClick = { shortcutToConfirm = activeShortcutSearchId!! to poi }) {
                                                        Text("+ Save", color = Color(0xFFCA065E), fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            HorizontalDivider(color = Color(0xFFFBF7FF).copy(alpha = 0.1f))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Quick Actions
                        val sortedShortcuts = shortcuts.sortedWith(compareBy({ it.id != "1" && it.id != "2" }, { it.id }))
                        LazyRow(
                            contentPadding = PaddingValues(start = 14.dp, end = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            items(sortedShortcuts) { shortcut ->
                                val label = shortcut.customName ?: shortcut.label
                                val icon = when {
                                    shortcut.poiItemJson == null -> Icons.Default.Add
                                    shortcut.label == "Home" -> Icons.Default.Home
                                    shortcut.label == "Work" -> Icons.Default.Work
                                    else -> Icons.Default.Place
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .size(width = 80.dp, height = 60.dp)
                                        .background(Color.Transparent, RoundedCornerShape(12.dp))
                                        .border(1.dp, Color(0xFFFBF7FF), RoundedCornerShape(12.dp))
                                        .clip(RoundedCornerShape(12.dp))
                                        .combinedClickable(
                                            onClick = {
                                                if (shortcut.poiItemJson.isNullOrEmpty()) {
                                                    activeShortcutSearchId = shortcut.id
                                                    pendingShortcutActivation = true
                                                    focusRequester.requestFocus()
                                                } else {
                                                    val poi = Gson().fromJson(shortcut.poiItemJson, PoiItem::class.java)
                                                    viewModel.addShortcutToRoute(shortcut.id, poi)
                                                }
                                            },
                                            onLongClick = {
                                                editingShortcut = shortcut
                                            }
                                        )
                                        .padding(4.dp)
                                ) {
                                    Icon(imageVector = icon, contentDescription = label, tint = Color(0xFFFBF7FF), modifier = Modifier.size(30.dp))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = label, 
                                        color = Color(0xFFFBF7FF), 
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
                                        .border(1.dp, Color(0xFFFBF7FF), RoundedCornerShape(12.dp))
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            activeShortcutSearchId = "NEW"
                                            pendingShortcutActivation = true
                                            focusRequester.requestFocus()
                                        }
                                        .padding(4.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add New", tint = Color(0xFFFBF7FF), modifier = Modifier.size(30.dp))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Add new", 
                                        color = Color(0xFFFBF7FF), 
                                        fontSize = 12.sp, 
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        
                        // List Header
                        Text(
                            text = "List of points",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF5EDFF),
                            modifier = Modifier.padding(top = 16.dp, start = 14.dp, bottom = 6.dp)
                        )
                        
                        // Reorderable Station List
                        ReorderableMapList(initialItems = routeItems, isSheetExpanded = isSheetExpanded)
                    }
                }
            }
            } else {
                Spacer(modifier = Modifier.height(1.dp))
            }
        },
        content = {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val bottomSheetHeightDp = if (isSheetExpanded) 400.dp else 290.dp
            val mapBottomPaddingPx = with(density) { (if (editingPoi != null) 32.dp else bottomSheetHeightDp + 32.dp).toPx().toInt() }
            val mapSafeMarginPx = with(density) { 64.dp.toPx().toInt() }

            Box(modifier = Modifier.fillMaxSize()) {
                // Map Area (Main Content)
                MapWidget(
                    routeItems = routeItems,
                    routeInfo = routeInfo,
                    isMapExpanded = true,
                    editingPoi = editingPoi,
                    onMapCenterChange = { center -> currentMapCenter = center },
                    onGetCenterCallback = { getMapCenter = it },
                    bottomPaddingPx = mapBottomPaddingPx,
                    safeMarginPx = mapSafeMarginPx,
                    modifier = Modifier.fillMaxSize()
                )

                if (editingPoi == null && routeItems.size >= 3) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = 16.dp)
                            .background(Color(0xFF13111B).copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .clickable(enabled = !isOptimizing) { viewModel.optimizeRoute() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isOptimizing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFFFBF7FF),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoGraph,
                                    contentDescription = "Optimize",
                                    tint = Color(0xFFFBF7FF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Optimize", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFFBF7FF))
                        }
                    }
                }

                if (editingPoi != null) {
                    val displayName = editingPoi!!.name
                    
                    Box(modifier = Modifier.align(Alignment.Center), contentAlignment = Alignment.BottomCenter) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 0.dp)) {
                            // Smart Pin Capsule
                            Row(
                                modifier = Modifier
                                    .wrapContentWidth()
                                    .shadow(8.dp, RoundedCornerShape(24.dp))
                                    .background(Color(0xFF13111B).copy(alpha = 0.9f), RoundedCornerShape(24.dp))
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Cancel Button
                                IconButton(
                                    onClick = { viewModel.cancelEditingPoi() },
                                    modifier = Modifier.size(36.dp).background(Color(0xFFCA065E).copy(alpha = 0.15f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFFCA065E), modifier = Modifier.size(20.dp))
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
                                
                                // Confirm Button
                                IconButton(
                                    onClick = {
                                        val center = getMapCenter?.invoke() ?: currentMapCenter
                                        center?.let { c ->
                                            if (editingPoi!!.isStation) {
                                                stationEditToConfirm = editingPoi!! to c
                                            } else {
                                                viewModel.saveEditedPoi(editingPoi!!, c.latitude, c.longitude)
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(36.dp).background(Color(0xFF00C853).copy(alpha = 0.15f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Confirm", tint = Color(0xFF00C853), modifier = Modifier.size(20.dp))
                                }
                            }
                            
                            // Central Pin Base
                            Icon(
                                imageVector = Icons.Default.LocationOn, 
                                contentDescription = "Pin", 
                                tint = Color(0xFFCA065E), 
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun MapInfoBlock(icon: ImageVector, value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = Color(0xFFF5EDFF), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                color = Color(0xFFF5EDFF),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = label,
                color = Color(0xFFF5EDFF).copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderableMapList(
    initialItems: List<RouteListItem>,
    isSheetExpanded: Boolean,
    viewModel: StationListViewModel = hiltViewModel()
) {
    // Local copy that drives the list.  NOT keyed on initialItems so that a
    // Room emission arriving mid-drag never resets the list and causes an
    // IndexOutOfBoundsException or "stuck" item.
    var items by remember { mutableStateOf(initialItems) }

    val state = rememberReorderableLazyListState(
        onMove = { from, to ->
            // Bounds-guard: indices can be stale if a DB emission fires during
            // the drag gesture and the snapshot of `items` used by the gesture
            // no longer matches the live list.
            if (from.index in items.indices && to.index in items.indices) {
                items = items.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                }
            }
        },
        onDragEnd = { _, _ ->
            // Persist the reordered positions so the next Room emission
            // confirms the order rather than reverting it.
            viewModel.reorderItems(items)
        }
    )

    // Sync from the upstream StateFlow only when no drag is active.
    // Suppressing the sync mid-drag is what prevents the crash and the
    // "stuck" behaviour on newly inserted items.
    LaunchedEffect(initialItems) {
        if (state.draggingItemKey == null) {
            items = initialItems
        }
    }

    LazyColumn(
        state = state.listState,
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 90.dp)
            .reorderable(state)
    ) {
        items(items, key = { it.stableId }) { item ->
            ReorderableItem(state, key = item.stableId) { isDragging ->
                val elevation = animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                val backgroundColor by animateColorAsState(if (isDragging) Color(0xFF13111B) else Color.Transparent, label = "bgColor")
                
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { dismissValue ->
                        if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                            if (item.isStation) {
                                viewModel.toggleHidePoi(item.id)
                                false // snap back
                            } else {
                                viewModel.deletePoi(item.id)
                                true // dismiss
                            }
                        } else false
                    }
                )

                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                            val iconColor = Color(0xFFCA065E)
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
                            // Circle indicator
                            Box(modifier = Modifier.size(12.dp).background(Color(0xFFF5EDFF), CircleShape))
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            // Sequence Number
                            Text(
                                text = (items.indexOf(item) + 1).toString(),
                                color = Color(0xFFF5EDFF).copy(alpha = if (item.isHidden) 0.5f else 1f),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            // Icon
                            if (item.isStation) {
                                val stationIcon = if (item.isHidden) Icons.Default.VisibilityOff else Icons.Default.Train
                                Icon(stationIcon, contentDescription = null, tint = Color(0xFFF5EDFF).copy(alpha = if (item.isHidden) 0.5f else 1f), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            
                            // Name
                            Text(
                                text = item.name,
                                color = Color(0xFFF5EDFF).copy(alpha = if (item.isHidden) 0.5f else 1f),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = if (item.isHidden) TextDecoration.LineThrough else TextDecoration.None
                            )
                            
                            Spacer(modifier = Modifier.weight(1f))
                            
                            // Edit Icon
                            Icon(
                                Icons.Default.EditLocationAlt, 
                                contentDescription = null, 
                                tint = Color(0xFFF5EDFF), 
                                modifier = Modifier.size(24.dp).clickable { viewModel.startEditingPoi(item) }
                            )
                        }
                        HorizontalDivider(color = Color(0xFFFBF7FF).copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 14.dp))
                    }
                }
            }
        }
    }
}
