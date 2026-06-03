# ViewModels

The former monolithic `StationListViewModel` was split into four focused
ViewModels in `ui/screens/`. UI models (`RouteListItem`/`StationItem`/`PoiItem`,
`StationWithCounts`, `DailyRouteInfo`, `ShortcutUiModel`, `SearchUiState`,
`UiEvent`) and the UI↔domain mappers now live in `ui/screens/RouteModels.kt`.

## RouteViewModel
- **File**: `ui/screens/RouteViewModel.kt`
- **Scope**: shared instance across the Work and Map tabs (same `NavBackStackEntry`).
- **Injects**: `StationRepository`, `RouteRepository`, `PoiRepository`, `PreferencesRepository`, `ShortcutRepository`, `TransactionRunner`, `@IoDispatcher CoroutineDispatcher`. (No DAOs, no `AppDatabase`, no `Dispatchers.IO` hardcoded.)
- **State**: `selectedDate`, `availableDates`, `routeItems`, `stableRouteDate`, `routeInfo`, `isExportButtonEnabled`, `editingPoi`, `isOptimizing`, `isRoundTripEnabled`, `mapExpandedByDate`, `uiEvent` (snackbars).
- **Operations**: `onDateSelected`, `setRoundTripEnabled`, `optimizeRoute` (maps items→`RouteWaypoint`, applies the optimized order back by id+isStation), `addPoiToRoute`, `addShortcutToRoute` (Home/round-trip logic), `toggleHidePoi`, `deletePoi`, `startEditingPoi`/`cancelEditingPoi`/`saveEditedPoi`, `reorderItems`, scroll persistence, `toggleMapExpanded`, `onInspectionConfirmed`. Private `rebuildHomePointsAndIndices`/`insertPoiAtCorrectOrderIndex`/`calculateDailyRoute`.

## SearchViewModel
- **File**: `ui/screens/SearchViewModel.kt` — **Injects**: `MapyCzRepository`.
- **State**: `searchQuery`, `searchState` (Idle/Loading/Success/Error). Debounced 300ms; maps `PoiLocation`→`PoiItem` for the UI.
- **Operations**: `onSearchQueryChanged`, `clearSearch`.

## ShortcutsViewModel
- **File**: `ui/screens/ShortcutsViewModel.kt` — **Injects**: `ShortcutRepository`.
- **State**: `shortcuts: List<ShortcutUiModel>`. Seeds Home/Work on init (`ensureDefaults`).
- **Operations**: `updateShortcut`, `createNewShortcut`, `deleteShortcut` (Home/Work id `"1"`/`"2"` kept but cleared; constants on `Shortcut.ID_HOME/ID_WORK/ID_NEW/NAME_HOME/NAME_WORK`).

## SettingsViewModel
- **File**: `ui/screens/SettingsViewModel.kt` — **Injects**: `ImportStationsUseCase`, `StationRepository`.
- **State**: `isLoading`, `uiEvent`. (Observed by the Work screen, which hosts the SnackbarHost.)
- **Operations**: `importStationsFromCsv(inputStream)` (the screen opens the content stream — no `Context` in the VM), `clearAllData` (deletes photo files + rows).

---

## ZoneInspectionViewModel
- **File**: `ui/inspection/ZoneInspectionViewModel.kt` — Camera + Gallery (shared via `SavedStateHandle` nav args).
- **Injects**: `StationRepository`, `FileStorageManager`, `CameraXController`, `ImageCompressor`, `SavedStateHandle`.
- **State**: `stationName`, `selectedZone`, `zoneCounts`, `photos`.
- **Operations**: `selectZone`, `initializeCamera`, `onZoomChange`, `setFlashMode`, `capturePhoto` (capture → compress → save file → save row; recycles the bitmap), `deletePhoto` (file + row).

## ExportViewModel
- **File**: `ui/export/ExportViewModel.kt` — **Injects**: `StationRepository`, `WorkManager`.
- **State**: `state: ExportViewState` (unexportedCount, exportState, zipUri, photos).
- **Operations**: `setSelectedDate`, `startExport` (enqueue `ExportZipWorker`), `resetState`.
