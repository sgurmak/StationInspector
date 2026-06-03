# 00 — Project Overview

**StationInspector** (`com.example.stationinspector`) — нативний Android-додаток (Kotlin) для інспекції чеських залізничних станцій.

## Користувацький сценарій
1. Інспектор **імпортує CSV** з графіком станцій на день ([SettingsScreen](../app/src/main/java/com/example/stationinspector/ui/screens/SettingsScreen.kt)).
2. Дивиться **оптимізований маршрут** на мапі (VROOM через ORS) — [MapScreen](../app/src/main/java/com/example/stationinspector/ui/screens/MapScreen.kt).
3. На станції **фотографує 3 зони** (Nádraží, Čekárna, WC) — звичайне фото або «defect» — [CameraScreen](../app/src/main/java/com/example/stationinspector/ui/inspection/CameraScreen.kt).
4. Переглядає й підтверджує — [GalleryScreen](../app/src/main/java/com/example/stationinspector/ui/inspection/GalleryScreen.kt).
5. **Експортує ZIP** з усіма фото за структурою клієнта — [ExportScreen](../app/src/main/java/com/example/stationinspector/ui/export/ExportScreen.kt) → [ExportZipWorker](../app/src/main/java/com/example/stationinspector/worker/ExportZipWorker.kt).

## Стек (коротко)
| Шар | Технологія |
|---|---|
| Мова / SDK | Kotlin 2.0, minSdk 26, targetSdk 35 |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt 2.52 (4 модулі) |
| БД | Room 2.6.1 (5 таблиць, 8 міграцій, `exportSchema=true`) |
| Мережа | Retrofit 2.9 + OkHttp 4.12 (ORS + Mapy.cz) |
| Камера | CameraX 1.4 (Preview + ImageCapture) |
| Мапи | osmdroid 6.1.18 + Mapy.cz tiles (7-денний кеш) |
| Фон | WorkManager 2.10 |
| Тести | JUnit + Robolectric + mockk + coroutines-test |

Деталі: [.claude/tech_stack.md](../.claude/tech_stack.md).

## Архітектура (одним абзацом)
Clean Architecture, package-by-layer, один модуль `:app`. **Правило залежностей дотримано**: `domain/` не залежить від `data`/`ui`/`osmdroid`; репозиторії повертають domain-моделі.
`Compose → @HiltViewModel (StateFlow) → UseCase / Repository (interface) → DAO / ApiService`.
DI — **чотири** Hilt-модулі: `AppModule`, `DatabaseModule`, `NetworkModule`, `DispatchersModule`.
Деталі: [.claude/architecture.md](../.claude/architecture.md).

## Карта пакетів
```
com.example.stationinspector/
├── di/          ← 4 Hilt-модулі (+ @IoDispatcher)
├── domain/      ← model + repository interfaces + usecase
├── data/        ← local (Room) + remote (Retrofit) + repository impls + mapper + storage
├── camera/      ← CameraXController, ImageCompressor
├── worker/      ← ExportZipWorker
├── utils/       ← PolylineUtils, MapNavigation
└── ui/          ← screens (4 route VMs), inspection, export, navigation, components, theme
```
`ui/zone/` (legacy) і монолітний `StationListViewModel` — **видалено**.

## ⚠️ Статус критичного боргу (must read)
1. **Hardcoded API keys** — ВИРІШЕНО (`local.properties` → `BuildConfig`).
2. **`fallbackToDestructiveMigration()`** — ВИРІШЕНО для release: тепер **лише debug**; `MIGRATION_7_8` додає `shortcuts.isRoundTrip`, покрито `MigrationTest`.
3. **Тести** — ~39 юніт-тестів (pure logic, мапери, **Robolectric Room**, міграція, `RouteViewModel`).
4. **`StationListViewModel`** — ВИРІШЕНО: розбито на 4 VM + репозиторії/use cases; DAO в UI немає.

Повний список і залишок: [.claude/technical_debt.md](../.claude/technical_debt.md).

## Корисні точки входу в код
- `MainActivity.kt`, `StationInspectorApplication.kt` — старт.
- `ui/navigation/NavGraph.kt` — увесь nav-граф.
- `di/` — як зібрано граф залежностей.
- `domain/repository/` — контракти, з яких варто стартувати при змінах.
