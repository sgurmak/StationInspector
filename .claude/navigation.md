# Navigation Graph

## Compose Navigation Routes

```
station_list?banner={banner}          ← START DESTINATION (MainAppScreen)
    │                                    Owns: Scaffold + BottomNavBar + gradient
    │                                    Tabs: Work(0) | Map(1) | Export(2) | Settings(3)
    │
    ├──[station click]──→ camera/{stationId}/{zoneName}
    │                        │
    │                        └──[✓ done]──→ gallery/{stationId}/{zoneName}
    │                                          │
    │                                          ├──[+ Add Photo]──→ camera/{stationId}/{zoneName}
    │                                          │                     (popUpTo gallery, inclusive)
    │                                          │
    │                                          └──[Confirm]──→ station_list
    │                                                            (popUpTo station_list, inclusive)
    │
    └──[export click]──→ export/{date}
                            │
                            └──[success]──→ station_list?banner=success
                                              (popUpTo station_list, inclusive)
```

## Route Definitions

| Route | Arguments | Screen |
|---|---|---|
| `station_list?banner={banner}` | `banner: String?` (nullable, default null) | `MainAppScreen` |
| `camera/{stationId}/{zoneName}` | `stationId: String`, `zoneName: String` | `CameraScreen` |
| `gallery/{stationId}/{zoneName}` | `stationId: String`, `zoneName: String` | `GalleryScreen` |
| `export/{date}` | `date: String` | `ExportScreen` |

## Tab Navigation (within MainAppScreen)

Tabs are NOT separate nav routes. They switch via local `currentTab` state, held
in `rememberSaveable` (survives process death / config change). `MainTab` is a
Serializable enum. The bottom bar is a Material 3 `NavigationBar`.

Each screen resolves its own ViewModels via `hiltViewModel()`. Because all tab
content shares the `station_list` `NavBackStackEntry` (it's rendered inside
`MainAppScreen`), `RouteViewModel` is the **same instance** across the Work and
Map tabs — preserving route/date state without prop-drilling.

| Tab Index | Label | Screen Composable | ViewModels |
|---|---|---|---|
| 0 | Work | `StationListScreen` | `RouteViewModel` + `SettingsViewModel` (loading/snackbar host) |
| 1 | Map | `MapScreen` | `RouteViewModel` + `SearchViewModel` + `ShortcutsViewModel` |
| 2 | Export | `ExportScreen` | `ExportViewModel` (date from `RouteViewModel`) |
| 3 | Settings | `SettingsScreen` | `SettingsViewModel` |

A cold-start `SplashScreen` (FleetWay branding) overlays the Work destination
until its animation completes (`rememberSaveable` showSplash flag in `NavGraph`).

## Navigation Entry Points

- **Station click** (from StationListScreen): if `totalPhotos > 0` → gallery, else → camera. Zone defaults to `ENTRANCE`.
- **Export click** (from tab): passes selected date string to `ExportScreen`.
- **Camera → Gallery**: replaces camera in backstack (popUpTo camera, inclusive).
- **Gallery → Camera** (add photo): replaces gallery in backstack (popUpTo gallery, inclusive).
- **Gallery → Confirm**: pops entire stack back to station_list.
- **Export → Success**: navigates to station_list with `banner=success`.
