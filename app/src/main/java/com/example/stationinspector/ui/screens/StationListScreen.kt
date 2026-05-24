package com.example.stationinspector.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stationinspector.ui.components.MapWidget
import com.example.stationinspector.ui.theme.CardContent
import com.example.stationinspector.ui.theme.ContentLight
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.material3.ExperimentalMaterial3Api

import com.example.stationinspector.ui.theme.ContentLight
import com.example.stationinspector.ui.theme.CardContent
import com.example.stationinspector.ui.theme.AccentPink
import com.example.stationinspector.ui.theme.AccentGreenAlt
import com.example.stationinspector.ui.theme.AppGradientTop

// ─────────────────────────────────────────────────────────────────────────────
//  Design tokens
// ─────────────────────────────────────────────────────────────────────────────

private val DateCircleUnsel  = ContentLight
private val DateCircleSel    = CardContent
private val DateTextUnsel    = CardContent
private val DateTextSel      = ContentLight
private val OverlayBg        = AppGradientTop  // Map info bar background
private val CardBg           = ContentLight
private val WarningRed       = AccentPink

// ─────────────────────────────────────────────────────────────────────────────
//  Station List Screen — content only; Scaffold and gradient live in MainAppScreen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationListScreen(
    viewModel:         StationListViewModel = hiltViewModel(),
    onStationClick:    (String, Int) -> Unit,
    onNavigateToPoi:   (Double, Double, String) -> Unit = { _, _, _ -> },
    onNavigateToMap:   () -> Unit = {},
    /** Outer Scaffold padding forwarded from MainAppScreen */
    contentPadding: PaddingValues = PaddingValues()
) {
    // ── Collect ViewModel state ───────────────────────────────────────────────
    val selectedDate    by viewModel.selectedDate.collectAsState()
    val stableRouteDate by viewModel.stableRouteDate.collectAsState()
    val availableDates  by viewModel.availableDates.collectAsState()
    val routeItems      by viewModel.routeItems.collectAsState()
    val routeInfo       by viewModel.routeInfo.collectAsState()
    val isLoading       by viewModel.isLoading.collectAsState()
    val isOptimizing    by viewModel.isOptimizing.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is StationListViewModel.UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    // ── Map expand / collapse — state is persisted per date in the ViewModel ──
    val mapExpandedByDate by viewModel.mapExpandedByDate.collectAsState()
    val isMapExpanded = selectedDate?.let { mapExpandedByDate[it] } ?: true


    // ── Snackbar host (success banner anchored at top) ────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        // Snackbar host sits on top of everything
        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.TopCenter)
        ) { data ->
            Snackbar(
                snackbarData   = data,
                containerColor = AccentGreenAlt,
                contentColor   = Color.White,
                shape          = RoundedCornerShape(10.dp),
                modifier       = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Calendar row ──────────────────────────────────────────────────
            CalendarRow(
                availableDates = availableDates,
                selectedDate   = selectedDate,
                onDateSelected = viewModel::onDateSelected
            )

            // key(stableRouteDate) resets this block only when routeItems for the new
            // date have already arrived — both come from the same _routeStateFlow
            // emission.  This guarantees a single coherent render on date switches,
            // avoiding the stale-then-correct double-render that keying on selectedDate
            // caused.
            key(stableRouteDate) {
                RouteContent(
                    stableRouteDate = stableRouteDate,
                    routeItems      = routeItems,
                    routeInfo       = routeInfo,
                    availableDates  = availableDates,
                    isMapExpanded   = isMapExpanded,
                    isOptimizing    = isOptimizing,
                    onNavigateToMap = onNavigateToMap,
                    onStationClick  = onStationClick,
                    onNavigateToPoi = onNavigateToPoi,
                    onToggleMap     = viewModel::toggleMapExpanded,
                    onSaveScroll    = viewModel::saveScrollPositionForDate,
                    getScroll       = viewModel::getScrollPositionForDate
                )
            } // end key(stableRouteDate)
        }

        // ── Loading overlay ───────────────────────────────────────────────────
        if (isLoading) {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Route Content — all per-date state: scroll, map, slider, and station list.
//  Placed inside key(stableRouteDate) in StationListScreen so that the entire
//  subtree (including remembered state) is atomically reset on date switches.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteContent(
    stableRouteDate: LocalDate?,
    routeItems:      List<RouteListItem>,
    routeInfo:       DailyRouteInfo,
    availableDates:  List<LocalDate>,
    isMapExpanded:   Boolean,
    isOptimizing:    Boolean,
    onNavigateToMap: () -> Unit,
    onStationClick:  (String, Int) -> Unit,
    onNavigateToPoi: (Double, Double, String) -> Unit,
    onToggleMap:     () -> Unit,
    onSaveScroll:    (LocalDate, Int, Int) -> Unit,
    getScroll:       (LocalDate?) -> Pair<Int, Int>
) {
    // Captured at entry so onDispose saves the scroll position for the date
    // being LEFT — stableRouteDate already holds the incoming date by the
    // time dispose runs.
    val thisDate = remember { stableRouteDate }

    // ── Per-date scroll state ─────────────────────────────────────────────────
    val savedPosition = remember { getScroll(thisDate) }
    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex        = savedPosition.first,
        initialFirstVisibleItemScrollOffset = savedPosition.second
    )
    // Save position the moment this date's subtree is disposed (user
    // navigates to camera/gallery, or switches to a different date).
    DisposableEffect(Unit) {
        onDispose {
            thisDate?.let { date ->
                onSaveScroll(
                    date,
                    lazyListState.firstVisibleItemIndex,
                    lazyListState.firstVisibleItemScrollOffset
                )
            }
        }
    }

    // ── Slider / highlight ────────────────────────────────────────────────────
    var sliderFloat         by remember { mutableFloatStateOf(0f) }
    val activeIndex          = sliderFloat.roundToInt()
    val coroutineScope       = rememberCoroutineScope()
    var highlightedCardIndex by remember { mutableStateOf<Int?>(null) }
    var highlightJob         by remember { mutableStateOf<Job?>(null) }

    // Scroll-anchor fix: when a Home POI is prepended (position 0),
    // Compose would push the list down to keep the old first item visible.
    // The null guard on previousKey prevents scrollToItem(0) from firing
    // on the initial item load — only on a subsequent prepend.
    val firstItemKey = routeItems.firstOrNull()?.stableId
    var knownFirstKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(firstItemKey) {
        val previousKey = knownFirstKey
        knownFirstKey   = firstItemKey
        if (previousKey != null && firstItemKey != null && previousKey != firstItemKey) {
            lazyListState.scrollToItem(0)
        }
    }

    // mapHeight is derived here so it resets with every date change alongside
    // the rest of the state.
    val mapHeight = if (isMapExpanded) 200.dp else 40.dp

    // ── Map Widget container ──────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(top = 12.dp, bottom = 20.dp)
            .height(mapHeight)
            .clip(RoundedCornerShape(12.dp))
            .clipToBounds()
    ) {
        // fillMaxSize → AndroidView always has a stable non-zero size
        val density = LocalDensity.current
        val mapSafeMarginPx = with(density) { (40.dp + 16.dp).roundToPx() }
        MapWidget(
            routeItems           = routeItems,
            routeInfo            = routeInfo,
            isMapExpanded        = isMapExpanded,
            isInteractive        = false,
            isMiniMap            = true,
            highlightedItemIndex = activeIndex,
            safeMarginPx         = mapSafeMarginPx,
            modifier             = Modifier.fillMaxSize()
        )

        // ── Transparent clickable layer — excludes info bar and slider zone ──
        // bottom = 40dp: info bar height; end = 40dp: slider touch zone width.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(
                    bottom = 40.dp,
                    end    = if (isMapExpanded && routeItems.size >= 2) 40.dp else 0.dp
                )
                .clickable { onNavigateToMap() }
        )

        // ── Vertical route-preview slider — right edge, expanded only ────────
        if (isMapExpanded && routeItems.size >= 2) {
            val sliderHeightDp = mapHeight - 40.dp - 24.dp
            val sliderColors = SliderDefaults.colors(
                thumbColor         = WarningRed,
                activeTrackColor   = WarningRed,
                inactiveTrackColor = ContentLight.copy(alpha = 0.30f)
            )
            Slider(
                value         = sliderFloat,
                onValueChange = { raw ->
                    sliderFloat = raw
                    val newIndex = raw.roundToInt()
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(newIndex)
                    }
                    // Highlight the card the slider lands on; cancel any
                    // previous highlight timer so rapid drags don't stack.
                    highlightJob?.cancel()
                    highlightJob = coroutineScope.launch {
                        highlightedCardIndex = newIndex
                        delay(2000)
                        highlightedCardIndex = null
                    }
                },
                valueRange    = 0f..(routeItems.size - 1).toFloat(),
                colors        = sliderColors,
                track         = { state ->
                    SliderDefaults.Track(
                        sliderState = state,
                        colors      = sliderColors,
                        modifier    = Modifier.height(6.dp)
                    )
                },
                modifier      = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(y = (-20).dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            constraints.copy(
                                minWidth  = 0,
                                minHeight = 0,
                                maxWidth  = sliderHeightDp.roundToPx()
                            )
                        )
                        // Report swapped dimensions: parent sees a narrow tall element
                        // that matches the slider's true vertical visual footprint.
                        layout(placeable.height, placeable.width) {
                            val x = -(placeable.width / 2 - placeable.height / 2)
                            val y = -(placeable.height / 2 - placeable.width / 2)
                            placeable.placeRelative(x, y)
                        }
                    }
                    .graphicsLayer { rotationZ = -90f }
            )
        }

        // ── Info overlay — height matches collapsed map height (40.dp) ────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(40.dp)
                .background(OverlayBg.copy(alpha = 0.85f))
        ) {
            Row(
                modifier              = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MapStatBlock(
                    value = if (routeInfo.totalDistanceKm > 0.0)
                        String.format(Locale.US, "%.1f km", routeInfo.totalDistanceKm)
                    else "– km",
                    label = "Total Distance"
                )
                Spacer(modifier = Modifier.width(16.dp))
                MapStatBlock(
                    value = if (routeInfo.totalTimeMins > 0)
                        routeInfo.formattedDuration else "–",
                    label = "Drive Time"
                )
                Spacer(modifier = Modifier.width(16.dp))
                MapStatBlock(
                    value = if (routeInfo.waypointCount > 0)
                        "${routeInfo.waypointCount} stops" else "–",
                    label = "Waypoints"
                )
                // Expand / collapse toggle
                IconButton(
                    onClick  = { onToggleMap() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector        = if (isMapExpanded)
                            Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isMapExpanded) "Collapse map" else "Expand map",
                        tint               = ContentLight,
                        modifier           = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // ── "Work Schedule" section header ────────────────────────────────────────
    if (availableDates.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text       = "Work Schedule",
                fontWeight = FontWeight.SemiBold,
                fontSize   = 20.sp,
                color      = ContentLight
            )
        }
    }

    // ── Station list or empty state ───────────────────────────────────────────
    if (routeItems.isEmpty() && availableDates.isNotEmpty()) {
        EmptyStationState()
    } else {
        LazyColumn(
            state               = lazyListState,
            contentPadding      = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier            = Modifier.fillMaxSize()
        ) {
            itemsIndexed(routeItems, key = { _, s -> s.stableId }) { index, item ->
                val isHighlighted = index == highlightedCardIndex
                if (index == 0) {
                    RouteEndpointLabel(text = "Start", isStart = true)
                }
                when (item) {
                    is StationItem -> {
                        StationCard(
                            station        = item.station,
                            sequenceNumber = index + 1,
                            isHighlighted  = isHighlighted,
                            onClick        = { if (!isOptimizing) onStationClick(item.station.id, item.station.photoCount + item.station.issueCount) }
                        )
                    }
                    is PoiItem -> {
                        PoiCard(
                            poi            = item,
                            sequenceNumber = index + 1,
                            isHighlighted  = isHighlighted,
                            onClick        = { if (!isOptimizing) onNavigateToPoi(item.latitude, item.longitude, item.name) }
                        )
                    }
                }
                if (index == routeItems.lastIndex) {
                    RouteEndpointLabel(text = "Finish", isStart = false)
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Map Info Stat Block — value + label stacked vertically
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MapStatBlock(value: String, label: String) {
    // Negative spacedBy pulls the label 4.dp closer to the value.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((-4).dp)
    ) {
        Text(
            text       = value,
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color      = ContentLight,
            maxLines   = 1
        )
        Text(
            text       = label,
            fontSize   = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color      = ContentLight,
            maxLines   = 1
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Calendar Row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CalendarRow(
    availableDates: List<LocalDate>,
    selectedDate:   LocalDate?,
    onDateSelected: (LocalDate) -> Unit
) {
    if (availableDates.isEmpty()) {
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "📅", fontSize = 32.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text       = "No data",
                    color      = ContentLight,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign  = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text      = "Click ↑ to download the schedule (CSV)",
                    color     = ContentLight.copy(alpha = 0.6f),
                    fontSize  = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        // Offset 2 positions back so the selected date appears roughly centred on first draw.
        val initialIndex = remember { val i = availableDates.indexOf(selectedDate); (i - 2).coerceAtLeast(0) }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

        // Smooth-scroll to re-centre whenever the user picks a new date.
        // Keyed only on selectedDate — availableDates is stable once loaded.
        LaunchedEffect(selectedDate) {
            if (selectedDate == null) return@LaunchedEffect
            val index = availableDates.indexOf(selectedDate)
            if (index >= 0) {
                listState.animateScrollToItem((index - 2).coerceAtLeast(0))
            }
        }

        LazyRow(
            state                 = listState,
            contentPadding        = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier              = Modifier.padding(top = 8.dp)
        ) {
            items(availableDates) { date ->
                DateItem(
                    date       = date,
                    isSelected = date == selectedDate,
                    onClick    = { onDateSelected(date) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Date Item — circle with day number + short day name
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DateItem(
    date:       LocalDate,
    isSelected: Boolean,
    onClick:    () -> Unit
) {
    val dayNumber = date.dayOfMonth.toString()
    val dayName   = date.dayOfWeek.getDisplayName(
        java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH
    )

    val bgColor   = if (isSelected) DateCircleSel  else DateCircleUnsel
    val textColor = if (isSelected) DateTextSel    else DateTextUnsel

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .background(bgColor, CircleShape)
                .then(if (isSelected) Modifier.border(1.dp, ContentLight, CircleShape) else Modifier)
        ) {
            Text(
                text       = dayNumber,
                color      = textColor,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text       = dayName,
            color      = ContentLight,
            fontSize   = 14.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Station Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StationCard(
    station:        StationWithCounts,
    sequenceNumber: Int,
    isHighlighted:  Boolean = false,
    onClick:        () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isHighlighted) Modifier.border(2.dp, WarningRed, RoundedCornerShape(12.dp))
                else Modifier
            )
            .background(CardBg)
            .clickable { onClick() }
    ) {
        Row(
            modifier          = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sequence number
            Text(
                text          = sequenceNumber.toString(),
                fontSize      = 36.sp,
                fontWeight    = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp,
                color         = CardContent,
                modifier      = Modifier.width(52.dp),
                textAlign     = TextAlign.Start
            )

            // Station name + train icon + counters
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment      = Alignment.CenterVertically,
                    horizontalArrangement  = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Train,
                        contentDescription = null,
                        tint               = CardContent,
                        modifier           = Modifier.size(22.dp)
                    )
                    Text(
                        text       = station.name,
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = CardContent,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.Image,
                        contentDescription = "Photos",
                        tint               = CardContent,
                        modifier           = Modifier.size(15.dp)
                    )
                    Text(
                        text     = station.photoCount.toString(),
                        fontSize = 14.sp,
                        color    = CardContent
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector        = Icons.Default.Warning,
                        contentDescription = "Issues",
                        tint               = WarningRed,
                        modifier           = Modifier.size(15.dp)
                    )
                    Text(
                        text     = station.issueCount.toString(),
                        fontSize = 14.sp,
                        color    = WarningRed
                    )
                }
            }

            // Navigation button
            IconButton(
                onClick  = {
                    if (station.latitude != 0.0 && station.longitude != 0.0) {
                        val uri = Uri.parse(
                            "geo:${station.latitude},${station.longitude}?q=${station.latitude},${station.longitude}(${Uri.encode(station.name)})"
                        )
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "No map application found", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Coordinates not found for this station", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    imageVector        = Icons.Default.Place,
                    contentDescription = "Navigate to station",
                    tint               = CardContent,
                    modifier           = Modifier.size(32.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  POI Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PoiCard(
    poi:            PoiItem,
    sequenceNumber: Int,
    isHighlighted:  Boolean = false,
    onClick:        () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isHighlighted) Modifier.border(2.dp, WarningRed, RoundedCornerShape(12.dp))
                else Modifier
            )
            .background(CardBg)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text          = sequenceNumber.toString(),
                fontSize      = 36.sp,
                fontWeight    = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp,
                color         = CardContent,
                modifier      = Modifier.width(52.dp),
                textAlign     = TextAlign.Start
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.Place,
                        contentDescription = null,
                        tint               = CardContent,
                        modifier           = Modifier.size(22.dp)
                    )
                    Text(
                        text       = poi.name,
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = CardContent,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Route endpoint labels — "Start" (left-aligned) and "Finish" (right-aligned)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RouteEndpointLabel(text: String, isStart: Boolean) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isStart) {
            Icon(
                imageVector        = Icons.Default.Adjust,
                contentDescription = null,
                tint               = ContentLight,
                modifier           = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = text, fontSize = 16.sp, color = ContentLight)
        } else {
            Spacer(modifier = Modifier.weight(1f))
            Text(text = text, fontSize = 16.sp, color = ContentLight)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector        = Icons.Default.Adjust,
                contentDescription = null,
                tint               = ContentLight,
                modifier           = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Empty state placeholder
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyStationState() {
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "🚉", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text       = "Список порожній. Додайте новий вокзал.",
                color      = ContentLight.copy(alpha = 0.7f),
                fontSize   = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign  = TextAlign.Center
            )
        }
    }
}


