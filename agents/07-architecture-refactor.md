# 07 — Architecture Refactor Agent

**Місія**: усунути порушення Clean Architecture, видалити legacy-екрани, підготувати ґрунт для модульного поділу.

## Контекст
- Архітектура: [.claude/architecture.md](../.claude/architecture.md).
- Борг: [.claude/technical_debt.md](../.claude/technical_debt.md) (#3, #4, #7).

## 1. StationListViewModel ламає шари
[StationListViewModel.kt](../app/src/main/java/com/example/stationinspector/ui/screens/StationListViewModel.kt) інжектить `ShortcutDao`, `PoiDao`, `StationDao` напряму.

**Дії**:
1. Створити `ShortcutRepository`, `PoiRepository` інтерфейси в `domain/repository/`.
2. Реалізації у `data/repository/`.
3. Замінити DAO-залежності в VM на репозиторії.
4. Оновити `DatabaseModule` (`@Provides` для нових репо).

## 2. Legacy-екрани (UA)
- [ui/zone/ZoneListScreen.kt](../app/src/main/java/com/example/stationinspector/ui/zone/ZoneListScreen.kt)
- [ui/zone/ZoneListViewModel.kt](../app/src/main/java/com/example/stationinspector/ui/zone/ZoneListViewModel.kt)
- [ui/inspection/ZoneGalleryScreen.kt](../app/src/main/java/com/example/stationinspector/ui/inspection/ZoneGalleryScreen.kt)

**Дії**:
1. У [ui/navigation/NavGraph.kt](../app/src/main/java/com/example/stationinspector/ui/navigation/NavGraph.kt) перевірити, чи маршрути до них досяжні.
2. Якщо ні — видалити файли + рядки в `NavGraph`.
3. Якщо так — мігрувати каллери на `CameraScreen`/`GalleryScreen`.

## 3. Модульний поділ (опційно, low priority)
Цільова структура (на майбутнє):
```
:app                 ← Application, MainActivity, NavGraph, DI roots
:core:design         ← Theme, Color, Type, shared Composables
:core:data           ← Room, mappers, storage
:core:network        ← Retrofit, DTOs
:feature:station-list
:feature:map
:feature:inspection
:feature:export
```
Поки `:app` — єдиний модуль. Перед розбиттям — узгодити з юзером.

## Правила
- Direction rule: `ui → domain ← data`. UI/domain **не імпортують** з `data/`.
- Інтерфейс репозиторію — у `domain/repository/`. Імплементація — у `data/repository/`. Bind у `DatabaseModule` через `@Binds` або `@Provides`.
- Не вводити нові DAO-зв'язки з VM.

## Готово, коли
- `StationListViewModel` не має жодного `*Dao` у конструкторі.
- `ui/zone/*` видалено або повністю мігровано.
- `git grep -r "import com.example.stationinspector.data.local.dao" app/src/main/java/com/example/stationinspector/ui` — порожній.
