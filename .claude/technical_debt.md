# Technical Debt & Issues

## Resolved

1. **Hardcoded API keys** — keys in `local.properties` → `BuildConfig` (`ORS_API_KEY`, `MAPY_CZ_API_KEY`).
2. **`fallbackToDestructiveMigration()`** — now **debug-only** in `DatabaseModule`; release builds enforce migrations. The previously-missing `shortcuts.isRoundTrip` column is added by `MIGRATION_7_8` and covered by `MigrationTest`. `exportSchema = true` (schemas under `app/schemas/`).
3. **God ViewModel + DAO injection** — `StationListViewModel` split into 4 ViewModels backed by repositories/use cases; no DAOs in the UI layer.
4. **domain↔ui↔data dependency cycle** — `RouteRepository` no longer references `RouteCacheEntity`/`RouteListItem`/`GeoPoint`; it speaks pure domain types. `domain/` has zero `data`/`ui`/`osmdroid` imports.
5. **Legacy `ui/zone/` duplicate screens** — removed; single Camera/Gallery inspection path.
6. **No tests** — ~39 unit tests: pure logic, mappers, Robolectric Room (real SQLite), migration, and `RouteViewModel` (mockk + coroutines-test). Test deps + a `@IoDispatcher` injection make the route logic deterministically testable.
7. **Security: HTTP body logging** — gated behind `BuildConfig.DEBUG` (was logging API keys + payloads in release).
8. **Security: backups** — `allowBackup="false"` + exclude-all `data_extraction_rules.xml`.
9. **Storage leak** — `clearAllData()` deletes photo files (`FileStorageManager.clearAllPhotoFiles()`), not just DB rows.
10. **Bitmap leaks / OOM** — `CameraXController` recycles the pre-rotation bitmap; `ZoneInspectionViewModel` recycles after encode.
11. **CSV OOM** — `ImportStationsUseCase` streams via `useLines` instead of `readBytes()`.
12. **Battery: map/camera** — collapsed mini-map no longer renders; map overlays rebuild only on data change (cached marker bitmap); 7-day tile cache; `stopCamera()` releases the camera.

## Remaining (smaller / optional)

- **ORS `unassigned` jobs ignored** — if the optimizer can't route to a point, it is silently dropped from the order. Needs DTO typing + a UX decision (warn the user). `data/repository/RouteRepositoryImpl`.
- **No `@Index` on hot columns** — `pois.inspectionDate/orderIndex`, `stations.inspectionDate/orderIndex`. Perf only; requires a version bump (8→9) + `MIGRATION_8_9 CREATE INDEX` + a migration test (now feasible).
- **Route logic still in `RouteViewModel`** — `rebuildHomePointsAndIndices` / `insertPoiAtCorrectOrderIndex` / `calculateDailyRoute` could move to use cases. The VM is already unit-testable, so this is optional polish.
- **`MapScreenContent` size** — `SearchResultsList`/`ShortcutsRow` were extracted; the inline `ShortcutEditSheet` (entangled focus/sheet state) remains.
- **Apache POI dependency** — `poi-ooxml` is declared but unused; export uses zip4j. Could be dropped (~APK size).
- **Single-module app** — all in `:app`; no enforced module boundaries.
- **Compose UI / instrumented tests** — none yet (need an emulator/device); migration tests beyond 7→8 lack historical exported schemas, so test new migrations going forward.

## Build gotcha (operational, not code)
Behind Avast "Web Shield" HTTPS scanning, Gradle dependency/Robolectric downloads fail (`PKIX`). Fixed machine-locally via a custom truststore referenced from `~/.gradle/gradle.properties` (`systemProp.javax.net.ssl.trustStore`) and the `testTrustStore` project property for forked test JVMs (wired generically in `build.gradle.kts`). See `Claude.md` → Build & test.
