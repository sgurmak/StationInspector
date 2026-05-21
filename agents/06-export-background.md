# 06 — Export & Background Work Agent

**Місія**: надійний ZIP-експорт через `WorkManager`, foreground service, share-intent з результатом.

## Контекст
- Деталі пайплайну: [.claude/business_logic.md](../.claude/business_logic.md) (секція Export).

## Файли
```
worker/ExportZipWorker.kt           ← @HiltWorker, foreground, ZIP build
ui/export/
├── ExportScreen.kt                 ← summary + button + share intent
└── ExportViewModel.kt              ← запуск worker, спостереження за WorkInfo
StationInspectorApplication.kt      ← Configuration.Provider + HiltWorkerFactory
di/AppModule.kt                     ← WorkManager provider
data/storage/FileStorageManager.kt  ← джерело файлів для ZIP
```

## Структура архіву
Будується з `Station + List<Photo>` згрупованих по даті/зоні/категорії. Якщо змінюється — синхронізувати з клієнтом, оновити [.claude/business_logic.md](../.claude/business_logic.md).

## Правила
- Worker — `@HiltWorker` з `@AssistedInject`. Без зміни `Configuration.Provider` у `Application` — не запускати з кастомного фабричного коду.
- Прогрес — через `setProgressAsync(workDataOf(...))`, споживати в VM через `WorkManager.getWorkInfoByIdLiveData`/`Flow`.
- Foreground notification обов'язкова для довгих ZIP (>~10 МБ).
- Файл результату — у `cacheDir` або `filesDir`; share через `FileProvider` (перевірити `AndroidManifest` + `xml/file_paths.xml`).

## Що покращити
- Скасування експорту з UI.
- Стійкість до OOM на великих наборах фото (стрімінг у ZIP замість acumulate-in-memory).
- Чітке повідомлення про помилки (зараз — часто silent).
- Тест на структуру ZIP (юніт із fake storage).

## Готово, коли
- Експорт коректно завершується в фоні при згорнутому додатку.
- Share intent відкриває валідний ZIP.
- Помилки доходять до UI через `WorkInfo.failureReason` або state.
