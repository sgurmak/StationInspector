# Design System & File Counts

## Color Palette

Semantic tokens live in `ui/theme/Color.kt`. Per-screen pure-rename aliases were
removed (e.g. MapScreen uses tokens directly); a few semantic local aliases
remain (e.g. `CardBg`, `DateCircleSel`).

| Token | Hex | Usage |
|---|---|---|
| `AppGradientTop` | `#392153` | Background gradient start |
| `AppGradientBottom` | `#13111B` | Background gradient end |
| `ContentDark` | `#13111B` | Core dark surface (nav bar, sheet backgrounds) |
| `ContentLight` | `#FBF7FF` | Primary text/icons on dark backgrounds |
| `ContentLightSecondary` | `#F5EDFF` | Muted lavender text/details |
| `CardContent` | `#261937` | Text/icons on light card backgrounds |
| `AccentPink` | `#CA065E` | Defect counts, destructive/accent (StationListScreen alias `WarningAccent`) |
| `AccentGreen` | `#4ADE80` | Camera confirm/active green |
| `AccentGreenAlt` | `#16A34A` | Snackbar success / confirm-inspection |
| `AccentGreenConfirm` | `#00C853` | Map edit confirm action |
| `DestructiveBg` | `#FFD3EB` | Clear-storage button surface |
| `BrandViolet`/`BrandLavender`/`SplashGradient*` | — | FleetWay splash branding |
| (route line in `MapWidget`) | `#8B5CF6` | Polyline + numbered marker pins (literal, drawing code) |

## Typography

- **Font**: System default (no custom fonts)
- **Headers**: SemiBold, 18-20sp
- **Body**: Normal/Medium, 14-16sp
- **Captions**: Medium, 10-12sp
- **Station sequence numbers**: SemiBold, 36sp
- **No letter spacing adjustments** except sequence numbers (-0.1sp)

## Shape

- **Cards**: RoundedCornerShape(12dp)
- **Date circles**: CircleShape (48dp)
- **Zone pills**: RoundedCornerShape(50%) — fully rounded
- **Bottom sheet**: RoundedCornerShape(24dp top, animated to 0dp when expanded)
- **Buttons**: RoundedCornerShape(12dp)
- **Search bar**: RoundedCornerShape(20dp)
- **Map container**: RoundedCornerShape(12dp) with clipToBounds

## Iconography

- Material Icons Extended (full set)
- Key icons: Train (stations), Place (POIs/navigation), Image (photo count), Warning (defect count), AutoGraph (optimize)
- Custom: Numbered teardrop map markers (80×120px, purple fill, white number text)
- Custom: Direction arrows on polyline (24×24px white triangles, every 2000m)

## Map Configuration

- **Tile source**: Mapy.cz (256px PNG tiles)
- **Bounds**: Czech Republic (48.55°–51.05°N, 12.09°–18.86°E)
- **Min zoom**: 7
- **Max zoom**: 19
- **Route line**: Dark outline (16px, #444) + purple main (8px, #8B5CF6) with direction arrows

## Interaction Patterns

- **No ripple** on settings buttons and shutter buttons (custom `clickableNoRipple` modifier)
- **Swipe-to-dismiss** on map list items (stations: toggle hide, POIs: delete)
- **Long-press drag** for reordering in expanded bottom sheet
- **Pinch-to-zoom** on camera preview and map
- **White flash overlay** (80ms) on photo capture

---

## File Counts (approximate — verify against source)

| Category | Count |
|---|---|
| Kotlin source files (main) | ~70 |
| Room entities | 6 (5 tables + 1 result class `StationWithSplitCounts`) |
| Room DAOs | 5 |
| ViewModels | 6 (Route/Search/Shortcuts/Settings + ZoneInspection + Export) |
| Use cases | 3 (StationNameCleaner, ParseStationsCsv, ImportStations) |
| DI modules | 4 (App, Database, Network, Dispatchers) |
| Hilt workers | 1 |
| DB migrations | 8 (`exportSchema = true`) |
| API services | 2 (ORS + Mapy.cz) |
| Unit tests | ~39 (incl. Robolectric Room + migration + VM) |
