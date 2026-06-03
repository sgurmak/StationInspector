# Architecture

## Pattern

**Clean Architecture** with **package-by-layer** organisation.

**Data flow**: `Screen (Composable)` → `ViewModel (Hilt)` → `UseCase / Repository (domain interface)` → `DAO / ApiService` → `Room DB / Network`

- Reactive state flows through `StateFlow` exposed by ViewModels; Room DAOs return `Flow<T>`.
- **Dependency rule is enforced**: `domain/` imports nothing from `data/`, `ui/`, or `osmdroid`. `data/` imports nothing from `ui/`. Repositories return **domain models**; mappers convert at the boundary.

## Package Structure

```
com.example.stationinspector/
├── di/                      ← Hilt modules: AppModule, DatabaseModule, NetworkModule, Dispatchers(Module + @IoDispatcher)
├── domain/
│   ├── model/               ← Station, Photo, Poi, PoiLocation, Shortcut, Route (GeoCoordinate/RouteSegment/RouteWaypoint/OptimizedRoute), enums
│   ├── repository/          ← Repository interfaces + TransactionRunner
│   └── usecase/             ← StationNameCleaner, ParseStationsCsvUseCase, ImportStationsUseCase
├── data/
│   ├── local/
│   │   ├── dao/             ← Room DAOs (5: Station, Photo, RouteCache, Poi, Shortcut)
│   │   ├── entity/          ← Room entities + projection rows (StationWithSplitCounts)
│   │   └── converter/       ← Room TypeConverters
│   ├── remote/              ← OrsApiService + dto/
│   ├── repository/          ← Impls: Station, Route, Poi, Shortcut, Preferences, MapyCz, RoomTransactionRunner
│   ├── mapper/              ← Entity↔Domain mappers (Station, Poi, Shortcut; Shortcut owns Gson)
│   └── storage/             ← FileStorageManager (photo files in filesDir)
├── camera/                  ← CameraXController (Preview + ImageCapture), ImageCompressor
├── worker/                  ← ExportZipWorker (@HiltWorker)
├── utils/                   ← PolylineUtils, MapNavigation (Context.openInMaps)
└── ui/
    ├── navigation/          ← NavGraph, SharedUiModels (MainTab, Material NavigationBar)
    ├── screens/             ← MainAppScreen, StationListScreen, MapScreen, SettingsScreen, SplashScreen,
    │                          RouteModels.kt (UI models), and the 4 ViewModels (Route/Search/Shortcuts/Settings)
    ├── inspection/          ← CameraScreen, GalleryScreen, ZoneInspectionViewModel
    ├── export/              ← ExportScreen + ExportViewModel
    ├── components/          ← MapWidget (osmdroid)
    └── theme/               ← Color (semantic tokens), Type, Theme, ModifierUtils
```

## Layers in detail

- **ViewModels** (`ui/screens`): the former `StationListViewModel` god object is split into `RouteViewModel`, `SearchViewModel`, `ShortcutsViewModel`, `SettingsViewModel`. Each resolves via `hiltViewModel()`; because the Work/Map/Settings/Export content share one `NavBackStackEntry` (inside `MainAppScreen`), `RouteViewModel` is the **same instance** across the Work and Map tabs, preserving route/date state.
- **UseCases** (`domain/usecase`): pure where possible. `ParseStationsCsvUseCase` streams a `Sequence<String>`; `ImportStationsUseCase` reads the `InputStream` lazily (no full-file load).
- **Repositories** return domain models. `RouteRepository` speaks `RouteSegment` / `RouteWaypoint` / `OptimizedRoute` / `GeoCoordinate` (no Room/UI/osmdroid types). `MapyCzRepository.searchLocation` returns `List<PoiLocation>`.
- **TransactionRunner** abstracts `db.withTransaction {}` so `RouteViewModel` composes multi-step writes without depending on `AppDatabase`.

## DI Graph (Hilt)

All bindings `@Singleton` under `SingletonComponent`.

```
SingletonComponent
├── AppModule          → WorkManager, MapyCzApi (Retrofit api.mapy.cz), MapyCzRepository
├── NetworkModule      → OkHttpClient (ORS auth + DEBUG-gated logging), Retrofit (api.openrouteservice.org), OrsApiService
├── DispatchersModule  → @IoDispatcher CoroutineDispatcher (Dispatchers.IO)
└── DatabaseModule     → AppDatabase, the 5 DAOs, DataStore<Preferences>, Gson,
                         StationRepository, RouteRepository, PoiRepository,
                         ShortcutRepository, PreferencesRepository, TransactionRunner
```

Use cases use `@Inject constructor` (no explicit `@Provides`). Two Retrofit instances: **ORS** (header auth) and **Mapy.cz** (apikey query param); both clients have 45s timeouts and DEBUG-only body logging.

## Key Architectural Notes

- Single-module app (`:app`). No feature modules.
- ViewModels are `@HiltViewModel`; Worker is `@HiltWorker` + `@AssistedInject`; Application is a `Configuration.Provider` for the `HiltWorkerFactory`.
- `RouteViewModel` injects `@IoDispatcher` (not hardcoded `Dispatchers.IO`) and `TransactionRunner` (not `AppDatabase`) — both for clean boundaries and deterministic tests.
- The legacy `ui/zone/` screens were removed; the only inspection path is Camera/Gallery (Czech zone labels Nádraží/Čekárna/WC).
