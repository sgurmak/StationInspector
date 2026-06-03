# Database Schema

**Engine**: Room 2.6.1  
**DB Name**: `station_inspector_db`  
**Current Version**: 8  
**Export Schema**: enabled (`exportSchema = true` → `app/schemas/`, the v8 JSON is committed)  

## Tables

### `stations` → `StationEntity`
| Column | Type | Notes |
|---|---|---|
| `id` | Long | PK, auto-generate |
| `name` | String | |
| `address` | String | |
| `latitude` | Double | Default 0.0 |
| `longitude` | Double | Default 0.0 |
| `inspectionDate` | LocalDate? | Stored as ISO string via TypeConverter |
| `status` | StationStatus | Enum: PENDING, IN_PROGRESS, COMPLETED |
| `orderIndex` | Int | Default 0, used for route ordering |

### `photos` → `PhotoEntity`
| Column | Type | Notes |
|---|---|---|
| `id` | Long | PK, auto-generate |
| `stationId` | Long | FK → stations.id, CASCADE delete |
| `zone` | PhotoZone | Enum: ENTRANCE, PLATFORM, TICKETING, RESTROOM, OTHER |
| `type` | PhotoType | Enum: PANORAMIC, DETAIL, DOCUMENT, CLIENT_REPORT, INTERNAL_DEFECT |
| `localPath` | String | Absolute file path |
| `timestamp` | Long | Capture time in millis |
| `description` | String | |
| `exported` | Boolean | |
| `assignedDate` | String | Date string for export filtering |

**Index**: `stationId`

### `route_cache` → `RouteCacheEntity`
| Column | Type | Notes |
|---|---|---|
| `id` | String | PK, format: "lat1,lon1-lat2,lon2" |
| `originLat` | Double | |
| `originLon` | Double | |
| `destLat` | Double | |
| `destLon` | Double | |
| `distanceMeters` | Double | |
| `durationSeconds` | Long | |
| `geometry` | String | Encoded polyline |

### `pois` → `PoiEntity`
| Column | Type | Notes |
|---|---|---|
| `id` | String | PK, UUID |
| `name` | String | |
| `city` | String? | |
| `address` | String? | |
| `region` | String? | |
| `latitude` | Double | |
| `longitude` | Double | |
| `inspectionDate` | LocalDate | Links POI to a specific work day |
| `orderIndex` | Int | Route ordering |

### `shortcuts` → `ShortcutEntity`
| Column | Type | Notes |
|---|---|---|
| `id` | String | PK ("1" = Home, "2" = Work, rest = UUID) |
| `label` | String | |
| `customName` | String? | User-set display name (max 10 chars) |
| `poiItemJson` | String? | Serialized `PoiLocation` JSON (Gson; encapsulated in `ShortcutMapper`) |
| `isNew` | Boolean | True if no address assigned |
| `isRoundTrip` | Boolean | Default false, only meaningful for Home |

## Result Classes (not tables)

- **`StationWithSplitCounts`** — `@Embedded StationEntity` + `regularCount: Int` (CLIENT_REPORT) + `issueCount: Int` (INTERNAL_DEFECT). Mapped to the domain `StationWithSplitCountsDomain`.

(The unused `StationWithPhotoCount` projection + its query were removed.)

## Relationships

- `photos.stationId` → `stations.id` (FK with CASCADE delete)
- `pois` linked to work days via `inspectionDate` column
- `shortcuts` store serialized `PoiItem` JSON for quick-add actions on the Map screen

## TypeConverters

`Converters` class handles:
- `LocalDate` ↔ ISO string (`DateTimeFormatter.ISO_LOCAL_DATE`)
- `StationStatus` ↔ enum name string
- `PhotoZone` ↔ enum name string
- `PhotoType` ↔ enum name string

## Migrations

| Migration | Changes |
|---|---|
| 1→2 | Add `assignedDate` column to `photos` |
| 2→3 | Add `latitude`, `longitude` to `stations` |
| 3→4 | Create `route_cache` and `pois` tables |
| 4→5 | Add `geometry` column to `route_cache` |
| 5→6 | Create `shortcuts` table |
| 6→7 | Empty migration (version bump) |
| 7→8 | Add `orderIndex` to `stations`; **add `isRoundTrip` to `shortcuts`** (this column was declared on the entity but missing from the migration chain — fixed); drop & recreate `pois` with the new string-keyed schema |

`MIGRATION_7_8` is covered by `MigrationTest` (Robolectric, real SQLite): it reconstructs the v7 state and asserts the new columns plus data preservation. Historical schemas (v1–v7) were never exported, so test **new** migrations going forward with `MigrationTestHelper`.

**Safety net**: `fallbackToDestructiveMigration()` is applied **only in debug builds** (`DatabaseModule`). Release builds enforce the real migrations — a missing path surfaces as an error, never silent data loss.

## DAOs

- **StationDao**: CRUD + `getStationsWithSplitCounts` (JOIN), `getAllStationsSync`, `updateStationOrder`/`updateStationOrders` (transaction)
- **PhotoDao**: CRUD + filter by station, by station+zone, unexported, `deleteAllPhotos`
- **RouteCacheDao**: `insertRouteCache` + `getRouteCacheById`
- **PoiDao**: insert + `getPoisForDate`(Flow)/`getPoisForDateSync` + `updatePoiOrders` (transaction) + `deletePoi`/`deletePoisByNameAndDate`
- **ShortcutDao**: CRUD + `clearOldBlankShortcuts` + `getShortcutCount`

DAOs are not injected into the UI layer; ViewModels go through repositories
(`StationRepository` exposes the station-ordering + `getStationsForDateSync`
API; `PoiRepository`, `ShortcutRepository`, `PreferencesRepository` wrap the
rest). `RoomTransactionRunner` provides `withTransaction` to the VM layer.
