# 08 — Testing Agent

**Місія**: збудувати тестове покриття з нуля. Пріоритет — бізнес-логіка з найвищим ризиком регресій.

## Поточний стан
- `app/src/test/` і `app/src/androidTest/` — лише boilerplate (`ExampleUnitTest`, `ExampleInstrumentedTest`).
- Жодних тестів на CSV-імпорт, route optimization, експорт, компресію, міграції.

## Пріоритезація (high → low)
1. **CSV-імпорт** ([.claude/business_logic.md](../.claude/business_logic.md) → секція Import). Чисто-функціональний парсер → ідеальний кандидат для юніт-тестів.
2. **Route optimization wrapper** (`RouteRepositoryImpl`) — з `MockWebServer`.
3. **Room migrations** — `MigrationTestHelper` (вимагає `exportSchema = true`, див. [02-data-persistence.md](02-data-persistence.md)).
4. **ExportZipWorker** — fake `FileStorageManager`, перевірити структуру ZIP.
5. **ImageCompressor** — instrumentation-тест із реальним JPEG-фікстуром.
6. **ViewModels** — `StateFlow`-snapshots через Turbine, корутини через `kotlinx-coroutines-test`.

## Інструментарій (додати до libs.versions.toml)
- `junit:4.13.2` (вже є)
- `kotlinx-coroutines-test`
- `app.cash.turbine:turbine`
- `org.mockito:mockito-core` або `io.mockk:mockk`
- `androidx.room:room-testing`
- `com.squareup.okhttp3:mockwebserver`
- `androidx.test.ext:junit`, `espresso-core` (вже є частково)

## Правила
- Тестувати **домен/репо** на JVM (швидко). Інструментальні — лише там, де потрібен Android-framework (Room migrations, Compose UI, CameraX).
- Не мокати DB у тестах міграцій — використовувати `MigrationTestHelper` із реальною схемою.
- Один тест — один сценарій, говорящі назви (`given_X_when_Y_then_Z`).

## Готово, коли
- Хоча б один юніт-тест на кожен пункт 1-5.
- CI-конфіг (якщо буде) — `./gradlew test` зелений.
