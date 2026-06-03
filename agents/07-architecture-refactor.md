# 07 — Architecture Refactor Agent

**Місія**: усунути порушення Clean Architecture, видалити legacy-екрани, підготувати ґрунт для модульного поділу.

## Контекст
- Архітектура: [.claude/architecture.md](../.claude/architecture.md).
- Борг: [.claude/technical_debt.md](../.claude/technical_debt.md).

## 1. Монолітний `StationListViewModel` — ВИРІШЕНО
`StationListViewModel` (інжектив `ShortcutDao`/`PoiDao`/`StationDao` напряму) **видалений** і розбитий на фокусні VM:
- `RouteViewModel`, `SearchViewModel`, `ShortcutsViewModel`, `SettingsViewModel` (`ui/screens/`), плюс `ZoneInspectionViewModel` (`ui/inspection/`) та `ExportViewModel` (`ui/export/`).
- **У жодному VM немає DAO** — лише repository-інтерфейси.

Створені шари:
- **Репозиторії** (інтерфейси в `domain/repository/`, impl у `data/repository/`): `ShortcutRepository`, `PoiRepository`, `PreferencesRepository` (+ наявні `Station`/`Route`/`MapyCz`). Усі повертають **domain-моделі**, не entity.
- **Use cases** (`domain/usecase/`): `StationNameCleaner`, `ParseStationsCsvUseCase` (стрімінговий, `Sequence<String>`), `ImportStationsUseCase`.
- **Мапери** (`data/mapper/`): `StationMapper`, `ShortcutMapper` (Gson інкапсульовано тут), `PoiMapper`.
- **Транзакції**: domain-інтерфейс `TransactionRunner` + `RoomTransactionRunner` (data) **замість інжекту `AppDatabase`** у VM.
- **Диспетчер**: `@IoDispatcher` ([di/Dispatchers.kt](../app/src/main/java/com/example/stationinspector/di/Dispatchers.kt)) замість hardcoded `Dispatchers.IO`.
- UI-моделі + їх мапери → [ui/screens/RouteModels.kt](../app/src/main/java/com/example/stationinspector/ui/screens/RouteModels.kt).

## 2. Розрив циклу залежностей — ВИРІШЕНО
- `RouteRepository` **більше не імпортує** `RouteCacheEntity` / `RouteListItem` / `osmdroid.GeoPoint` — лише domain-типи `RouteSegment` / `RouteWaypoint` / `OptimizedRoute` / `GeoCoordinate` ([domain/repository/RouteRepository.kt](../app/src/main/java/com/example/stationinspector/domain/repository/RouteRepository.kt)).
- `MapyCzRepository.searchLocation` повертає `List<PoiLocation>` (domain).
- **`domain/` не залежить від `data/`, `ui/` чи `osmdroid`.** Direction rule `ui → domain ← data` дотримано.

## 3. Legacy-екрани (UA) — ВИРІШЕНО
- `ui/zone/ZoneListScreen.kt`, `ui/zone/ZoneListViewModel.kt`, `ui/inspection/ZoneGalleryScreen.kt` — **видалені** разом із недосяжними маршрутами в `NavGraph`. Каталог `ui/zone/` більше не існує. Живий UI використовує `CameraScreen`/`GalleryScreen`.

## 4. DI — ВИРІШЕНО
- **4 Hilt-модулі**: `AppModule`, `DatabaseModule`, `NetworkModule`, **`DispatchersModule`** (новий). `DatabaseModule` біндить усі нові репозиторії + `TransactionRunner`.

## 5. Модульний поділ (опційно, low priority)
Поки `:app` — **єдиний модуль**. Цільова (на майбутнє) структура:
```
:app                 ← Application, MainActivity, NavGraph, DI roots
:core:design / :core:data / :core:network
:feature:station-list / :feature:map / :feature:inspection / :feature:export
```
Перед розбиттям — узгодити з юзером.

## Правила
- Direction rule: `ui → domain ← data`. UI/domain **не імпортують** з `data/`.
- Інтерфейс репозиторію — у `domain/repository/`; імплементація — у `data/repository/`; bind у `DatabaseModule`.
- Не вводити нові DAO-зв'язки з VM. Транзакції — через `TransactionRunner`. IO — через `@IoDispatcher`.

## Залишок
- Винесення частини route-логіки (`rebuildHomePointsAndIndices` тощо) з `RouteViewModel` у dedicated use cases — **опційно**, low priority.
- Single-module → multi-module — лише за згодою користувача.

## Готово, коли
- `RouteViewModel` (і решта VM) не мають жодного `*Dao` у конструкторі (виконано).
- `ui/zone/*` видалено (виконано).
- `git grep "import com.example.stationinspector.data.local.dao" .../ui` — порожній (виконано).
