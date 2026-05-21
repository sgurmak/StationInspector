# StationInspector — Global project context

## Briefly about the project
**StationInspector** (`com.example.stationinspector`) is a native Android app (Kotlin, minSdk 26, targetSdk 35) for inspecting Czech railway stations. Inspectors import a daily schedule of stations from CSV, follow an optimized driving route on a map, photograph each station zone (entrance, waiting area, restroom), categorize photos as normal or defect, and export a structured ZIP archive for client delivery.

## Technology stack (key)
- **Language**: Kotlin 2.0.0, Jetpack Compose + Material 3
- **DI**: Dagger Hilt 2.52
- **Database**: Room 2.6.1 (5 tables, 8 migrations, KSP)
- **Network**: Retrofit 2.9 + OkHttp 4.12 — two APIs: OpenRouteService (routing/optimization) and Mapy.cz (geocoding/tiles)
- **Camera**: CameraX 1.4.0 with custom JPEG compression pipeline
- **Maps**: osmdroid 6.1.18 with Mapy.cz tile source (Czech Republic bounds)
- **Background**: WorkManager 2.10 (foreground ZIP export worker)

## Architecture
Clean Architecture (3 layers) with package-by-layer organization. Single `:app` module.

```
UI (Compose screens) → ViewModel (StateFlow) → Repository (interfaces) → DAO / API (Room + Retrofit)
```

DI wires everything via 3 Hilt modules: `AppModule`, `DatabaseModule`, `NetworkModule`.

## Key limitations and issues (important for agents)
1. **Hardcoded API keys** — ORS key in `NetworkModule.kt:20`, Mapy.cz tile key in `MapWidget.kt:26`. Must be moved to `local.properties` → `BuildConfig`.
2. **`fallbackToDestructiveMigration()` enabled** in `DatabaseModule.kt:41` — will silently destroy all user data if a migration path is missing.
3. **StationListViewModel breaks clean architecture** — directly injects `ShortcutDao`, `PoiDao`, `StationDao` alongside repository interfaces.
4. **No test coverage** — only boilerplate example tests exist. No regression protection for business logic.

## Files with detailed documentation (open if necessary)

| File | Contents |
|---|---|
| [`.claude/identity.md`](.claude/identity.md) | Project identity, package, SDK versions |
| [`.claude/tech_stack.md`](.claude/tech_stack.md) | Full library list with versions |
| [`.claude/architecture.md`](.claude/architecture.md) | Package structure, DI graph, architectural notes |
| [`.claude/database.md`](.claude/database.md) | Tables, columns, migrations, DAOs |
| [`.claude/api_network.md`](.claude/api_network.md) | API endpoints, DTOs, auth, map config |
| [`.claude/navigation.md`](.claude/navigation.md) | Nav graph, routes, tab navigation |
| [`.claude/screens.md`](.claude/screens.md) | All 10 screens with roles and components |
| [`.claude/viewmodels.md`](.claude/viewmodels.md) | 4 ViewModels — state, dependencies, operations |
| [`.claude/business_logic.md`](.claude/business_logic.md) | Photo rules, export pipeline, route optimization, CSV import |
| [`.claude/design_system.md`](.claude/design_system.md) | Colors, typography, shapes, interaction patterns |
| [`.claude/technical_debt.md`](.claude/technical_debt.md) | 9 issues ranked by severity |

---

> When performing a new task: read this file (`Claude.md`) and 1-2 files from `.claude/` that are relevant to the task.
