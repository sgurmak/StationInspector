# Key Business Logic

## Photo Categorization

### Types
- **`CLIENT_REPORT`** — Normal inspection photos for client delivery
- **`INTERNAL_DEFECT`** — Defect documentation (issues found during inspection)

### Zones
- **ENTRANCE** (Czech: Nádraží) — Station building
- **PLATFORM** (Czech: Čekárna) — Waiting area
- **RESTROOM** (Czech: WC) — Toilets

### Compression Rules (ImageCompressor)
| Photo Type | Max Dimensions | Starting Quality | Max File Size |
|---|---|---|---|
| CLIENT_REPORT | 960×1280 | 80% | 500 KB |
| INTERNAL_DEFECT | 1200×1600 | 85% | 800 KB |

The compressor first resizes (maintaining aspect ratio), then iteratively reduces JPEG quality in 5% steps until the file fits within the size limit (min quality: 10%).

### Storage
- Photos are saved as `{UUID}.jpg` in `context.filesDir`
- `FileStorageManager` handles save/delete/exists operations on `Dispatchers.IO`
- File paths stored as absolute paths in the `photos.localPath` DB column

---

## Export Pipeline

### Trigger
`ExportViewModel.startExport()` → enqueues `ExportZipWorker` via WorkManager as a unique foreground work request.

### ExportZipWorker Steps

1. **Filter**: Get all photos with `assignedDate` matching the export date
2. **Build folder structure** in cache directory:
   ```
   Fotky_ČD/                    ← Normal photos (CLIENT_REPORT)
     {StationName}/
       {ZoneCzech}/
         {StationName}_1.jpg
         {StationName}_2.jpg
   Závady/                       ← Defects (INTERNAL_DEFECT)
     {StationName}/
       {ZoneCzech}/
         {StationName}_1.jpg
   ```
3. **Zone name mapping** (`ZONE_FOLDER`, enum → Czech folder): ENTRANCE → Nádraží, PLATFORM → Čekárna, RESTROOM → WC (other zones fall back to the enum name). Every path segment passes through `sanitizeSegment()` (strips separators/reserved chars, rejects `.`/`..`) so a crafted station name can't escape the staging dir.
4. Stations are resolved **once** into a map (not one query per photo).
5. **Create ZIP** with Zip4j inside `use {}`.
6. **Cleanup**: the staging dir is removed in a `finally` (on success, failure, or cancellation); old `session_*` export dirs are pruned.
7. **Return** a FileProvider URI; `ExportScreen` opens the Android share intent.

> "Clear storage" deletes photo **files** too: `StationRepository.clearAllData()` → `FileStorageManager.clearAllPhotoFiles()` before deleting rows. It deliberately **keeps** the `station_coord_overrides` table so manual coordinate corrections survive a clear + re-import (see CSV Import step 5).

### ZIP Naming
Format: `KPI_{dd.MM}.zip` (e.g., `KPI_15.04.zip`)

### Foreground Notification
- Channel: `export_channel`
- Title: "Генерація звіту"
- Text: "Створення ZIP-архіву..."
- Service type: `dataSync`

---

## Route Optimization

### Flow (RouteViewModel.optimizeRoute → RouteRepository)

1. **Filter** (VM): active (non-hidden) items with valid coordinates; require ≥3, else snackbar.
2. The VM maps items to domain `RouteWaypoint(id, isStation, lat, lon)` and calls `routeRepository.optimizeAndFetchGeometry(waypoints): Result<OptimizedRoute>`.
3. **Impl**: detect round-trip (first/last share coords), build the VROOM request (vehicle start/end + intermediate jobs), `POST /optimization`.
4. **Reconstruct** via the pure, unit-tested `reconstructOptimizedOrder(waypoints, orderedJobIds)` → start + optimizer-ordered intermediates + end.
5. The VM maps the optimized order back to station/POI `orderIndex` (by id+isStation, hidden items appended) and persists via `StationRepository`/`PoiRepository` ordering APIs.

> The optimize path no longer does a redundant `/directions` fetch (its polyline was unused and its `hashCode` cache key never hit). Geometry is computed fresh by the daily-route calculation below.

### Daily Route Calculation (automatic, on routeItems change)

For each consecutive pair of active items:
1. Check `route_cache` for existing segment
2. If miss → fetch from ORS directions API → cache result
3. If station has no coordinates → geocode via ORS → save to station
4. Accumulate total distance, duration, and polyline points

---

## CSV Import

### Format
```
StationName;DD.MM.YYYY
StationName,DD.MM.YYYY
```
Both `;` and `,` delimiters supported. UTF-8 encoding.

### Processing
`SettingsScreen` opens the content `InputStream` → `SettingsViewModel.importStationsFromCsv(stream)` → `ImportStationsUseCase` (IO) → `ParseStationsCsvUseCase` (pure). No `Context` in the ViewModel.

1. **Stream-parse** line-by-line (`bufferedReader().useLines`, no full-file load → no OOM on a hostile file).
2. **Clean station names** via `StationNameCleaner` (pure, unit-tested):
   - Extract/preserve a parenthetical suffix (e.g., "(zastávka)")
   - Strip `žst.`, `os.n.`
   - Cut at the first `" - "` / `"- "`
   - Re-attach parentheses; collapse whitespace
3. **Deduplicate** by cleaned name (`distinctBy`).
4. **Save** each station (status = PENDING) and **seed coordinates** from bundled `stations.json`.
5. **Re-apply manual coordinate overrides** (`applyCoordinateOverrides()`): any station whose name matches a row in `station_coord_overrides` gets its hand-corrected lat/lon back. Overrides **win** over both the CSV and `stations.json` defaults, so a re-import after "clear storage" restores dragged-in positions instead of resetting them. Matching is by **station name** (import ids are regenerated each time).

`ParseStationsCsvUseCase` and `StationNameCleaner` have thorough unit tests; bad dates are kept as `null` (not dropped).

### stations.json
Bundled asset file mapping station names to `{"lat": Double, "lon": Double}`. Used as a fast local geocoding fallback before hitting the ORS geocode API.
