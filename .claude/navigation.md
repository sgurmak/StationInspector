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

Tabs are NOT separate nav routes. They switch via local `currentTab` state:

| Tab Index | Label | Screen Composable | ViewModel |
|---|---|---|---|
| 0 | Work | `StationListScreen` | Shared `StationListViewModel` |
| 1 | Map | `MapScreen` | Shared `StationListViewModel` |
| 2 | Export | `ExportScreen` | Own `ExportViewModel` |
| 3 | Settings | `SettingsScreen` | Shared `StationListViewModel` |

## Navigation Entry Points

- **Station click** (from StationListScreen): if `totalPhotos > 0` → gallery, else → camera. Zone defaults to `ENTRANCE`.
- **Export click** (from tab): passes selected date string to `ExportScreen`.
- **Camera → Gallery**: replaces camera in backstack (popUpTo camera, inclusive).
- **Gallery → Camera** (add photo): replaces gallery in backstack (popUpTo gallery, inclusive).
- **Gallery → Confirm**: pops entire stack back to station_list.
- **Export → Success**: navigates to station_list with `banner=success`.
