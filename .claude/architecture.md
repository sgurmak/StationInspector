# Architecture

## Pattern

**Clean Architecture (3-layer)** with **package-by-layer** organisation.

**Data flow**: `Screen (Composable)` → `ViewModel (Hilt)` → `Repository (interface)` → `DAO / ApiService` → `Room DB / Network`

All reactive state flows through `kotlinx.coroutines.flow.StateFlow` exposed from ViewModels. Room DAOs return `Flow<T>` for live queries.

## Package Structure

```
com.example.stationinspector/
├── di/                      ← Hilt modules (3 files)
├── domain/
│   ├── model/               ← Domain entities (Station, Photo, enums)
│   └── repository/          ← Repository interfaces
├── data/
│   ├── local/
│   │   ├── dao/             ← Room DAOs (5)
│   │   ├── entity/          ← Room entities (7)
│   │   └── converter/       ← Room TypeConverters
│   ├── remote/
│   │   ├── OrsApiService    ← Retrofit interface
│   │   └── dto/             ← Network DTOs
│   ├── repository/          ← Repository implementations (3)
│   ├── mapper/              ← Entity↔Domain mappers
│   └── storage/             ← FileStorageManager (photo files)
├── camera/                  ← CameraXController, ImageCompressor
├── worker/                  ← ExportZipWorker
├── utils/                   ← PolylineUtils
└── ui/
    ├── navigation/          ← NavGraph
    ├── screens/             ← Main screens (StationList, Map, Settings)
    ├── inspection/          ← Camera/Gallery screens
    ├── export/              ← Export screen + ViewModel
    ├── zone/                ← Zone list/gallery (older UI path)
    ├── components/          ← MapWidget
    └── theme/               ← Color, Type, Theme
```

## DI Graph (Hilt)

All bindings are `@Singleton` scoped under `SingletonComponent`.

```
SingletonComponent
├── AppModule
│   ├── WorkManager
│   ├── MapyCzApi (Retrofit — separate instance, base: api.mapy.cz)
│   └── MapyCzRepository (→ MapyCzRepositoryImpl)
│
├── DatabaseModule
│   ├── AppDatabase (Room)
│   ├── StationDao
│   ├── PhotoDao
│   ├── RouteCacheDao
│   ├── PoiDao
│   ├── ShortcutDao
│   ├── StationRepository (→ StationRepositoryImpl)
│   ├── RouteRepository (→ RouteRepositoryImpl)
│   └── DataStore<Preferences>
│
└── NetworkModule
    ├── OkHttpClient (ORS auth interceptor + logging)
    ├── Retrofit (base: api.openrouteservice.org)
    └── OrsApiService
```

Two separate Retrofit instances exist:
1. **ORS** (NetworkModule) — directions, optimization, geocoding
2. **Mapy.cz** (AppModule) — location search for POIs

Each has its own OkHttpClient with different auth strategies (header vs query param).

## Key Architectural Notes

- Single-module app (`:app`). No feature modules.
- ViewModels are `@HiltViewModel` with constructor injection.
- Workers use `@HiltWorker` with `@AssistedInject`.
- Application class implements `Configuration.Provider` for custom WorkManager initialization with `HiltWorkerFactory`.
- `StationListViewModel` is shared across Work, Map, and Settings tabs via `hiltViewModel()` scoped to `MainAppScreen`.
