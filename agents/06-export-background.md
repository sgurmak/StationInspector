# 06 — Export & Background Work Agent

**Місія**: надійний ZIP-експорт через `WorkManager`, foreground service, share-intent з результатом.

## Контекст
- Деталі пайплайну: [.claude/business_logic.md](../.claude/business_logic.md) (секція Export).

## Файли
```
worker/ExportZipWorker.kt           ← @HiltWorker, foreground, ZIP build (zip4j)
ui/export/
├── ExportScreen.kt                 ← summary + button + share intent
└── ExportViewModel.kt              ← запуск worker, спостереження за WorkInfo
StationInspectorApplication.kt      ← Configuration.Provider + HiltWorkerFactory
di/AppModule.kt                     ← WorkManager provider
data/storage/FileStorageManager.kt  ← джерело файлів для ZIP
domain/usecase/ImportStationsUseCase.kt + ParseStationsCsvUseCase.kt  ← стрімінговий CSV-імпорт
```

## ВИРІШЕНО — `ExportZipWorker` посилено
[ExportZipWorker.kt](../app/src/main/java/com/example/stationinspector/worker/ExportZipWorker.kt):
- **`try/finally` очистка staging** — `stagingDir.deleteRecursively()` у `finally`, тож тимчасова копія завжди видаляється (успіх / помилка / скасування) ([рядки 75, 146-148](../app/src/main/java/com/example/stationinspector/worker/ExportZipWorker.kt)).
- **`ZipFile(...).use { }`** — ресурс архіву коректно закривається ([рядок 131](../app/src/main/java/com/example/stationinspector/worker/ExportZipWorker.kt)).
- **`sanitizeSegment()` на КОЖЕН сегмент шляху** — назва станції/зони/дати санітизується ([рядки 54, 98-100](../app/src/main/java/com/example/stationinspector/worker/ExportZipWorker.kt)), щоб крафтнута назва станції не «вийшла» зі staging-директорії (path traversal).
- **Станції резолвляться один раз** через map (id → station), без повторних запитів у циклі по фото.
- **`ZONE_FOLDER` — лише `ENTRANCE`/`PLATFORM`/`RESTROOM`** → `Nádraží`/`Čekárna`/`WC` ([рядки 42-45](../app/src/main/java/com/example/stationinspector/worker/ExportZipWorker.kt)); невідома зона — фолбек на `zone.name`.
- **`printStackTrace` → `Log`** — помилки логуються через `Log.e/Log.w` з `TAG` ([рядки 68, 144](../app/src/main/java/com/example/stationinspector/worker/ExportZipWorker.kt)), а не німо в stderr.

## ВИРІШЕНО — CSV-імпорт стрімінговий
- `ImportStationsUseCase` читає `InputStream` через `bufferedReader(...).useLines { }` ([ImportStationsUseCase.kt:33](../app/src/main/java/com/example/stationinspector/domain/usecase/ImportStationsUseCase.kt)), а `ParseStationsCsvUseCase` приймає лінивий `Sequence<String>` ([ParseStationsCsvUseCase.kt:32](../app/src/main/java/com/example/stationinspector/domain/usecase/ParseStationsCsvUseCase.kt)) — без `readText()`/OOM на великих файлах. Покрито `ParseStationsCsvUseCaseTest`.

## Структура архіву
Будується з `Station + List<Photo>` згрупованих по даті/зоні/категорії. Папки зон — `Nádraží/Čekárna/WC`. Якщо змінюється — синхронізувати з клієнтом і оновити [.claude/business_logic.md](../.claude/business_logic.md).

## Правила
- Worker — `@HiltWorker` з `@AssistedInject`. Без зміни `Configuration.Provider` у `Application` — не запускати з кастомного фабричного коду.
- Прогрес — через `setProgress`/`WorkInfo`, споживати в `ExportViewModel` через `Flow`/`LiveData`.
- Foreground notification обов'язкова для довгих ZIP.
- Файл результату — у `cacheDir`/`filesDir`; share через `FileProvider` (`AndroidManifest` provider + `xml/file_paths.xml`).

## Залишок / що покращити
- Скасування експорту з UI (staging-cleanup уже стійкий до cancel).
- Чітке повідомлення про помилки в UI (зараз логуються, але `WorkInfo.failure` ще не завжди мапиться у user-facing state).
- Тест на структуру ZIP (fake `FileStorageManager`) ще не написаний.

## Готово, коли
- Експорт завершується в фоні при згорнутому додатку.
- Share intent відкриває валідний ZIP.
- Staging завжди прибирається (виконано), без path-traversal (виконано), без OOM на імпорті (виконано).
