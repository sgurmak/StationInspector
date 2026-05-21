# Screen Inventory

## MainAppScreen
- **File**: `app/src/main/java/com/example/stationinspector/ui/screens/MainAppScreen.kt`
- **Role**: Root composable. Owns the Scaffold, vertical gradient background (#392153 → #13111A), and 4-tab BottomNavBar.
- **ViewModel**: Creates shared `StationListViewModel` via `hiltViewModel()` and passes it to child tab screens.

## StationListScreen (Tab 0: Work)
- **File**: `app/src/main/java/com/example/stationinspector/ui/screens/StationListScreen.kt`
- **Role**: Main work view. Horizontal calendar date selector, collapsible mini map widget with route stats (distance/time/stops), and a scrollable list of station cards and POI cards for the selected date.
- **Key components**: `CalendarRow`, `MapWidget` (non-interactive), `StationCard` (with photo/issue counts + nav button), `PoiCard`, success snackbar banner.

## MapScreen (Tab 1: Map)
- **File**: `app/src/main/java/com/example/stationinspector/ui/screens/MapScreen.kt`
- **Role**: Full interactive map with bottom sheet. Top bar shows route info (distance/time/stops). Bottom sheet contains: search box (Mapy.cz geocoding), shortcut quick-actions (Home/Work/custom), reorderable station/POI list with swipe-to-dismiss (hide stations, delete POIs). "Optimize" button triggers VROOM route optimization. POI coordinate editing mode with draggable pin.
- **Key features**: `BottomSheetScaffold`, `ReorderableMapList` (drag-and-drop), shortcut management dialogs, coordinate editing with confirmation dialog for stations.

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
- **Props**: routeItems, routeInfo, isMapExpanded, editingPoi, isInteractive, bottomPaddingPx, safeMarginPx

## ZoneListScreen (Legacy)
- **File**: `app/src/main/java/com/example/stationinspector/ui/zone/ZoneListScreen.kt`
- **Role**: Older zone-by-zone inspection screen with Ukrainian labels (Вокзал, Зона очікування, WC). Shows per-zone photo/issue counts with camera action buttons. Has "Підтвердити інспекцію" (Confirm inspection) bottom bar.
- **ViewModel**: `ZoneListViewModel`
- **Status**: Legacy — superseded by the Camera/Gallery flow but still in codebase.

## ZoneGalleryScreen (Legacy)
- **File**: `app/src/main/java/com/example/stationinspector/ui/inspection/ZoneGalleryScreen.kt`
- **Role**: Older zone gallery with Ukrainian labels. Category tabs for "Звичайні фото" / "Косяки". Photo grid with delete buttons. "Додати фото" bottom button.
- **ViewModel**: `ZoneInspectionViewModel`
- **Status**: Legacy — superseded by GalleryScreen but still in codebase.
