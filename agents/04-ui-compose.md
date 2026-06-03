# 04 — UI / Compose Agent

**Місія**: екрани, навігація, дизайн-система. Чистий Compose, Material 3, чеська локалізація для нових екранів.

## Контекст
- Інвентар екранів: [.claude/screens.md](../.claude/screens.md).
- Навігація: [.claude/navigation.md](../.claude/navigation.md).
- Дизайн-система: [.claude/design_system.md](../.claude/design_system.md).
- ViewModels: [.claude/viewmodels.md](../.claude/viewmodels.md).

## Структура (після рефакторингу)
```
ui/
├── screens/         ← MainAppScreen, StationListScreen, MapScreen, SettingsScreen, SplashScreen
│                      + RouteViewModel, SearchViewModel, ShortcutsViewModel, SettingsViewModel
│                      + RouteModels.kt (UI-моделі: RouteListItem/DailyRouteInfo + мапери)
├── inspection/      ← CameraScreen, GalleryScreen, ZoneInspectionViewModel
├── export/          ← ExportScreen, ExportViewModel
├── navigation/      ← NavGraph.kt, SharedUiModels.kt, MainTab (enum, Serializable)
├── components/      ← MapWidget.kt (osmdroid wrapper)
└── theme/           ← Color.kt, Type.kt, Theme.kt, ModifierUtils.kt
```
> `ui/zone/` (legacy UA-екрани) **ВИДАЛЕНО**. `StationListViewModel.kt` **ВИДАЛЕНО**.

## ВИРІШЕНО — основні зміни
- **Монолітний `StationListViewModel` розбито** на 4 фокусні VM: `RouteViewModel`, `SearchViewModel`, `ShortcutsViewModel`, `SettingsViewModel` (+ окремі `ZoneInspectionViewModel`, `ExportViewModel`). У VM немає DAO — лише репозиторії.
- **UI-моделі + мапери винесено** у [ui/screens/RouteModels.kt](../app/src/main/java/com/example/stationinspector/ui/screens/RouteModels.kt) (`RouteListItem`, `DailyRouteInfo` тощо). Серіалізацію/збірку перенесено у ViewModel, не в Composable.
- **Навігація на `MainTab`-enum** — [ui/navigation/](../app/src/main/java/com/example/stationinspector/ui/navigation/). Material **`NavigationBar`** замість кастомного бару; активний таб тримається у `rememberSaveable` ([MainAppScreen.kt:38](../app/src/main/java/com/example/stationinspector/ui/screens/MainAppScreen.kt)) — переживає process death. `MainTab` — Serializable enum, кастомний Saver не потрібен.
- **`SplashScreen`** (бренд FleetWay) — [ui/screens/SplashScreen.kt](../app/src/main/java/com/example/stationinspector/ui/screens/SplashScreen.kt), показ керується `rememberSaveable` у [NavGraph.kt:64](../app/src/main/java/com/example/stationinspector/ui/navigation/NavGraph.kt).
- **Семантичні токени кольорів** — [theme/Color.kt](../app/src/main/java/com/example/stationinspector/ui/theme/Color.kt): alias-шар прибрано, кольори мають змістовні імена (`AppGradientTop`/`AppGradientBottom`, `AccentPink`, `AccentGreen*`, `AccentRed` для destructive/alert, `Splash*`/`Brand*` для сплешу). Старий `WarningRed`/alias більше не вживається.
- **Декомпозиція великих екранів**:
  - `RouteCardScaffold` + `StationCard`/`PoiCard` винесено в [StationListScreen.kt](../app/src/main/java/com/example/stationinspector/ui/screens/StationListScreen.kt).
  - `SearchResultsList` + `ShortcutsRow` винесено з логіки [MapScreen.kt](../app/src/main/java/com/example/stationinspector/ui/screens/MapScreen.kt).
- **Дедуплікація map-deep-link** — `geo:`-Intent зведено в один хелпер [utils/MapNavigation.kt](../app/src/main/java/com/example/stationinspector/utils/MapNavigation.kt) (`Context.openInMaps(...)`).
- **Перформанс карти** ([components/MapWidget.kt](../app/src/main/java/com/example/stationinspector/ui/components/MapWidget.kt)): osmdroid `Configuration` ініціалізується ОДИН раз (не на кожну рекомпозицію); overlay перебудовується лише при зміні даних; bitmap-маркер кешується; 7-денний кеш тайлів; згорнута міні-карта взагалі не монтується.

## Основні екрани
- **MainAppScreen** — Scaffold + gradient (`AppGradientTop #392153 → AppGradientBottom #13111B`), 4 таби (Work / Map / Export / Settings) через `NavigationBar`.
- **StationListScreen** — календар, мап-віджет, картки станцій/POI (`StationCard`/`PoiCard`).
- **MapScreen** — повна мапа + bottom sheet, optimize, drag-reorder, shortcuts (`SearchResultsList`/`ShortcutsRow`).
- **Camera + Gallery** — інспекція 3 зон (Nádraží / Čekárna / WC).
- **SplashScreen** — брендований вхід.

## Правила
- **Стан** — `StateFlow` з ViewModel; у Composable — `collectAsStateWithLifecycle()`.
- **`hiltViewModel()`** — для отримання VM. VM scoped на `MainAppScreen` де потрібно ділити стан між табами.
- **Material 3** — кольори через `MaterialTheme.colorScheme` / семантичні токени з `Color.kt`; типографіка через `MaterialTheme.typography`.
- **Локалізація** — чеська для UI станцій/інспекції (Nádraží / Čekárna / WC). Українських лейблів у живому UI більше нема.
- **Жодних блокуючих викликів** у Composable; усі операції — через VM-suspend або Flow.

## Готово, коли (для нової фічі)
- Екран — stateless Composable + ViewModel із StateFlow.
- Жодних `e.printStackTrace()` без user-facing зворотного зв'язку (→ `Log` + state).
- Темна тема (єдина зараз) виглядає коректно.
