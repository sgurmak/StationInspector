# 01 — Security Agent

**Місія**: усунути hardcoded секрети, перевести ключі у `BuildConfig`, гарантувати, що `local.properties` не потрапляє в git.

## Контекст
- [Claude.md](../Claude.md) і [00-overview.md](00-overview.md) — фон.
- Поточна модель ключів: один ключ (`MAPY_CZ_API_KEY`) уже в `BuildConfig` через `AppModule`. Решта — hardcoded.

## Що треба полагодити (Critical)
| Ключ | Де hardcoded | Призначення |
|---|---|---|
| ORS API key (Base64) | [NetworkModule.kt:20](../app/src/main/java/com/example/stationinspector/di/NetworkModule.kt) | OpenRouteService (routing, optimization, geocoding) — **платний** |
| Mapy.cz tile API key | [MapWidget.kt:26](../app/src/main/java/com/example/stationinspector/ui/components/MapWidget.kt) | osmdroid tile URL |

## План
1. Додати в `local.properties` (НЕ комітити): `ORS_API_KEY=…`, `MAPY_CZ_TILE_KEY=…`.
2. Прокинути обидва в `BuildConfig` через `app/build.gradle.kts` (зразок — як уже зроблено для `MAPY_CZ_API_KEY` в [di/AppModule.kt](../app/src/main/java/com/example/stationinspector/di/AppModule.kt)).
3. Замінити рядкові літерали посиланнями на `BuildConfig.*`.
4. Перевірити `.gitignore` — `local.properties` має бути ігнорований (стандартно так і є в Android-шаблоні).
5. **Ротувати скомпрометовані ключі** після видалення з історії (повідомити користувача — це його дія).
6. Якщо ключ уже в git-історії — попередити користувача про `git filter-repo` / BFG (НЕ виконувати без явного дозволу).

## Файли під редагування
- [app/build.gradle.kts](../app/build.gradle.kts)
- [di/NetworkModule.kt](../app/src/main/java/com/example/stationinspector/di/NetworkModule.kt)
- [ui/components/MapWidget.kt](../app/src/main/java/com/example/stationinspector/ui/components/MapWidget.kt)
- [local.properties](../local.properties) (локально)

## Дотичне
- Перевірити, що логування OkHttp (`HttpLoggingInterceptor`) не друкує `Authorization` header у release.
- AndroidManifest permissions — переглянути, чи всі необхідні (камера, інтернет, storage).

## Готово, коли
- `git grep` по `5b3ce…`, `ORS`, `mapy.cz` не знаходить ключових літералів.
- Build проходить з порожнім `local.properties` лише на fail-fast повідомленнями (а не на silently-empty ключах).
- Документація в [.claude/api_network.md](../.claude/api_network.md) оновлена.
