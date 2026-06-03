# 03 — Network / API Agent

**Місія**: надійні мережеві виклики ORS і Mapy.cz, чіткий error-handling, ключі через `BuildConfig`.

## Контекст
- Деталі: [.claude/api_network.md](../.claude/api_network.md).
- Дві Retrofit-інстанси з різною авторизацією:
  - **ORS** (`api.openrouteservice.org`) — `Authorization` header, інжектиться через `OkHttpClient` interceptor у [NetworkModule.kt:25-32](../app/src/main/java/com/example/stationinspector/di/NetworkModule.kt).
  - **Mapy.cz** (`api.mapy.cz`) — `apikey` query param, налаштовується в [AppModule.kt](../app/src/main/java/com/example/stationinspector/di/AppModule.kt).

## Файли
```
data/remote/
├── OrsApiService.kt          ← Retrofit interface (directions, optimization, geocoding)
└── dto/OrsDtos.kt            ← DTO для ORS (містить поле `unassigned` у VROOM-відповіді)
data/repository/
├── RouteRepositoryImpl.kt    ← ORS-операції (кеш у RouteCacheDao); повертає domain-моделі
├── StationRepositoryImpl.kt  ← geocoding (fetchAndSaveCoordinates → GeoCoordinate?)
└── MapyCzRepository.kt       ← Mapy.cz search → List<PoiLocation>
di/NetworkModule.kt
di/AppModule.kt
```

## ВИРІШЕНО
1. **Ключі → `BuildConfig`** — `BuildConfig.ORS_API_KEY` у header, `BuildConfig.MAPY_CZ_API_KEY` у query. Жодних літералів (див. [01-security.md](01-security.md)).
2. **Logging DEBUG-gated** — `HttpLoggingInterceptor.Level.BODY` лише при `BuildConfig.DEBUG`, інакше `NONE` ([NetworkModule.kt:34-42](../app/src/main/java/com/example/stationinspector/di/NetworkModule.kt)). Ключ і координати не світяться в release-логах.
3. **Таймаути** — ORS-`OkHttpClient` має `connectTimeout`/`readTimeout` 45s ([NetworkModule.kt:47-48](../app/src/main/java/com/example/stationinspector/di/NetworkModule.kt)); Mapy.cz-клієнт також отримав 45s-таймаути (в [AppModule.kt](../app/src/main/java/com/example/stationinspector/di/AppModule.kt)). VROOM-оптимізація на великих наборах не падає по дефолтному таймауту.
4. **Domain-чисті типи** — `MapyCzRepository.searchLocation` повертає `List<PoiLocation>` (domain), `RouteRepository` оперує `RouteSegment`/`RouteWaypoint`/`OptimizedRoute`/`GeoCoordinate` — без `RouteCacheEntity`/`RouteListItem`/`osmdroid.GeoPoint` у сигнатурах (розрив циклу залежностей, див. [07-architecture-refactor.md](07-architecture-refactor.md)).

## Кейси, на які треба зважати
- **Кеш маршрутів** — у Room (`RouteCacheDao`); ключ — впорядкований набір координат + профіль.
- **VROOM optimization** — викликається з UI через `RouteViewModel` (раніше — монолітний `StationListViewModel.optimizeRoute()`, видалений).
- **Geocoding silent failures** — `fetchAndSaveCoordinates` повертає `null` без явного сигналу UI.

## Залишок
- **ORS `unassigned` jobs ігноруються** — у VROOM-відповіді ([OrsDtos.kt](../app/src/main/java/com/example/stationinspector/data/remote/dto/OrsDtos.kt)) поле `unassigned` не обробляється: маршрут може тихо «загубити» точку, яку оптимізатор не зміг призначити. Варто сигналізувати в UI.
- **Result-обгортка / retry-backoff** — `optimizeAndFetchGeometry` уже повертає `Result<OptimizedRoute>`, але geocoding-шлях ще nullable; transient-retry не реалізовано.
- **Тести** — `RouteRepositoryImplTest` покриває кеш/оптимізацію (mockk). `MockWebServer` для end-to-end ORS/Mapy ще не додано.

## Готово, коли
- Ключі не літерали (виконано).
- Помилки доходять до UI (частково: optimization — так; geocoding/`unassigned` — ще ні).
- Logging release-safe (виконано).
