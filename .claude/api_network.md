# Networking & APIs

## API 1: OpenRouteService (ORS)

- **Base URL**: `https://api.openrouteservice.org/`
- **Auth**: API key in `Authorization` header (Base64 string hardcoded in `NetworkModule.kt`)
- **Timeouts**: connect 45s, read 45s
- **DI**: `NetworkModule` → OkHttpClient → Retrofit → `OrsApiService`

### Endpoints

| Method | Path | DTO | Purpose |
|---|---|---|---|
| `POST` | `v2/directions/driving-car` | `DirectionsRequest` → `DirectionsResponse` | Driving directions with encoded polyline geometry |
| `POST` | `optimization` | `OptimizationRequest` → `OptimizationResponse` | VROOM-based route optimization (TSP solver) |
| `GET` | `v2/geocode/search?text=` | — → `GeocodeResponse` | Geocode station names to lat/lon coordinates |

### DTOs

- `DirectionsRequest` — `coordinates: List<List<Double>>` (lon,lat pairs)
- `DirectionsResponse` → `RouteDto` → `RouteSummaryDto` (distance, duration) + geometry string
- `OptimizationRequest` — `jobs: List<JobDto>` + `vehicles: List<VehicleDto>`
- `OptimizationResponse` → `OptRouteDto` → `OptStepDto` (type: start/job/end, id, distance, duration)
- `GeocodeResponse` → `GeocodeFeature` → `GeocodeGeometry` (coordinates: lon,lat)

**Note**: ORS uses [longitude, latitude] coordinate order (GeoJSON convention).

---

## API 2: Mapy.cz Geocoding

- **Base URL**: `https://api.mapy.cz/`
- **Auth**: API key as `apikey` query parameter (from `local.properties` → `BuildConfig.MAPY_CZ_API_KEY`)
- **DI**: `AppModule` → separate OkHttpClient (auth interceptor) → Retrofit → `MapyCzApi`

### Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `v1/geocode?query=` | Location search for adding POIs to routes |

### Response Structure

```
MapyCzResponse
  └── items: List<MapyCzItem>
        ├── name: String
        ├── label: String?
        ├── position: MapyCzPosition (lon, lat)
        └── regionalStructure: List<MapyCzRegion>
              ├── type: "regional.municipality" → city
              ├── type: "regional.street" → street
              ├── type: "regional.address" → house number
              └── type: "regional.region" / "regional.district" → region
```

---

## API 3: Mapy.cz Map Tiles

- **URL pattern**: `https://api.mapy.cz/v1/maptiles/basic/256/{z}/{x}/{y}?apikey=...`
- **Auth**: API key hardcoded directly in `MapWidget.kt`
- **Consumer**: osmdroid's custom `OnlineTileSourceBase`
- **Zoom range**: 0–19
- **Tile size**: 256px PNG

### Map Configuration (osmdroid)

- Default center: 49.8175°N, 15.4730°E (Czechia)
- Default zoom: 8
- Scroll bounds: N 51.05 / S 48.55 / W 12.09 / E 18.86 (Czech Republic)
- Min zoom: 7
- Multi-touch zoom: enabled
- Built-in zoom controls: disabled
