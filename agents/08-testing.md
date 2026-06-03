# 08 — Testing Agent

**Місія**: будувати тестове покриття. Пріоритет — бізнес-логіка з найвищим ризиком регресій.

## Поточний стан — ~39 юніт-тестів (було лише boilerplate)
`app/src/test/` тепер містить реальне покриття (раніше — самий `ExampleUnitTest`):

| Файл | Що покриває | Підхід |
|---|---|---|
| `domain/usecase/StationNameCleanerTest.kt` (11) | очистка назв станцій | pure JVM |
| `domain/usecase/ParseStationsCsvUseCaseTest.kt` (8) | стрімінговий CSV-парсер | pure JVM |
| `data/mapper/ShortcutMapperTest.kt` (4) | entity↔domain + Gson | pure JVM |
| `data/mapper/PoiMapperTest.kt` (2) | entity↔domain | pure JVM |
| `data/repository/ShortcutRepositoryRoomTest.kt` (3) | репозиторій на **реальному SQLite** | Robolectric Room |
| `data/repository/StationRepositoryRoomTest.kt` (2) | репозиторій на реальному SQLite | Robolectric Room |
| `data/local/MigrationTest.kt` (1) | міграція **7→8** (`isRoundTrip` fix) | room-testing `MigrationTestHelper` |
| `data/repository/RouteRepositoryImplTest.kt` (5) | кеш + VROOM-оптимізація | mockk |
| `ui/screens/RouteViewModelTest.kt` (3) | стан/операції VM | mockk + coroutines-test |

`app/src/androidTest/` — поки лише `ExampleInstrumentedTest`.

## Інструментарій (у [app/build.gradle.kts:127-135](../app/build.gradle.kts))
- `junit:4.13.2`
- `org.robolectric:robolectric:4.13` — Room на реальному SQLite на JVM. `@Config(sdk = [34])`.
- `androidx.test:core-ktx:1.6.1`
- `androidx.room:room-testing:2.6.1` — `MigrationTestHelper`.
- `io.mockk:mockk:1.13.12`
- `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1`
- `testOptions.unitTests`: `isReturnDefaultValues = true` (un-mocked `android.util.Log` тощо), `isIncludeAndroidResources = true` (Robolectric потребує merged-ресурси для Room).

## TLS / Avast (важливо для запуску)
Forked test-JVM **не** успадковує SSL-property демона, тож Robolectric-завантаження `android-all` падає за TLS-інтерсептором (Avast HTTPS-сканування). Якщо задано property **`testTrustStore`** (тримається в `~/.gradle`, поза репо) — `tasks.withType<Test>` прокидає `-Djavax.net.ssl.trustStore*` у воркери ([build.gradle.kts:148-156](../app/build.gradle.kts)). Деталі — у Claude.md.

## Правила
- Тестувати **домен/мапери/use cases** на чистому JVM (швидко).
- Room — через Robolectric (реальний SQLite), **не** мокати DB.
- Міграції — `MigrationTestHelper` з реальною схемою (потребує `exportSchema = true`, виконано).
- VM — `kotlinx-coroutines-test` + mockk на репозиторії.
- Один тест — один сценарій, говорящі назви.

## Залишок
- **Compose UI / instrumented** тести ще не написані (`androidTest` — boilerplate).
- **Історичні схеми v1-v7 не експортовані** (експорт увімкнено пізніше) — тестувати можна лише **нові** міграції наперед; повний ланцюг старих версій недоступний для `MigrationTestHelper`.
- `MockWebServer` для end-to-end ORS/Mapy ще не доданий (зараз — mockk на `OrsApiService`).
- `ExportZipWorker` / `ImageCompressor` ще без тестів.

## Готово, коли
- Кожна high-risk-логіка (CSV-імпорт, route-оптимізація, міграції, мапери, VM) має юніт-тест (виконано для переліченого).
- `./gradlew test` зелений (із налаштованим `testTrustStore` у відповідному середовищі).
