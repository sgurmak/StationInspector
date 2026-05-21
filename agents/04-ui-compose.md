# 04 — UI / Compose Agent

**Місія**: екрани, навігація, дизайн-система. Чистий Compose, Material 3, чеська локалізація для нових екранів.

## Контекст
- Інвентар екранів: [.claude/screens.md](../.claude/screens.md).
- Навігація: [.claude/navigation.md](../.claude/navigation.md).
- Дизайн-система: [.claude/design_system.md](../.claude/design_system.md).
- ViewModels: [.claude/viewmodels.md](../.claude/viewmodels.md).

## Структура
```
ui/
├── screens/         ← MainAppScreen, StationListScreen, MapScreen, SettingsScreen, StationListViewModel
├── inspection/      ← CameraScreen, GalleryScreen, ZoneInspectionViewModel, ZoneGalleryScreen (legacy)
├── export/          ← ExportScreen, ExportViewModel
├── zone/            ← ZoneListScreen, ZoneListViewModel (LEGACY — не розширювати)
├── navigation/      ← NavGraph.kt
├── components/      ← MapWidget.kt (osmdroid wrapper)
└── theme/           ← Color.kt, Type.kt, Theme.kt
```

## Основні екрани
- **MainAppScreen** — Scaffold, gradient `#392153 → #13111A`, 4 таби (Work / Map / Export / Settings).
- **StationListScreen** — календар, мап-віджет, картки станцій/POI.
- **MapScreen** — повна мапа + bottom sheet, optimize, drag-reorder, shortcuts.
- **Camera + Gallery** — інспекція 3 зон (Nádraží / Čekárna / WC).

## ⚠️ Legacy
- `ui/zone/ZoneListScreen.kt`, `ui/zone/ZoneListViewModel.kt`, `ui/inspection/ZoneGalleryScreen.kt` — старі екрани з українськими лейблами. Не додавати фічі; план видалення — у [07-architecture-refactor.md](07-architecture-refactor.md).

## Правила
- **Стан** — `StateFlow` з ViewModel; у Composable — `collectAsStateWithLifecycle()`.
- **`hiltViewModel()`** — для отримання VM; `StationListViewModel` спільний на 3 таби (scoped на `MainAppScreen`).
- **Material 3** — кольори через `MaterialTheme.colorScheme`, типографіка через `MaterialTheme.typography`.
- **Нова локалізація** — чеська. Українські рядки лишаються тільки у `ui/zone/*` (вмирає).
- **Жодних блокуючих викликів** у Composable; усі операції — через VM-suspend або Flow.

## Готово, коли (для нової фічі)
- Екран — stateless Composable + ViewModel із StateFlow.
- Жодних `e.printStackTrace()` без user-facing зворотного зв'язку.
- Темна тема (єдина зараз) виглядає коректно.
