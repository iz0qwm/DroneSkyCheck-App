# Android Map Prototype

## Scope

This prototype is the first native Android map screen for Drone Sky Check. It loads cartographic GeoJSON from the KWOS static mirror and calls `zoneCheckV3` on map taps for the operational verdict. It still does not use Firebase or GPS.

## MapLibre Version

The app uses MapLibre Native Android `org.maplibre.gl:android-sdk:13.4.0`.

The version was selected from Maven Central as the current stable Android artifact available for the MapLibre Native package. The official MapLibre Android quickstart documents the same dependency coordinate and the `MapView` integration model.

The temporary basemap style is OpenFreeMap Liberty:

`https://tiles.openfreemap.org/styles/liberty`

This style is public, works with MapLibre Native, does not require an API key, and includes roads, city/locality labels and geographic references. It is still for development only and is not the final Drone Sky Check basemap.

## Structure

- `app/src/main/java/it/droneskycheck/app/ui/map/MapScreen.kt`: Compose screen, title pill and V3 verdict bottom sheet.
- `app/src/main/java/it/droneskycheck/app/ui/map/MapViewModel.kt`: UI state holder for selected point, selected feature, V3 verdict and camera bounds.
- `app/src/main/java/it/droneskycheck/app/ui/map/MapUiState.kt`: simple state models.
- `app/src/main/java/it/droneskycheck/app/ui/map/DemoZoneStatus.kt`: temporary local mapping from `lowerLimit` to display status.
- `app/src/main/java/it/droneskycheck/app/map/DroneSkyMapView.kt`: native MapLibre `MapView` embedded in Compose through `AndroidView`.
- `app/src/main/java/it/droneskycheck/app/map/MapLayerIds.kt`: style URL, KWOS mirror URL and static split layer list.
- `app/src/main/java/it/droneskycheck/app/map/DscZoneMapColors.kt`: centralized Drone Sky Check zone colors aligned to the webapp scale.
- `app/src/main/java/it/droneskycheck/app/data/DscApiConfig.kt`: temporary V3 endpoint and API key.
- `app/src/main/java/it/droneskycheck/app/data/ZoneCheckV3Repository.kt`: minimal V3 HTTP client and DTO parser.
- `app/src/main/java/it/droneskycheck/app/data/ZoneCheckV3Models.kt`: native DTO subset used by the bottom sheet.
- `app/src/main/assets/sample_dsc_zones.geojson`: local demo GeoJSON.

## Static KWOS GeoJSON

The app no longer uses `/zones` for map geometries. It loads static GeoJSON
from:

`https://www.kwos.org/appoggio/droni/DroneSkyCheck/`

The current layer list points to `split/*.geojson` files under that mirror.

## Local GeoJSON Fixture

The local `sample_dsc_zones.geojson` remains only as a fixture/prototype asset.

The file contains artificial polygons around Rome with properties compatible with the documented Drone Sky Check zone contract:

- `id`
- `name`
- `type`
- `restriction`
- `lowerLimit`
- `upperLimit`
- `description`

These are visual test features only, not real aeronautical zones.

## Source And Layers

The MapLibre rendering model is:

`GeoJsonSource -> FillLayer -> LineLayer`

Each static split file has its own source, fill layer and line layer generated
from `MapLayerIds.STATIC_LAYERS`.

Fill and line colors are data-driven from `lowerLimit`:

- `0`: red, webapp value `#ff3b30`
- `25`: orange, webapp value `#ff9500`
- `45`: yellow, webapp value `#ffcc00` supported by the shared color table even though the current demo asset does not contain a 45 m polygon
- `60`: blue/cyan, webapp value `#5ac8fa`

The fill is semitransparent so the basemap remains readable.

There is no `120 m` polygon. `120 m` represents the base condition and is not rendered as a map zone.

## Tap Handling

The map registers an `OnMapClickListener`. On tap, the native map converts the tapped `LatLng` to screen coordinates and calls `queryRenderedFeatures` against a small screen-space rectangle around the touch point. The query is scoped to all configured static fill and line layers.

The selected feature properties, if any, are converted to `DemoZone`. The tapped
coordinates are always sent to `zoneCheckV3`; this allows a verdict even when no
rendered feature is selected at that exact touch point.

The bottom sheet shows:

- `zoneCheckV3` status and explanation
- maximum allowed limit from backend
- blocker/warning codes
- engine/version
- selected cartographic feature, if present
- queried point

## Temporary Status Mapping

The status is derived only from local demo `lowerLimit`:

- `0 m`: `NON CONSENTITO`
- `1..119 m`: `LIMITATO`
- `120 m` or more: `APERTO`, only as a defensive fallback if unexpected local data reaches the sheet

No demo polygon uses `120 m`, because `120 m` is the base condition and should not be represented as a colored zone. This mapping is now only a defensive fallback for cartographic feature display. The operational verdict comes from backend `zoneCheckV3`.

## Camera Idle And Future Bbox

The map registers `OnCameraIdleListener`, not continuous movement callbacks. On camera idle it captures:

- zoom
- north
- south
- east
- west

It also exposes the future API-ready bbox shape:

`bbox=minLat,minLon,maxLat,maxLon`

For this prototype the value is only stored in UI state and logged with tag `DroneSkyMap`. Geometry loading comes from static KWOS GeoJSON rather than bbox `/zones` calls.

## MapView Lifecycle

`MapView` is created once with `remember` inside Compose and embedded with `AndroidView`, so recomposition does not recreate it.

Lifecycle forwarding is handled with a `LifecycleEventObserver`:

- `ON_START -> mapView.onStart()`
- `ON_RESUME -> mapView.onResume()`
- `ON_PAUSE -> mapView.onPause()`
- `ON_STOP -> mapView.onStop()`
- `ON_DESTROY -> mapView.onDestroy()`

Low memory is forwarded through Android `ComponentCallbacks`.

## Temporary Pieces

The following pieces are intentionally temporary:

- OpenFreeMap Liberty style URL
- local artificial GeoJSON fixture
- hardcoded V3 API key in `DscApiConfig`
- Logcat-only bbox reporting

Future API/security work should replace the temporary key with App Check, Play
Integrity, Firebase Auth or an equivalent mobile-safe mechanism.
