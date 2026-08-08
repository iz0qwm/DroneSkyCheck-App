# Android Map Prototype

## Scope

This prototype is the first native Android map screen for Drone Sky Check. It does not call Drone Sky Check APIs, does not use Firebase, does not use authentication, does not access GPS, and does not contain API keys.

## MapLibre Version

The app uses MapLibre Native Android `org.maplibre.gl:android-sdk:13.4.0`.

The version was selected from Maven Central as the current stable Android artifact available for the MapLibre Native package. The official MapLibre Android quickstart documents the same dependency coordinate and the `MapView` integration model.

The temporary basemap style is:

`https://demotiles.maplibre.org/style.json`

This style is for development only and is not the final Drone Sky Check basemap.

## Structure

- `app/src/main/java/it/droneskycheck/app/ui/map/MapScreen.kt`: Compose screen, title pill and bottom sheet.
- `app/src/main/java/it/droneskycheck/app/ui/map/MapViewModel.kt`: UI state holder for selected zone and camera bounds.
- `app/src/main/java/it/droneskycheck/app/ui/map/MapUiState.kt`: simple state models.
- `app/src/main/java/it/droneskycheck/app/ui/map/DemoZoneStatus.kt`: temporary local mapping from `lowerLimit` to display status.
- `app/src/main/java/it/droneskycheck/app/map/DroneSkyMapView.kt`: native MapLibre `MapView` embedded in Compose through `AndroidView`.
- `app/src/main/java/it/droneskycheck/app/map/MapLayerIds.kt`: source, layer, asset and style constants.
- `app/src/main/assets/sample_dsc_zones.geojson`: local demo GeoJSON.

## Local GeoJSON

The prototype loads `sample_dsc_zones.geojson` from app assets using:

`GeoJsonSource("dsc-sample-zones-source", URI("asset://sample_dsc_zones.geojson"))`

The file contains artificial polygons around Rome with properties compatible with the documented Drone Sky Check zone contract:

- `id`
- `name`
- `type`
- `restriction`
- `lowerLimit`
- `upperLimit`

These are visual test features only, not real aeronautical zones.

## Source And Layers

The MapLibre rendering model is:

`GeoJsonSource -> FillLayer -> LineLayer`

The current IDs are:

- source: `dsc-sample-zones-source`
- fill layer: `dsc-sample-zones-fill`
- line layer: `dsc-sample-zones-line`

Fill and line colors are data-driven from `lowerLimit`:

- `0`: restrictive red
- `60`: limited orange
- `120`: neutral teal

The fill is semitransparent so the basemap remains readable.

## Tap Handling

The map registers an `OnMapClickListener`. On tap, the native map converts the tapped `LatLng` to screen coordinates and calls `queryRenderedFeatures` against the fill layer only.

The selected feature properties are converted to `DemoZone` and pushed into Compose state. `MapScreen` then opens a Material 3 `ModalBottomSheet`.

The bottom sheet shows:

- zone name
- temporary local status
- quota
- zone type
- restriction
- upper limit when present
- `Zona dimostrativa locale`

## Temporary Status Mapping

The status is derived only from local demo `lowerLimit`:

- `0 m`: `NON CONSENTITO`
- `1..119 m`: `LIMITATO`
- `120 m` or more: `APERTO`

This is not an Android decision engine. When the real API integration arrives, the operational verdict must come from backend `zoneCheckV2`.

## Camera Idle And Future Bbox

The map registers `OnCameraIdleListener`, not continuous movement callbacks. On camera idle it captures:

- zoom
- north
- south
- east
- west

It also exposes the future API-ready bbox shape:

`bbox=minLat,minLon,maxLat,maxLon`

For this prototype the value is only stored in UI state and logged with tag `DroneSkyMap`. No HTTP request is made.

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

- public demo style URL
- local artificial GeoJSON
- color palette
- local `lowerLimit` display mapping
- Logcat-only bbox reporting

Future API work should replace the local data with backend-provided zones and operational verdicts from `zoneCheckV2`.
