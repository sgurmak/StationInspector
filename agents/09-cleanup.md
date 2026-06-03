# 09 — Cleanup & Tech-debt Agent

**Місія**: дрібні, але кумулятивно важливі покращення — мертвий код, error handling, магічні рядки, чистота.

## ВИРІШЕНО
1. **Мертвий код прибрано** — `ShortcutUiModel.entity`, дублікати `import`, дубль-коментар; також мертві `StationWithPhotoCount` + запит і `getRouteCacheByCoordinates` (див. [02-data-persistence.md](02-data-persistence.md)).
2. **Магічні рядки → константи** — id/назви шорткатів винесено в `Shortcut.ID_HOME/ID_WORK/ID_NEW`, `Shortcut.NAME_HOME/NAME_WORK` ([domain/model/Shortcut.kt:19-27](../app/src/main/java/com/example/stationinspector/domain/model/Shortcut.kt)).
3. **Alias-шар кольорів прибрано** — семантичні токени в [theme/Color.kt](../app/src/main/java/com/example/stationinspector/ui/theme/Color.kt) (`AccentRed` для destructive/alert замість колишнього `WarningRed`-alias).
4. **`printStackTrace` → `Log`** — у `src/main` не лишилось жодного `printStackTrace`; помилки логуються через `android.util.Log` з `TAG` (worker, репозиторії).
5. **Mapy.cz таймаути** — додано 45s connect/read (див. [03-network.md](03-network.md)).
6. **Дедуплікація `geo:`-Intent** — зведено в `utils/MapNavigation.openInMaps` (див. [04-ui-compose.md](04-ui-compose.md)).

## Залишок
### 1. Apache POI досі невикористаний
- `poi-ooxml` оголошено в [app/build.gradle.kts:113](../app/build.gradle.kts) (`implementation(libs.poi.ooxml)`) + [gradle/libs.versions.toml](../gradle/libs.versions.toml), але **жодного `org.apache.poi`-імпорту** в `app/src` (перевірено). Прибрати dependency + версію → зменшити APK. Експорт використовує zip4j, не POI.

### 2. Індекси БД (потребує version bump 8→9)
- Немає `@Index` на гарячих колонках: `pois` / `stations` — `inspectionDate`, `orderIndex`. Сортування/фільтри по даті й порядку йдуть full-scan. Додати `@Index` + `MIGRATION_8_9` (`CREATE INDEX`) + експорт схеми v9 + `MigrationTest`-кейс.

### 3. Локалізація → strings.xml (опційно)
- Частина чеських рядків ще inline в Composable. Винести в `res/values/strings.xml` (+ `values-cs/`) — готує ґрунт для офіційної багатомовності.

### 4. Root README (опційно)
- Кореня все ще нема `README.md` для людей (є лише `Claude.md`). Короткий README з build/run, де брати API-ключі (`local.properties`), як імпортувати CSV.

### 5. Lint / detekt (опційно)
- Додати `detekt` або `./gradlew lint` як gate, прибрати накопичені warnings.

## Файли під увагу
- [app/build.gradle.kts](../app/build.gradle.kts)
- [gradle/libs.versions.toml](../gradle/libs.versions.toml)
- [data/local/AppDatabase.kt](../app/src/main/java/com/example/stationinspector/data/local/AppDatabase.kt) (для `@Index` + 8→9)
- [data/local/entity/PoiEntity.kt](../app/src/main/java/com/example/stationinspector/data/local/entity/PoiEntity.kt), [StationEntity.kt](../app/src/main/java/com/example/stationinspector/data/local/entity/StationEntity.kt)

## Правила
- Перед кожним пунктом — окремий commit. Не змішувати.
- Кожен крок має проходити `./gradlew assembleDebug`.
- Зміни схеми БД = bump version + експорт схеми + `MigrationTest`.
