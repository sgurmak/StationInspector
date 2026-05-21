# Design System & File Counts

## Color Palette

| Token | Hex | Usage |
|---|---|---|
| `AppGradientTop` | `#392153` | Background gradient start |
| `AppGradientBottom` | `#13111A` | Background gradient end |
| `NavBarBg` | `#13111B` | Bottom nav bar, sheet backgrounds |
| `ContentLight` | `#FBF7FF` | Primary text/icons on dark backgrounds |
| `CardContent` / `ContentDark` | `#261937` | Text/icons on light card backgrounds |
| `CardBg` | `#FBF7FF` | Station/POI card backgrounds |
| `AccentRed` / `WarningRed` | `#CA065E` | Defect counts, destructive actions, accent color |
| `AccentGreen` | `#4ADE80` | Confirmation indicators, done buttons |
| `SuccessGreen` | `#16A34A` | Snackbar success, confirm inspection button |
| `RoutePurple` | `#8B5CF6` | Map polyline, numbered marker pins |
| `DateCircleSel` | `#261937` | Selected date circle background |
| `DateCircleUnsel` | `#FBF7FF` | Unselected date circle background |

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

## File Counts

| Category | Count |
|---|---|
| Kotlin source files | 53 |
| Room entities | 7 (5 tables + 2 result classes) |
| Room DAOs | 5 |
| ViewModels | 4 |
| Composable screens | 10 |
| DI modules | 3 |
| Hilt workers | 1 |
| DB migrations | 8 |
| API services | 2 (ORS + Mapy.cz) |
| Network DTOs | 10 |
