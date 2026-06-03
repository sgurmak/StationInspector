# StationInspector — Global project context

## Briefly about the project
**StationInspector** (`com.example.stationinspector`, app name "FleetWay") is a native Android app (Kotlin, minSdk 26, targetSdk 35) for inspecting Czech railway stations. Inspectors import a daily schedule of stations from CSV, follow an optimized driving route on a map, photograph each station zone (entrance, waiting area, restroom), categorize photos as normal or defect, and export a structured ZIP archive for client delivery.

## Technology stack (key)
- **Language**: Kotlin 2.0.21, Jetpack Compose + Material 3
- **DI**: Dagger Hilt 2.52 (4 modules: `AppModule`, `DatabaseModule`, `NetworkModule`, `DispatchersModule`)
- **Database**: Room 2.6.1 (5 tables, 8 migrations, KSP, `exportSchema = true` → `app/schemas/`)
- **Network**: Retrofit 2.9 + OkHttp 4.12 — two APIs: OpenRouteService (routing/optimization/geocoding) and Mapy.cz (location search + map tiles)
- **Camera**: CameraX 1.4.0, Preview + ImageCapture only (no ImageAnalysis/face detection), custom JPEG compression pipeline
- **Maps**: osmdroid 6.1.18 with Mapy.cz tile source (Czech Republic bounds), 7-day tile cache
- **Background**: WorkManager 2.10 (foreground ZIP export worker)
- **Testing**: JUnit4 + Robolectric (in-memory Room on real SQLite) + mockk + kotlinx-coroutines-test (~39 unit tests)

## Architecture
Clean Architecture with **package-by-layer** organization. Single `:app` module.

```
UI (Compose) → ViewModel (StateFlow) → UseCase / Repository (domain interfaces) → DAO / API (Room + Retrofit)
```

- **Dependency rule holds**: `domain/` depends on nothing else (no `data`, `ui`, or `osmdroid` imports); `data/` does not depend on `ui/`. Repositories return **domain models**, never Room entities or UI models.
- **ViewModels** (the old `StationListViewModel` god object was split):
  - `RouteViewModel` — date, route items/info, optimize, edit, reorder, Home/round-trip, map-expand, scroll. Shared across the Work and Map tabs (same `NavBackStackEntry` scope).
  - `SearchViewModel` — Mapy.cz search box.
  - `ShortcutsViewModel` — shortcut chips CRUD; seeds Home/Work.
  - `SettingsViewModel` — CSV import + clear-all; loading flag + snackbars.
  - `ZoneInspectionViewModel` — camera + gallery. `ExportViewModel` — export screen.
- **UseCases** (`domain/usecase/`): `StationNameCleaner` (pure), `ParseStationsCsvUseCase` (pure, streaming), `ImportStationsUseCase` (IO).
- **TransactionRunner** (`domain/repository/`, impl `data/repository/RoomTransactionRunner`) abstracts Room transactions so ViewModels stay Room-free.
- **`@IoDispatcher`** (`di/Dispatchers.kt`) injects `Dispatchers.IO` so coroutines are deterministic under test.

## Key limitations and issues (important for agents)
1. **Hardcoded API keys** — [RESOLVED] In `local.properties` → `BuildConfig`.
2. **`fallbackToDestructiveMigration()`** — [RESOLVED for release] Now **debug-only** (`DatabaseModule`). Release builds enforce real migrations (no silent data loss). `MIGRATION_7_8` restores the previously-missing `shortcuts.isRoundTrip` column; covered by `MigrationTest`.
3. **God ViewModel / DAO injection** — [RESOLVED] Split into 4 ViewModels backed by repositories + use cases. No DAOs in the UI layer.
4. **Test coverage** — [PARTIAL→GOOD] ~39 unit tests: pure logic (name cleaner, CSV, route reconstruction), mappers, **Robolectric Room** (real SQLite), **migration**, and **RouteViewModel** (mockk + coroutines-test). Compose UI / instrumented tests still absent.
5. **Storage leak** — [RESOLVED] `clearAllData()` now deletes photo files, not just DB rows (`FileStorageManager.clearAllPhotoFiles()`).
6. **Security** — [RESOLVED] HTTP body logging gated behind `BuildConfig.DEBUG`; `allowBackup="false"` + exclude-all `data_extraction_rules.xml`.

## Build & test (operational notes)
- Use the Android Studio JBR: `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`, then `gradlew assembleDebug` / `gradlew testDebugUnitTest`.
- **TLS-intercepting AV (Avast "Web Shield")** breaks Gradle dependency downloads (`PKIX`/`certificate_unknown`). The JBR truststore lacks the Avast root. Machine-local fix (kept in `~/.gradle/gradle.properties`, **not** in the repo): a custom truststore = JBR `cacerts` + the exported Avast root, referenced via `systemProp.javax.net.ssl.trustStore`; forked test JVMs get it via the `testTrustStore` project property (wired generically in `app/build.gradle.kts`). Alternative: disable Avast HTTPS scanning.

## Files with detailed documentation (open if necessary)

| File | Contents |
|---|---|
| [`.claude/identity.md`](.claude/identity.md) | Project identity, package, SDK versions |
| [`.claude/tech_stack.md`](.claude/tech_stack.md) | Full library list with versions |
| [`.claude/architecture.md`](.claude/architecture.md) | Package structure, layers, DI graph, use cases |
| [`.claude/database.md`](.claude/database.md) | Tables, columns, migrations, schema export |
| [`.claude/api_network.md`](.claude/api_network.md) | API endpoints, DTOs, auth, map config |
| [`.claude/navigation.md`](.claude/navigation.md) | Nav graph, routes, tab navigation |
| [`.claude/screens.md`](.claude/screens.md) | Screens with roles and components |
| [`.claude/viewmodels.md`](.claude/viewmodels.md) | The 4 route ViewModels + inspection/export |
| [`.claude/business_logic.md`](.claude/business_logic.md) | Photo rules, export pipeline, route optimization, CSV import |
| [`.claude/design_system.md`](.claude/design_system.md) | Colors, typography, shapes, interaction patterns |
| [`.claude/technical_debt.md`](.claude/technical_debt.md) | Remaining issues (most historical ones resolved) |

---

> When performing a new task: read this file (`Claude.md`) and 1-2 files from `.claude/` that are relevant to the task. Verify against the actual source — the on-disk code is the source of truth (and beware a stale `Glob` index after large refactors).
