package it.droneskycheck.app.map

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.droneskycheck.app.ui.map.CameraBounds
import it.droneskycheck.app.ui.map.DemoZone
import java.net.URI
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature

@Composable
fun DroneSkyMapView(
    onZoneTapped: (DemoZone) -> Unit,
    onCameraIdle: (CameraBounds) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
            getMapAsync { map ->
                configureMap(map, onZoneTapped, onCameraIdle)
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        var isDestroyed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    if (!isDestroyed) {
                        mapView.onDestroy()
                        isDestroyed = true
                    }
                }
                Lifecycle.Event.ON_CREATE, Lifecycle.Event.ON_ANY -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            override fun onLowMemory() {
                mapView.onLowMemory()
            }
        }
        context.applicationContext.registerComponentCallbacks(callbacks)

        onDispose {
            context.applicationContext.unregisterComponentCallbacks(callbacks)
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!isDestroyed) {
                mapView.onDestroy()
                isDestroyed = true
            }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}

private fun configureMap(
    map: MapLibreMap,
    onZoneTapped: (DemoZone) -> Unit,
    onCameraIdle: (CameraBounds) -> Unit
) {
    map.cameraPosition = CameraPosition.Builder()
        .target(LatLng(ROME_LATITUDE, ROME_LONGITUDE))
        .zoom(ROME_ZOOM)
        .build()

    map.setStyle(
        Style.Builder()
            .fromUri(MapLayerIds.STYLE_URL)
            .withSource(
                GeoJsonSource(
                    MapLayerIds.ZONES_SOURCE_ID,
                    URI("asset://${MapLayerIds.SAMPLE_ZONES_ASSET}")
                )
            )
            .withLayer(
                FillLayer(
                    MapLayerIds.ZONES_FILL_LAYER_ID,
                    MapLayerIds.ZONES_SOURCE_ID
                ).withProperties(
                    fillColor(zoneFillColorExpression()),
                    fillOpacity(0.42f)
                )
            )
            .withLayer(
                LineLayer(
                    MapLayerIds.ZONES_LINE_LAYER_ID,
                    MapLayerIds.ZONES_SOURCE_ID
                ).withProperties(
                    lineColor(zoneLineColorExpression()),
                    lineOpacity(0.92f),
                    lineWidth(2.2f)
                )
            )
    ) {
        map.addOnMapClickListener { latLng ->
            val selectedZone = map.queryRenderedFeatures(
                pointForLatLng(map, latLng),
                MapLayerIds.ZONES_FILL_LAYER_ID
            ).firstOrNull()?.let(::featureToDemoZone)

            if (selectedZone != null) {
                onZoneTapped(selectedZone)
                true
            } else {
                false
            }
        }

        map.addOnCameraIdleListener {
            val bounds = map.projection.visibleRegion.latLngBounds
            val cameraBounds = CameraBounds(
                zoom = map.cameraPosition.zoom,
                north = bounds.latitudeNorth,
                south = bounds.latitudeSouth,
                east = bounds.longitudeEast,
                west = bounds.longitudeWest
            )
            Log.d(
                LOG_TAG,
                "camera idle zoom=${cameraBounds.zoom}, bbox=${cameraBounds.bbox}"
            )
            onCameraIdle(cameraBounds)
        }

        Log.d(LOG_TAG, "Loaded local demo GeoJSON from assets/${MapLayerIds.SAMPLE_ZONES_ASSET}")
    }
}

private fun pointForLatLng(map: MapLibreMap, latLng: LatLng): PointF =
    map.projection.toScreenLocation(latLng)

private fun featureToDemoZone(feature: Feature): DemoZone {
    val properties = feature.properties()
    return DemoZone(
        id = properties?.get("id")?.asString.orEmpty(),
        name = properties?.get("name")?.asString ?: "Zona senza nome",
        type = properties?.get("type")?.asString ?: "UNKNOWN",
        restriction = properties?.get("restriction")?.takeIf { !it.isJsonNull }?.asString,
        lowerLimit = properties?.get("lowerLimit")?.asInt ?: 120,
        upperLimit = properties?.get("upperLimit")?.takeIf { !it.isJsonNull }?.asInt
    )
}

private fun zoneFillColorExpression(): Expression =
    Expression.match(
        Expression.get("lowerLimit"),
        Expression.literal(0),
        Expression.rgba(198.0f, 40.0f, 40.0f, 1.0f),
        Expression.literal(60),
        Expression.rgba(245.0f, 124.0f, 0.0f, 1.0f),
        Expression.literal(120),
        Expression.rgba(38.0f, 166.0f, 154.0f, 1.0f),
        Expression.rgba(96.0f, 125.0f, 139.0f, 1.0f)
    )

private fun zoneLineColorExpression(): Expression =
    Expression.match(
        Expression.get("lowerLimit"),
        Expression.literal(0),
        Expression.rgba(127.0f, 29.0f, 29.0f, 1.0f),
        Expression.literal(60),
        Expression.rgba(180.0f, 83.0f, 9.0f, 1.0f),
        Expression.literal(120),
        Expression.rgba(13.0f, 148.0f, 136.0f, 1.0f),
        Expression.rgba(69.0f, 90.0f, 100.0f, 1.0f)
    )

private const val ROME_LATITUDE = 41.9028
private const val ROME_LONGITUDE = 12.4964
private const val ROME_ZOOM = 10.0
private const val LOG_TAG = "DroneSkyMap"
