# 02 — Data & Persistence Agent

**Місія**: безпечні Room-міграції, чисті репозиторії, відсутність втрат даних.

## Контекст
- Огляд: [00-overview.md](00-overview.md).
- Деталі схеми: [.claude/database.md](../.claude/database.md).

## Шар у коді
```
data/local/
├── AppDatabase.kt          ← @Database, exportSchema = false (борг)
├── converter/Converters.kt
├── dao/                    ← StationDao, PhotoDao, RouteCacheDao, PoiDao, ShortcutDao
└── entity/                 ← 5 entity + 2 projection views

data/repository/            ← StationRepositoryImpl, RouteRepositoryImpl, MapyCzRepository
data/mapper/StationMapper.kt
domain/repository/          ← StationRepository, RouteRepository (інтерфейси)
di/DatabaseModule.kt        ← provides DAO + DB + DataStore
```

## ⚠️ Критичний борг
1. **`fallbackToDestructiveMigration()` увімкнено** — [DatabaseModule.kt:41](../app/src/main/java/com/example/stationinspector/di/DatabaseModule.kt). Видалити перед production.
2. **`exportSchema = false`** — [AppDatabase.kt:22](../app/src/main/java/com/example/stationinspector/data/local/AppDatabase.kt). Увімкнути → отримати JSON schemas → додати `MigrationTestHelper`-тести.
3. **8 наявних міграцій** — переглянути на коректність, додати тести по схемах.

## Типові завдання
- Додавання таблиці / колонки → нова `Migration(N, N+1)` + entity update + DAO + тест.
- Зміна репозиторію → правити **інтерфейс** в `domain/repository/`, потім impl у `data/repository/`.
- Mapper entity↔domain — [StationMapper.kt](../app/src/main/java/com/example/stationinspector/data/mapper/StationMapper.kt).

## Правила
- Жодна Compose-Screen чи ViewModel **не повинна** імпортувати з `data/local/dao/*`. Виняток на сьогодні — `StationListViewModel` (борг, див. [07-architecture-refactor.md](07-architecture-refactor.md)).
- DAO повертають `Flow<T>` для live-queries; one-shot — `suspend fun`.
- TypeConverters централізовано в `Converters.kt`.
- DataStore (Preferences) — провайдиться в `DatabaseModule` (так склалось історично; за бажання — перенести в `AppModule`).

## Готово, коли
- `fallbackToDestructiveMigration` видалено.
- `exportSchema = true`, у `schemas/` є JSON під кожну версію.
- Є хоча б один `MigrationTestHelper`-тест.
