# Agent Workspace — StationInspector

Цей каталог — робочий простір для агентів, які вдосконалюють проєкт. Кожен файл — це **самодостатній бриф** для конкретної ролі: контекст, посилання на код, обмеження, типові завдання.

## Як користуватися

1. **Будь-який агент починає з** [00-overview.md](00-overview.md) — 1-сторінковий огляд проєкту.
2. Далі читає **тільки той бриф**, що відповідає його ролі (нижче).
3. Поглиблений довідник — у [`.claude/`](../.claude/) (читати за потреби, не за замовчуванням).
4. Корінь правди про код — у [`app/src/main/java/com/example/stationinspector/`](../app/src/main/java/com/example/stationinspector/).

## Ролі агентів

| # | Бриф | Що робить | Головні файли |
|---|------|-----------|----------------|
| 00 | [Overview](00-overview.md) | Загальний контекст (читати всім) | [Claude.md](../Claude.md) |
| 01 | [Security](01-security.md) | API-ключі, секрети, BuildConfig | [NetworkModule.kt](../app/src/main/java/com/example/stationinspector/di/NetworkModule.kt), [MapWidget.kt](../app/src/main/java/com/example/stationinspector/ui/components/MapWidget.kt) |
| 02 | [Data & Persistence](02-data-persistence.md) | Room, міграції, репозиторії | [data/local/](../app/src/main/java/com/example/stationinspector/data/local/), [DatabaseModule.kt](../app/src/main/java/com/example/stationinspector/di/DatabaseModule.kt) |
| 03 | [Network](03-network.md) | Retrofit, ORS, Mapy.cz, geocoding | [data/remote/](../app/src/main/java/com/example/stationinspector/data/remote/), [NetworkModule.kt](../app/src/main/java/com/example/stationinspector/di/NetworkModule.kt) |
| 04 | [UI / Compose](04-ui-compose.md) | Екрани, навігація, тема | [ui/screens/](../app/src/main/java/com/example/stationinspector/ui/screens/), [ui/theme/](../app/src/main/java/com/example/stationinspector/ui/theme/) |
| 05 | [Camera & Media](05-camera-media.md) | CameraX, компресія фото, файлове сховище | [camera/](../app/src/main/java/com/example/stationinspector/camera/), [data/storage/](../app/src/main/java/com/example/stationinspector/data/storage/) |
| 06 | [Export & Background](06-export-background.md) | WorkManager, ZIP-експорт | [worker/](../app/src/main/java/com/example/stationinspector/worker/), [ui/export/](../app/src/main/java/com/example/stationinspector/ui/export/) |
| 07 | [Architecture Refactor](07-architecture-refactor.md) | Clean architecture, legacy-екрани, модулі | [ui/zone/](../app/src/main/java/com/example/stationinspector/ui/zone/), [ui/screens/StationListViewModel.kt](../app/src/main/java/com/example/stationinspector/ui/screens/StationListViewModel.kt) |
| 08 | [Testing](08-testing.md) | Юніт- і інструментальні тести | [app/src/test/](../app/src/test/), [app/src/androidTest/](../app/src/androidTest/) |
| 09 | [Cleanup & Tech-debt](09-cleanup.md) | Невикористані залежності, error handling | [build.gradle.kts](../app/build.gradle.kts), [libs.versions.toml](../gradle/libs.versions.toml) |

## Спільні правила для всіх агентів

- **Мова коду / коментарів**: англійська. UI-рядки — чеська (нові екрани); українська лишилася в legacy `ui/zone/*` — не множити.
- **minSdk 26, targetSdk 35, Kotlin 2.0, Compose, Hilt, Room** — не змінювати без узгодження.
- **Не порушувати Clean Architecture**: UI → ViewModel → Repository (interface). Прямі DAO в UI — анти-патерн (вже є борг у `StationListViewModel`).
- **Не вмикати `--no-verify`, не робити `git push --force`, не амендити чужі коміти.**
- **Перед PR** — переконатися, що `./gradlew assembleDebug` проходить.
- **Технічний борг** і його пріоритети — у [.claude/technical_debt.md](../.claude/technical_debt.md). Якщо знайдено нову проблему — додати туди.

## Карта документації

```
Claude.md                       ← корінь, project-wide instructions
.claude/                        ← довідник (читати за потреби)
  identity.md, tech_stack.md, architecture.md, database.md,
  api_network.md, navigation.md, screens.md, viewmodels.md,
  business_logic.md, design_system.md, technical_debt.md
agents/                         ← робочі брифи (цей каталог)
  README.md (цей файл)
  00-overview.md … 09-cleanup.md
```
