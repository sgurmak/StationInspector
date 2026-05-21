# Technical Debt & Issues

## Critical

### 1. Hardcoded API Keys
- **Where**: `NetworkModule.kt:20` (ORS API key as Base64 string), `MapWidget.kt:26` (Mapy.cz tile API key)
- **Risk**: Keys are committed to source control. Anyone with repo access can extract and abuse them. ORS key is especially sensitive (paid API with usage limits).
- **Proper approach**: Move all keys to `local.properties` → `BuildConfig` (as already done for `MAPY_CZ_API_KEY` in `AppModule`).

### 2. fallbackToDestructiveMigration() Enabled
- **Where**: `DatabaseModule.kt:41`
- **Risk**: If a migration path is missing in a production release, Room will silently **delete all user data** (stations, photos, routes, shortcuts). Combined with 8 existing migrations, this is a data-loss time bomb.
- **Proper approach**: Remove `fallbackToDestructiveMigration()` before any production release. Ensure all migration paths are covered and tested.

---

## Medium

### 3. StationListViewModel Violates Clean Architecture
- **Where**: `StationListViewModel.kt` constructor
- **Issue**: Directly injects `ShortcutDao`, `PoiDao`, `StationDao` (data layer) alongside domain repository interfaces. This bypasses the repository abstraction and couples the ViewModel to Room implementation details.
- **Impact**: Makes testing harder (need Room DB instead of mock repositories), violates the dependency rule (domain/UI should not depend on data layer).

### 4. Legacy Duplicate Screens
- **Where**: `ui/zone/ZoneListScreen.kt`, `ui/zone/ZoneListViewModel.kt`, `ui/inspection/ZoneGalleryScreen.kt`
- **Issue**: Older screens with Ukrainian labels (Вокзал, Зона очікування, Косяки) coexist with newer Camera/Gallery flow using Czech labels (Nádraží, Čekárna, WC). The legacy screens are still in the codebase and potentially reachable.
- **Impact**: Maintenance burden, inconsistent UX language, confusion for developers.

### 5. No Test Coverage
- **Where**: Only boilerplate `ExampleUnitTest.kt` and `ExampleInstrumentedTest.kt` exist
- **Impact**: No regression protection for business logic (CSV import, route optimization, export pipeline, photo compression). Any refactoring is high-risk.

---

## Low

### 6. Apache POI Unused Dependency
- **Where**: `libs.versions.toml` and `app/build.gradle.kts`
- **Issue**: `poi-ooxml 5.2.3` is declared but never imported in any source file. It's a large dependency (~10MB+) adding to APK size for no benefit.

### 7. Single-Module Architecture
- **Where**: Entire project lives in `:app`
- **Issue**: No separation between feature, domain, or data modules. Build times will degrade as the project grows. No enforced layer boundaries.

### 8. Inconsistent Error Handling
- **Where**: Various — `e.printStackTrace()` throughout repositories and ViewModels
- **Issue**: Errors are printed to logcat but not surfaced to the user or handled gracefully in many cases (e.g., `fetchAndSaveCoordinates` silently returns null, CSV import swallows exceptions).

### 9. Schema Export Disabled
- **Where**: `AppDatabase.kt:22` — `exportSchema = false`
- **Issue**: Room schema JSON files are not generated, making it impossible to verify migration correctness via Room's testing utilities.
