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
| DI | Hilt 2.52 |
| БД | Room 2.6.1 (5 таблиць, 8 міграцій) |
| Мережа | Retrofit 2.9 + OkHttp 4.12 (ORS + Mapy.cz) |
| Камера | CameraX 1.4 |
| Мапи | osmdroid 6.1.18 + Mapy.cz tiles |
| Фон | WorkManager 2.10 |

Деталі: [.claude/tech_stack.md](../.claude/tech_stack.md).

## Архітектура (одним абзацом)
Clean Architecture, 3 шари, package-by-layer, один модуль `:app`.
`Compose → @HiltViewModel (StateFlow) → Repository (interface) → DAO / ApiService`.
DI — три Hilt-модулі: `AppModule`, `DatabaseModule`, `NetworkModule`.
Деталі: [.claude/architecture.md](../.claude/architecture.md).

## Карта пакетів
```
com.example.stationinspector/
├── di/          ← 3 Hilt-модулі
├── domain/      ← model + repository interfaces
├── data/        ← local (Room) + remote (Retrofit) + repository impls + storage
├── camera/      ← CameraXController, ImageCompressor
├── worker/      ← ExportZipWorker
├── utils/       ← PolylineUtils
└── ui/          ← screens, inspection, export, zone (legacy), navigation, theme
```

## ⚠️ Найкритичніший борг (must read)
1. **Hardcoded API keys** — [NetworkModule.kt:20](../app/src/main/java/com/example/stationinspector/di/NetworkModule.kt), [MapWidget.kt:26](../app/src/main/java/com/example/stationinspector/ui/components/MapWidget.kt).
2. **`fallbackToDestructiveMigration()`** — [DatabaseModule.kt:41](../app/src/main/java/com/example/stationinspector/di/DatabaseModule.kt) → видаляє дані юзера.
3. **Немає тестів** — лише boilerplate.
4. **`StationListViewModel`** ламає clean architecture (інжектить DAO напряму).

Повний список: [.claude/technical_debt.md](../.claude/technical_debt.md).

## Корисні точки входу в код
- `MainActivity.kt`, `StationInspectorApplication.kt` — старт.
- `ui/navigation/NavGraph.kt` — увесь nav-граф.
- `di/` — як зібрано граф залежностей.
- `domain/repository/` — контракти, з яких варто стартувати при змінах.
