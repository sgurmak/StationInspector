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
3. **Zone name mapping** (English enum → Czech folder name):
   - ENTRANCE / STATION → Nádraží
   - PLATFORM / WAITING_ROOM → Čekárna
   - RESTROOM / WC → WC
4. **Create ZIP** using Zip4j
5. **Cleanup**: Delete previous export sessions, delete staging directory
6. **Return**: FileProvider URI for the ZIP file
7. **Share**: ExportScreen opens Android share intent with the ZIP

### ZIP Naming
Format: `KPI_{dd.MM}.zip` (e.g., `KPI_15.04.zip`)

### Foreground Notification
- Channel: `export_channel`
- Title: "Генерація звіту"
- Text: "Створення ZIP-архіву..."
- Service type: `dataSync`

---

## Route Optimization

### Flow (StationListViewModel.optimizeRoute → RouteRepositoryImpl)

1. **Filter**: Only active (non-hidden) items with valid coordinates (lat/lon ≠ 0)
2. **Detect round-trip**: If first and last items share coordinates
3. **Build VROOM request**:
   - Vehicle: start = first item, end = last item (if round-trip)
   - Jobs: all intermediate items
4. **Send to ORS** `POST /optimization`
5. **Parse response**: Extract step order from route steps (type = "job")
6. **Reconstruct list**: start + sorted intermediates + end
7. **Persist new order**: Update `orderIndex` for all stations and POIs in DB
8. **Fetch geometry**: Send reordered coordinates to `POST /directions/driving-car`
9. **Cache**: Store full route geometry in `route_cache`

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

### Processing (StationListViewModel.importStationsFromCsv)

1. **Parse** each line into name + date
2. **Clean station names** (`cleanStationName`):
   - Extract and preserve parenthetical suffixes (e.g., "(v Čechách)")
   - Remove abbreviations: `žst.`, `os.n.`
   - Cut at `" - "` or `"- "` (removes sub-station suffixes)
   - Re-attach parentheses
   - Collapse multiple spaces
3. **Deduplicate** by cleaned name
4. **Save** each station with status = PENDING
5. **Seed coordinates** from bundled `stations.json` (220+ Czech stations with pre-mapped lat/lon)

### stations.json
Bundled asset file mapping station names to `{"lat": Double, "lon": Double}`. Used as a fast local geocoding fallback before hitting the ORS geocode API.
