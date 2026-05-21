# 09 — Cleanup & Tech-debt Agent

**Місія**: дрібні, але кумулятивно важливі покращення — APK-розмір, error handling, чистота build-скриптів.

## Завдання

### 1. Видалити Apache POI
- `poi-ooxml 5.2.3` оголошено в [gradle/libs.versions.toml](../gradle/libs.versions.toml) і [app/build.gradle.kts](../app/build.gradle.kts), але **жодного імпорту** в `src/`.
- Перевірити: `grep -r "org.apache.poi" app/src` → має бути порожньо.
- Видалити версію + dependency. Перебилдити, перевірити APK розмір.

### 2. Уніфікувати error handling
- Скрізь `e.printStackTrace()` (репозиторії, ViewModels). Замінити на:
  - `Timber` або `android.util.Log.e(TAG, msg, e)` + sealed `Result`.
  - Зробити помилки видимими для UI (snackbar / state).
- Особливо: `StationRepositoryImpl.fetchAndSaveCoordinates` (silent null), CSV import (swallows exceptions).

### 3. Локалізація → strings.xml
- Багато hardcoded чеських/українських рядків у Composable. Перенести в `res/values/strings.xml` (та опційно `values-cs/`, `values-uk/`).
- Це готує ґрунт для офіційної багатомовності.

### 4. Lint / detekt
- Додати [detekt](https://detekt.dev/) або принаймні `./gradlew lint` як обов'язковий gate.
- Виправити warnings, які накопичились.

### 5. README на корені
- Поточний корінь не має `README.md` для людей (є `Claude.md`). Додати короткий README з кроками build/run, де брати API-ключі, як імпортувати CSV.

## Файли під увагу
- [app/build.gradle.kts](../app/build.gradle.kts)
- [gradle/libs.versions.toml](../gradle/libs.versions.toml)
- [data/repository/StationRepositoryImpl.kt](../app/src/main/java/com/example/stationinspector/data/repository/StationRepositoryImpl.kt)
- Усі Composable із hardcoded рядками.

## Правила
- Перед кожним пунктом — окремий PR/commit. Не змішувати.
- Кожен крок має проходити `./gradlew assembleDebug`.
