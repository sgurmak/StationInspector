# 03 — Network / API Agent

**Місія**: надійні мережеві виклики ORS і Mapy.cz, чіткий error-handling, ключі через `BuildConfig`.

## Контекст
- Деталі: [.claude/api_network.md](../.claude/api_network.md).
- Дві Retrofit-інстанси з різною авторизацією:
  - **ORS** (`api.openrouteservice.org`) — Authorization header, інжектиться через `OkHttpClient` interceptor у [NetworkModule.kt](../app/src/main/java/com/example/stationinspector/di/NetworkModule.kt).
  - **Mapy.cz** (`api.mapy.cz`) — `apikey` query param, налаштовується в [AppModule.kt](../app/src/main/java/com/example/stationinspector/di/AppModule.kt).

## Файли
```
data/remote/
├── OrsApiService.kt          ← Retrofit interface (directions, optimization, geocoding)
└── dto/OrsDtos.kt            ← DTO для ORS
data/repository/
├── RouteRepositoryImpl.kt    ← ORS-операції (з кешем у RouteCacheDao)
├── StationRepositoryImpl.kt  ← geocoding (fetchAndSaveCoordinates)
└── MapyCzRepository.kt       ← Mapy.cz search
di/NetworkModule.kt
di/AppModule.kt
```

## Кейси, на які треба зважати
- **Кеш маршрутів** — у Room (`RouteCacheDao`); ключ — впорядкований набір координат + профіль.
- **VROOM optimization** — викликається з `MapScreen` через `StationListViewModel.optimizeRoute()`.
- **Geocoding silent failures** — `fetchAndSaveCoordinates` повертає null без сигналу UI (борг #8).

## Що покращити
1. **Ключі → BuildConfig** (див. [01-security.md](01-security.md)).
2. **Sealed `Result<T, NetworkError>`** замість nullable + `e.printStackTrace()`.
3. **Retry/backoff** для transient помилок (OkHttp Interceptor).
4. **HttpLoggingInterceptor.Level** — `BODY` лише в debug builds.
5. **Тести** — `MockWebServer` для критичних потоків (optimization, geocoding).

## Готово, коли
- Жодного `e.printStackTrace()` без поверх обробки.
- Ключі не літерали.
- Помилки доходять до UI (snackbar / state).
