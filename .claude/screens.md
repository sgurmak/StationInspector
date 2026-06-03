# Screen Inventory

## MainAppScreen
- **File**: `app/src/main/java/com/example/stationinspector/ui/screens/MainAppScreen.kt`
- **Role**: Root composable. Owns the Scaffold, vertical gradient background (`AppGradientTop`→`AppGradientBottom`), and the Material 3 `NavigationBar` (4 tabs). `currentTab` is `rememberSaveable`.
- **ViewModel**: resolves `RouteViewModel` for the selected date (Export tab needs it); child tab screens resolve their own ViewModels via `hiltViewModel()` (same `NavBackStackEntry` → shared `RouteViewModel`).

## StationListScreen (Tab 0: Work)
- **File**: `app/src/main/java/com/example/stationinspector/ui/screens/StationListScreen.kt`
- **Role**: Main work view. Horizontal calendar date selector, collapsible mini map widget with route stats (distance/time/stops), and a scrollable list of station cards and POI cards for the selected date.
- **Key components**: `CalendarRow`, `MapWidget` (non-interactive; **mounted only while expanded** — collapsed it would render invisibly behind the info bar and waste battery), `StationCard`/`PoiCard` (both built on a shared `RouteCardScaffold`), success snackbar banner.
- **ViewModels**: `RouteViewModel` + `SettingsViewModel` (this screen hosts the SnackbarHost + loading overlay for import/clear).

## MapScreen (Tab 1: Map)
- **File**: `app/src/main/java/com/example/stationinspector/ui/screens/MapScreen.kt`
- **Role**: Full interactive map with bottom sheet. Top bar shows route info (distance/time/stops). Bottom sheet contains: search box (Mapy.cz geocoding), shortcut quick-actions (Home/Work/custom), reorderable station/POI list with swipe-to-dismiss (hide stations, delete POIs). "Optimize" button triggers VROOM route optimization. POI coordinate editing mode with draggable pin.
- **Key features**: `BottomSheetScaffold`, `ReorderableMapList` (drag-and-drop), shortcut management dialogs, coordinate editing with confirmation dialog. Stateless sub-composables `SearchResultsList` and `ShortcutsRow` were extracted (each with a `@Preview`); the inline `ShortcutEditSheet` remains.
- **ViewModels**: `RouteViewModel` + `SearchViewModel` + `ShortcutsViewModel` (the screen clears the search after add-to-route).

## SettingsScreen (Tab 3: Settings)
- **File**: `app/src/main/java/com/example/stationinspector/ui/screens/SettingsScreen.kt`
- **Role**: Two actions: "Import CSV" (opens file picker for station schedule) and "Clear Storage" (deletes all stations and photos with confirmation dialog).

## CameraScreen
- **File**: `app/src/main/java/com/example/stationinspector/ui/inspection/CameraScreen.kt`
- **Role**: Full-screen camera with CameraX preview. Top bar: flash toggle (auto/on/off) + 3-zone selector strip (Nádraží/Čekárna/WC with per-zone photo counts) + done checkmark. Bottom: station name + last photo thumbnail with delete + dual shutter buttons ("Defects" in pink, "Photo" in white). Pinch-to-zoom supported.
- **ViewModel**: `ZoneInspectionViewModel`

## GalleryScreen
- **File**: `app/src/main/java/com/example/stationinspector/ui/inspection/GalleryScreen.kt`
- **Role**: Photo review after shooting. Shows station name, 3-zone selector with counters, Photos/Defects toggle tabs, 3-column photo grid with delete overlays, and "Confirm inspection" button at bottom.
- **ViewModel**: `ZoneInspectionViewModel` (shared with CameraScreen via nav args)

## ExportScreen (Tab 2: Export)
- **File**: `app/src/main/java/com/example/stationinspector/ui/export/ExportScreen.kt`
- **Role**: Export summary showing photo counts (ordinary + defects) and station counts. "Generate ZIP-archive" button triggers WorkManager export. On success, opens Android share intent with the ZIP file. Loading overlay during export.
- **ViewModel**: `ExportViewModel`

## MapWidget (Reusable Component)
- **File**: `app/src/main/java/com/example/stationinspector/ui/components/MapWidget.kt`
- **Role**: Reusable osmdroid map wrapped in `AndroidView`. Renders Mapy.cz tiles, numbered station markers (purple teardrop pins), route polylines (dark outline + purple main line with direction arrows). Used in both StationListScreen (non-interactive, collapsible) and MapScreen (interactive, full-size).
- **Props**: routeItems, routeInfo, isMapExpanded, editingPoi, isInteractive, isMiniMap, highlightedItemIndex, top/bottomPaddingPx, safeMarginPx
- **Perf**: osmdroid is configured once (not per recomposition); route overlays rebuild only in a keyed `LaunchedEffect` (not on every recompose); the inactive mini-marker bitmap is cached and reused.

## SplashScreen
- **File**: `app/src/main/java/com/example/stationinspector/ui/screens/SplashScreen.kt`
- **Role**: Cold-start FleetWay branding (animated logo + gradient text) overlaid on the Work destination until its animation completes, then dismissed via a `rememberSaveable` flag in `NavGraph`.

> The legacy `ui/zone/ZoneListScreen` + `ZoneGalleryScreen` (Ukrainian-label inspection path) were **removed**. The only inspection flow is Camera → Gallery (Czech zone labels).
