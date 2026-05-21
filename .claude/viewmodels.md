# ViewModels

## StationListViewModel

- **File**: `app/src/main/java/com/example/stationinspector/ui/screens/StationListViewModel.kt`
- **Scope**: Shared across Work (Tab 0), Map (Tab 1), and Settings (Tab 3) tabs
- **Size**: ~700 lines (largest ViewModel)

### Injected Dependencies
- `StationRepository` (domain interface)
- `RouteRepository` (domain interface)
- `MapyCzRepository` (data layer — breaks clean arch)
- `ShortcutDao` (data layer — breaks clean arch)
- `PoiDao` (data layer — breaks clean arch)
- `StationDao` (data layer — breaks clean arch)
- `DataStore<Preferences>`

### State (StateFlow)
| State | Type | Description |
|---|---|---|
| `selectedDate` | `LocalDate?` | Currently selected work day |
| `availableDates` | `List<LocalDate>` | All dates with assigned stations |
| `routeItems` | `List<RouteListItem>` | Combined stations + POIs for selected date, sorted by orderIndex |
| `routeInfo` | `DailyRouteInfo` | Total distance/time/stops + polyline points |
| `isExportButtonEnabled` | `Boolean` | True when there are photos to export |
| `isLoading` | `Boolean` | CSV import / clear data in progress |
| `isOptimizing` | `Boolean` | Route optimization in progress |
| `searchQuery` | `String` | Current Mapy.cz search text |
| `searchState` | `SearchUiState` | Idle / Loading / Success(results) / Error(message) |
| `editingPoi` | `RouteListItem?` | POI being coordinate-edited on map |
| `shortcuts` | `List<ShortcutEntity>` | Quick-action shortcuts |
| `isRoundTripEnabled` | `Boolean` | Home round-trip toggle (DataStore) |

### Operations
- `onDateSelected(date)` — switch active work day
- `importStationsFromCsv(context, uri)` — parse CSV, clean names, deduplicate, save, seed coordinates
- `clearAllData()` — delete all stations and photos
- `optimizeRoute()` — send active items to VROOM, update order in DB
- `addPoiToRoute(poi)` — add search result as POI for current date
- `addShortcutToRoute(shortcutId, poi)` — add shortcut POI (with Home round-trip logic)
- `toggleHidePoi(id)` — hide/show item from route calculation
- `deletePoi(id)` — remove POI from DB
- `startEditingPoi(poi)` / `cancelEditingPoi()` / `saveEditedPoi(poi, lat, lon)` — coordinate editing flow
- `updateShortcut(id, poi, customName)` / `createNewShortcut(poi, customName)` / `deleteShortcut(id)` — shortcut CRUD
- `setRoundTripEnabled(enabled)` — persist round-trip preference

### UI Models (defined in same file)
- `RouteListItem` (sealed interface) — `StationItem` | `PoiItem`
- `StationWithCounts` — id, name, lat/lon, photoCount, issueCount
- `DailyRouteInfo` — totalDistanceKm, totalTimeMins, waypointCount, polylinePoints
- `SearchUiState` — Idle | Loading | Success | Error

---

## ZoneInspectionViewModel

- **File**: `app/src/main/java/com/example/stationinspector/ui/inspection/ZoneInspectionViewModel.kt`
- **Scope**: Camera + Gallery screens (shared via SavedStateHandle nav args)

### Injected Dependencies
- `StationRepository`
- `FileStorageManager`
- `CameraXController`
- `ImageCompressor`
- `SavedStateHandle` (provides `stationId`)

### State (StateFlow)
| State | Type | Description |
|---|---|---|
| `stationName` | `String` | Loaded from DB for display |
| `selectedZone` | `PhotoZone` | Currently active zone (default: ENTRANCE) |
| `zoneCounts` | `List<ZonePhotoCount>` | Per-zone ordinary + defect counts |
| `photos` | `List<Photo>` | Photos for current station + selected zone |

### Operations
- `selectZone(zone)` — switch active zone
- `initializeCamera(lifecycleOwner, surfaceProvider)` — bind CameraX
- `onZoomChange(zoomChange)` — pinch-to-zoom
- `setFlashMode(flashMode)` — auto/on/off
- `capturePhoto(type)` — capture → compress → save to file → save to DB
- `deletePhoto(photoId)` — delete file + DB record

---

## ExportViewModel

- **File**: `app/src/main/java/com/example/stationinspector/ui/export/ExportViewModel.kt`
- **Scope**: Export screen only

### Injected Dependencies
- `StationRepository`
- `WorkManager`

### State (StateFlow)
| State | Type | Description |
|---|---|---|
| `state` | `ExportViewState` | unexportedCount, exportState (IDLE/RUNNING/SUCCESS/ERROR), zipUri, photos |

### Operations
- `setSelectedDate(dateStr)` — set export target date
- `startExport()` — enqueue `ExportZipWorker`, observe work result
- `resetState()` — return to IDLE

---

## ZoneListViewModel (Legacy)

- **File**: `app/src/main/java/com/example/stationinspector/ui/zone/ZoneListViewModel.kt`
- **Scope**: Legacy ZoneListScreen

### Injected Dependencies
- `StationRepository`
- `SavedStateHandle` (provides `stationId`)

### State (StateFlow)
| State | Type | Description |
|---|---|---|
| `stationName` | `String` | Station display name |
| `zonesWithCounts` | `List<ZoneWithStats>` | Per-zone photo + issue counts (3 zones) |
| `totalPhotoCount` | `Int` | All photos across all zones |

### Operations
- `markStationCompleted()` — update station status to COMPLETED

### Constants
- `INSPECTION_ZONES`: 3 zones with Ukrainian display names (Вокзал, Зона очікування, WC)
