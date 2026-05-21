# Technology Stack

| Category | Library | Version | Role |
|---|---|---|---|
| **DI** | Dagger Hilt | 2.52 | `@HiltAndroidApp`, `@HiltViewModel`, `@HiltWorker` |
| **Database** | Room | 2.6.1 | 5 tables, 8 migrations, KSP annotation processor |
| **Preferences** | DataStore Preferences | 1.0.0 | User settings (round-trip toggle) |
| **Networking** | Retrofit 2.9 + OkHttp 4.12 + Gson | — | Two API clients: OpenRouteService + Mapy.cz |
| **Maps** | osmdroid 6.1.18 | — | Mapy.cz tile source, polylines, numbered markers |
| **Camera** | CameraX 1.4.0 | — | Photo capture with zoom, flash, rotation |
| **Images** | Coil 2.7.0 | — | Async image loading in Compose |
| **Background** | WorkManager 2.10 | — | Foreground export worker (ZIP generation) |
| **ZIP** | Zip4j 2.11.5 | — | Archive creation for exports |
| **Office** | Apache POI 5.2.3 | — | Declared but not used in current source |
| **Navigation** | Compose Navigation | (BOM) | NavHost with 4 routes |
| **Drag & Drop** | compose-reorderable 0.9.6 | — | Reorderable station/POI lists in Map screen |
| **Icons** | Material Icons Extended | (BOM) | Full icon set |

## Build Configuration

- **Compile SDK**: 35
- **JVM Target**: 11
- **Compose**: enabled via Kotlin Compose plugin
- **BuildConfig**: enabled (used for Mapy.cz API key from `local.properties`)
- **KSP**: used for Room and Hilt annotation processing
- **ProGuard**: not enabled for release builds (`isMinifyEnabled = false`)
