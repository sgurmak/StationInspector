package com.example.stationinspector.ui.components

import android.preference.PreferenceManager
import com.example.stationinspector.BuildConfig
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clipToBounds
import org.osmdroid.views.overlay.Polyline
import com.example.stationinspector.ui.screens.DailyRouteInfo
import com.example.stationinspector.ui.screens.RouteListItem


@Composable
fun MapWidget(
    routeItems:    List<RouteListItem>,
    routeInfo:     DailyRouteInfo,
    isMapExpanded: Boolean = true,
    editingPoi:    RouteListItem? = null,
    onMapCenterChange: ((org.osmdroid.util.GeoPoint) -> Unit)? = null,
    onGetCenterCallback: (( () -> org.osmdroid.util.GeoPoint ) -> Unit)? = null,
    topPaddingPx: Int = 0,
    bottomPaddingPx: Int = 0,
    safeMarginPx: Int = 0,
    isInteractive:       Boolean = true,
    isMiniMap:           Boolean = false,
    highlightedItemIndex: Int? = null,
    modifier:            Modifier = Modifier
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Configure osmdroid once per composition
    Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
    Configuration.getInstance().userAgentValue = context.packageName

    val mapView = remember {
        MapView(context).apply {
            // ── Pinch-to-zoom on; legacy +/- buttons off ──────────────────────
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)

            // ── Custom Mapy.cz tile source ────────────────────────────────────
            val mapyCzTileSource = object : OnlineTileSourceBase(
                "Mapy.cz",
                0, 19, 256, ".png",
                arrayOf("https://api.mapy.cz/v1/maptiles/basic/256/")
            ) {
                override fun getTileURLString(pMapTileIndex: Long): String {
                    return baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
                            MapTileIndex.getX(pMapTileIndex) + "/" +
                            MapTileIndex.getY(pMapTileIndex) + "?apikey=${BuildConfig.MAPY_CZ_API_KEY}"
                }
            }
            setTileSource(mapyCzTileSource)

            // ── Default view: centred on Czechia at zoom 8 ───────────────────
            controller.setCenter(org.osmdroid.util.GeoPoint(49.8175, 15.4730))
            controller.setZoom(8.0)

            // ── Restrict panning to the Czech Republic bounding box ──────────
            // N 51.05 / S 48.55 / W 12.09 / E 18.86
            setScrollableAreaLimitDouble(
                org.osmdroid.util.BoundingBox(51.05, 18.86, 48.55, 12.09)
            )

            // ── Prevent zooming out past country level ────────────────────────
            minZoomLevel = 7.0

            addMapListener(object : org.osmdroid.events.MapListener {
                override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                    onMapCenterChange?.invoke(org.osmdroid.util.GeoPoint(mapCenter.latitude, mapCenter.longitude))
                    return true
                }
                override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                    onMapCenterChange?.invoke(org.osmdroid.util.GeoPoint(mapCenter.latitude, mapCenter.longitude))
                    return true
                }
            })
        }
    }

    LaunchedEffect(mapView) {
        onGetCenterCallback?.invoke {
            org.osmdroid.util.GeoPoint(mapView.mapCenter.latitude, mapView.mapCenter.longitude)
        }
    }

    // ── Lifecycle observer: resume / pause the MapView with the Activity ─────
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE  -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Fix 1 (part B): clear overlays before detaching so osmdroid
            // never tries to close InfoWindows on a potentially unmodifiable list.
            mapView.overlays.clear()
            mapView.onDetach()
        }
    }

    LaunchedEffect(editingPoi) {
        if (editingPoi != null) {
            val point = org.osmdroid.util.GeoPoint(editingPoi.latitude, editingPoi.longitude)
            mapView.controller.setCenter(point)
            mapView.controller.setZoom(17.0)
            onMapCenterChange?.invoke(point)
            
            // Explicitly kill the native ghost marker
            val markersToRemove = mapView.overlays.filterIsInstance<org.osmdroid.views.overlay.Marker>()
                .filter { it.title?.contains(editingPoi.name) == true }
            if (markersToRemove.isNotEmpty()) {
                mapView.overlays.removeAll(markersToRemove)
                mapView.invalidate()
            }
        }
    }

    LaunchedEffect(routeItems, routeInfo.polylinePoints, isMapExpanded, editingPoi) {
        if (isMapExpanded && editingPoi == null) {
            val validItems = routeItems.filter { it.latitude != 0.0 && it.longitude != 0.0 }
            val margin = if (safeMarginPx > 0) safeMarginPx else 120
            val scrollY = (bottomPaddingPx - topPaddingPx) / 2

            if (routeInfo.polylinePoints.isNotEmpty()) {
                val box = org.osmdroid.util.BoundingBox.fromGeoPoints(routeInfo.polylinePoints)
                mapView.post {
                    mapView.zoomToBoundingBox(box, false, margin)
                    mapView.scrollBy(0, scrollY)
                }
            } else if (validItems.isNotEmpty()) {
                val box = org.osmdroid.util.BoundingBox.fromGeoPoints(
                    validItems.map { org.osmdroid.util.GeoPoint(it.latitude, it.longitude) }
                )
                mapView.post {
                    mapView.zoomToBoundingBox(box, false, margin)
                    mapView.scrollBy(0, scrollY)
                }
            }
        }
    }

    // ── Render the osmdroid MapView inside Compose ────────────────────────────

    // ── Render the osmdroid MapView inside Compose ────────────────────────────
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .clipToBounds(),
        factory  = { mapView },
        update = { view ->
            view.setMultiTouchControls(isInteractive)
            if (!isInteractive) {
                view.setOnTouchListener { _, _ -> true }
            } else {
                view.setOnTouchListener(null)
            }

            // Clear existing lines and markers safely without killing MapEventsOverlay
            view.overlays.removeAll { it is org.osmdroid.views.overlay.Polyline || it is org.osmdroid.views.overlay.Marker }

            // ── Route polyline (single purple line) ──────────────────────────────
            if (routeInfo.polylinePoints.isNotEmpty()) {
                val polylineMain = Polyline(view)
                polylineMain.infoWindow = null
                polylineMain.outlinePaint.color = android.graphics.Color.parseColor("#8B5CF6")
                polylineMain.outlinePaint.strokeWidth = 8f
                polylineMain.setPoints(ArrayList(routeInfo.polylinePoints))
                view.overlays.add(polylineMain)
            }

            // ── Numbered station markers ──────────────────────────────────────────
            // The highlighted marker is deferred and added last so it renders above
            // the polyline and all other markers.
            val validItems = routeItems.filter { it.latitude != 0.0 && it.longitude != 0.0 && it.stableId != editingPoi?.stableId }
            val isRoundTrip = !isMiniMap && validItems.size >= 2 &&
                              validItems.first().name == "Home" &&
                              validItems.last().name == "Home"

            var highlightedMarker: org.osmdroid.views.overlay.Marker? = null
            validItems.forEachIndexed { index, item ->
                if (isRoundTrip && index == validItems.lastIndex) {
                    return@forEachIndexed // Skip the last marker as it overlaps with the first one
                }

                val marker = org.osmdroid.views.overlay.Marker(view)
                marker.infoWindow = null
                marker.position = org.osmdroid.util.GeoPoint(item.latitude, item.longitude)
                
                val markerLabel = if (isRoundTrip && index == 0) {
                    "1/${validItems.size}"
                } else {
                    "${index + 1}"
                }

                marker.title = "$markerLabel. ${item.name}"
                if (isMiniMap) {
                    if (index == highlightedItemIndex) {
                        marker.icon = createMiniHighlightedMarkerBitmap(context, index + 1)
                        marker.setAnchor(
                            org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                            org.osmdroid.views.overlay.Marker.ANCHOR_CENTER
                        )
                        highlightedMarker = marker   // will be added after the loop
                        return@forEachIndexed
                    } else {
                        marker.icon = createMiniInactiveMarkerBitmap(context)
                    }
                    marker.setAnchor(
                        org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                        org.osmdroid.views.overlay.Marker.ANCHOR_CENTER
                    )
                } else {
                    marker.icon = createLargeNumberedMarkerBitmap(context, markerLabel)
                    marker.setAnchor(
                        org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                        org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM
                    )
                }
                view.overlays.add(marker)
            }
            // Add highlighted marker on top of everything else
            highlightedMarker?.let { view.overlays.add(it) }

            // Zoom to bounding box logic moved to LaunchedEffect to avoid jitter during sheet scrolling

            // Force redraw
            view.invalidate()
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Full-map marker: large teardrop with white number or text label (e.g. 1/N)
// ─────────────────────────────────────────────────────────────────────────────

fun createLargeNumberedMarkerBitmap(
    context: android.content.Context,
    number:  String
): android.graphics.drawable.BitmapDrawable {
    val width  = if (number.length > 2) 110 else 80
    val height = 120
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#8B5CF6")
        style = android.graphics.Paint.Style.FILL
    }
    val radius = width / 2f
    val path   = android.graphics.Path()
    val oval   = android.graphics.RectF(0f, 0f, width.toFloat(), width.toFloat())
    path.arcTo(oval, 150f, 240f)
    path.lineTo(width / 2f, height.toFloat())
    path.close()
    canvas.drawPath(path, paint)

    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color          = android.graphics.Color.WHITE
        textSize       = when {
            number.length > 4 -> 22f
            number.length > 3 -> 26f
            number.length > 2 -> 30f
            else -> 36f
        }
        textAlign      = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
    }
    val textY = radius - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(number, radius, textY, textPaint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

// ─────────────────────────────────────────────────────────────────────────────
//  Mini-map inactive marker: small solid dot, no label (30 px)
// ─────────────────────────────────────────────────────────────────────────────

fun createMiniInactiveMarkerBitmap(
    context: android.content.Context
): android.graphics.drawable.BitmapDrawable {
    val size   = 30
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint  = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#8B5CF6")
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

// ─────────────────────────────────────────────────────────────────────────────
//  Mini-map highlighted marker: larger circle with white sequence number (60 px)
// ─────────────────────────────────────────────────────────────────────────────

fun createMiniHighlightedMarkerBitmap(
    context: android.content.Context,
    number:  Int
): android.graphics.drawable.BitmapDrawable {
    val size   = 60
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val radius = size / 2f
    val circlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#CA065E")
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawCircle(radius, radius, radius, circlePaint)

    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color          = android.graphics.Color.WHITE
        textSize       = 28f
        textAlign      = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
    }
    val textY = radius - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(number.toString(), radius, textY, textPaint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}
