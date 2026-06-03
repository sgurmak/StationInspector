# 02 — Data & Persistence Agent

**Місія**: безпечні Room-міграції, чисті репозиторії, відсутність втрат даних.

## Контекст
- Огляд: [00-overview.md](00-overview.md).
- Деталі схеми: [.claude/database.md](../.claude/database.md).

## Шар у коді
```
data/local/
├── AppDatabase.kt          ← @Database(version = 8, exportSchema = true)
├── converter/Converters.kt
├── dao/                    ← StationDao, PhotoDao, RouteCacheDao, PoiDao, ShortcutDao
└── entity/                 ← 5 entity + 1 projection (StationWithSplitCounts)

data/repository/            ← StationRepositoryImpl, RouteRepositoryImpl, MapyCzRepository,
                              ShortcutRepositoryImpl, PoiRepositoryImpl, PreferencesRepositoryImpl,
                              RoomTransactionRunner
data/mapper/                ← StationMapper, ShortcutMapper (Gson інкапсульовано тут), PoiMapper
domain/repository/          ← StationRepository, RouteRepository, ShortcutRepository,
                              PoiRepository, PreferencesRepository, TransactionRunner (інтерфейси)
di/DatabaseModule.kt        ← provides DAO + DB + DataStore + усі репозиторії + TransactionRunner
di/DispatchersModule        ← @IoDispatcher (di/Dispatchers.kt)
app/schemas/                ← експортовані JSON-схеми Room
```

## ВИРІШЕНО — критичний борг
1. **`exportSchema = true`** — [AppDatabase.kt:21](../app/src/main/java/com/example/stationinspector/data/local/AppDatabase.kt). KSP пише схеми у `app/schemas/` (`ksp { arg("room.schemaLocation", ...) }` у [build.gradle.kts:69-71](../app/build.gradle.kts)). Наразі експортована лише `8.json` (поточна версія).
2. **`fallbackToDestructiveMigration()` — ЛИШЕ debug** — [DatabaseModule.kt:53-58](../app/src/main/java/com/example/stationinspector/di/DatabaseModule.kt): `if (BuildConfig.DEBUG) fallbackToDestructiveMigration()`. Release-білд при відсутньому шляху міграції краш-ить (видно проблему), а не мовчки стирає інспекційні дані.
3. **`MIGRATION_7_8` — латентний баг втрати даних виправлено** — [AppDatabase.kt:72-83](../app/src/main/java/com/example/stationinspector/data/local/AppDatabase.kt). `ShortcutEntity` оголошує `isRoundTrip`, але `MIGRATION_5_6` створив таблицю `shortcuts` без неї, а `6→7` був no-op. Тепер `7→8` додає `ALTER TABLE shortcuts ADD COLUMN isRoundTrip` (раніше тільки destructive-fallback ховав цей розрив схеми, стираючи дані). Покрито [MigrationTest](../app/src/test/java/com/example/stationinspector/data/local/MigrationTest.kt).
4. **Витік сховища при очистці виправлено** — `StationRepositoryImpl.clearAllData()` ([StationRepositoryImpl.kt:83-87](../app/src/main/java/com/example/stationinspector/data/repository/StationRepositoryImpl.kt)) тепер додатково викликає `FileStorageManager.clearAllPhotoFiles()`: раніше видалялися лише рядки БД, а файли фото лишалися на диску.
5. **Прибрано мертвий код** — `StationWithPhotoCount` + відповідний запит, `getRouteCacheByCoordinates`. Лишилася лише жива проекція `StationWithSplitCounts`.

## Транзакції та диспетчери
- VM/репозиторії **не інжектять `AppDatabase`**. Транзакції — через domain-інтерфейс `TransactionRunner` ([domain/repository/TransactionRunner.kt](../app/src/main/java/com/example/stationinspector/domain/repository/TransactionRunner.kt)), реалізація `RoomTransactionRunner` у data-шарі.
- Замість hardcoded `Dispatchers.IO` — `@IoDispatcher` (`di/Dispatchers.kt`), що дозволяє підміну в тестах.

## Типові завдання
- Додавання таблиці / колонки → нова `Migration(N, N+1)` + entity update + DAO + **експортована схема** + `MigrationTest`-кейс.
- Зміна репозиторію → правити **інтерфейс** у `domain/repository/`, потім impl у `data/repository/`; репозиторії повертають **domain-моделі**, не entity.
- Mapper entity↔domain — у `data/mapper/`. Gson не «протікає» в API репозиторіїв — інкапсульований у `ShortcutMapper`.

## Правила
- Жодна Compose-Screen чи ViewModel **не імпортує** з `data/local/dao/*` (виняток `StationListViewModel` усунено — його більше нема, див. [07-architecture-refactor.md](07-architecture-refactor.md)).
- DAO повертають `Flow<T>` для live-queries; one-shot — `suspend fun`.
- TypeConverters централізовано в `Converters.kt`.
- DataStore (Preferences) провайдиться в `DatabaseModule`; доступ — через `PreferencesRepository`.

## Залишок
- Історичні схеми v1-v7 **не** експортовані (експорт увімкнено пізніше). Нові міграції тестувати наперед; для старих ланцюгів `MigrationTest` обмежений.
- Немає `@Index` на гарячих колонках (`pois`/`stations` `inspectionDate`/`orderIndex`) — потребує version bump 8→9 (див. [09-cleanup.md](09-cleanup.md)).

## Готово, коли
- `fallbackToDestructiveMigration` лише в debug (виконано).
- `exportSchema = true`, у `app/schemas/` є JSON (виконано для v8).
- Є `MigrationTest` на критичний шлях (виконано для 7→8).
