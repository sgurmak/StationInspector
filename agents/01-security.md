# 01 — Security Agent

**Місія**: усунути hardcoded секрети, тримати ключі у `BuildConfig`, не дати чутливим даним залишити пристрій.

## Контекст
- [Claude.md](../Claude.md) і [00-overview.md](00-overview.md) — фон.
- Деталі мережі/ключів: [.claude/api_network.md](../.claude/api_network.md).

## Стан ключів — ВИРІШЕНО
**ОБИДВА API-ключі тепер у `BuildConfig`, жодного hardcoded-літерала в `src/`.**

| Ключ | Звідки береться | Де вживається | Призначення |
|---|---|---|---|
| `ORS_API_KEY` | `local.properties` → `BuildConfig` ([app/build.gradle.kts:31-32](../app/build.gradle.kts)) | `Authorization` header в [NetworkModule.kt:27](../app/src/main/java/com/example/stationinspector/di/NetworkModule.kt) | OpenRouteService (routing / VROOM optimization) — **платний** |
| `MAPY_CZ_API_KEY` | `local.properties` → `BuildConfig` ([app/build.gradle.kts:29-30](../app/build.gradle.kts)) | `apikey` query у tile-source [MapWidget.kt:78](../app/src/main/java/com/example/stationinspector/ui/components/MapWidget.kt) + Mapy.cz search ([AppModule.kt](../app/src/main/java/com/example/stationinspector/di/AppModule.kt)) | геокодинг + osmdroid-тайли |

- Якщо ключа немає в `local.properties` — підставляється рядок `KEY_NOT_FOUND` (fail-fast при першому запиті, а не silently-empty).
- `local.properties` ігнорується git (стандартно в Android-шаблоні).

## ВИРІШЕНО — інше
- **HTTP body-логування лише в debug** — [NetworkModule.kt:34-42](../app/src/main/java/com/example/stationinspector/di/NetworkModule.kt): `HttpLoggingInterceptor.Level.BODY` під `BuildConfig.DEBUG`, інакше `NONE`. Це не дає `Authorization`-ключу й геокодованим координатам/адресам потрапити в release-логи.
- **`allowBackup="false"`** + **`data_extraction_rules.xml`** (exclude-all для `cloud-backup` і `device-transfer`) — [AndroidManifest.xml:15-16](../app/src/main/AndroidManifest.xml), [res/xml/data_extraction_rules.xml](../app/src/main/res/xml/data_extraction_rules.xml). Інспекційні дані (станції, координати, шляхи фото, налаштування) не залишають пристрій.

## Permissions (AndroidManifest)
- `INTERNET`, `ACCESS_NETWORK_STATE`, `CAMERA`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`. Усі вживані; storage-permission не потрібен (scoped storage у `filesDir`).

## Залишок / на що зважати
- **Ротація скомпрометованих ключів**: якщо ключі колись були в git-історії — це дія користувача (`git filter-repo` / BFG + ротація на стороні провайдера). НЕ виконувати без явного дозволу.
- Будь-який новий секрет — лише через `local.properties` → `BuildConfig`, ніколи літералом.

## Готово, коли
- `git grep` по ключових літералах (`5b3ce…`, інші) — порожній (виконано).
- Build проходить із порожнім `local.properties` на fail-fast `KEY_NOT_FOUND` (виконано).
- Документація в [.claude/api_network.md](../.claude/api_network.md) синхронізована (виконано).
