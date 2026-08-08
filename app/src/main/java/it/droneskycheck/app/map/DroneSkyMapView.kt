package it.droneskycheck.app.map

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
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
import it.droneskycheck.app.ui.map.MapPoint
import it.droneskycheck.app.ui.map.MapTapSelection
import java.net.URI
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.fillPattern
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature

@Composable
fun DroneSkyMapView(
    onMapTapped: (MapTapSelection) -> Unit,
    onCameraIdle: (CameraBounds) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
            getMapAsync { map ->
                configureMap(map, onMapTapped, onCameraIdle)
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
    onMapTapped: (MapTapSelection) -> Unit,
    onCameraIdle: (CameraBounds) -> Unit
) {
    map.cameraPosition = CameraPosition.Builder()
        .target(LatLng(ROME_LATITUDE, ROME_LONGITUDE))
        .zoom(ROME_ZOOM)
        .build()

    val styleBuilder = Style.Builder().fromUri(MapLayerIds.STYLE_URL)
    MapLayerIds.STATIC_LAYERS.forEach { layer ->
        styleBuilder
            .withSource(GeoJsonSource(layer.sourceId, URI(layer.url)))
            .withLayer(
                FillLayer(
                    layer.fillLayerId,
                    layer.sourceId
                ).withProperties(
                    fillColor(zoneFillColorExpression()),
                    fillOpacity(DscZoneMapColors.fillOpacityExpression(layer))
                ).withZoomRange(layer)
            )
        if (layer.usesZebraPattern) {
            styleBuilder.withLayer(
                FillLayer(
                    layer.zebraLayerId,
                    layer.sourceId
                ).withProperties(
                    fillPattern(NOTAM_ZEBRA_PATTERN_ID),
                    fillOpacity(NOTAM_ZEBRA_OPACITY)
                ).withZoomRange(layer)
            )
        }
        styleBuilder
            .withLayer(
                LineLayer(
                    layer.lineLayerId,
                    layer.sourceId
                ).withProperties(
                    lineColor(zoneLineColorExpression()),
                    lineOpacity(DscZoneMapColors.lineOpacityExpression()),
                    lineWidth(layer.lineWidth)
                ).withZoomRange(layer)
            )
    }

    map.setStyle(styleBuilder) {
        it.addImage(NOTAM_ZEBRA_PATTERN_ID, createNotamZebraPattern())

        map.addOnMapClickListener { latLng ->
            val selectedZone = map.queryRenderedFeatures(
                touchAreaForLatLng(map, latLng),
                *interactiveLayerIds()
            ).firstOrNull()?.let(::featureToDemoZone)

            onMapTapped(
                MapTapSelection(
                    point = MapPoint(lat = latLng.latitude, lon = latLng.longitude),
                    zone = selectedZone
                )
            )
            true
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

        Log.d(LOG_TAG, "Loaded DSC static GeoJSON layers from ${MapLayerIds.KWOS_DATA_BASE_URL}")
    }
}

private fun <T : Layer> T.withZoomRange(layer: DscMapLayer): T {
    minZoom = layer.minZoom
    return this
}

private fun touchAreaForLatLng(map: MapLibreMap, latLng: LatLng): RectF {
    val center: PointF = map.projection.toScreenLocation(latLng)
    return RectF(
        center.x - TAP_HIT_SLOP_PX,
        center.y - TAP_HIT_SLOP_PX,
        center.x + TAP_HIT_SLOP_PX,
        center.y + TAP_HIT_SLOP_PX
    )
}

private fun featureToDemoZone(feature: Feature): DemoZone {
    val properties = feature.properties()
    return DemoZone(
        id = properties?.stringValue("id")
            ?: properties?.stringValue("identifier")
            ?: "",
        name = properties?.stringValue("name") ?: "Zona senza nome",
        type = properties?.stringValue("_splitType")
            ?: properties?.stringValue("type")
            ?: "UNKNOWN",
        restriction = properties?.stringValue("restriction"),
        lowerLimit = properties?.intValue("lowerLimit")
            ?: properties?.intValue("lowerlimit")
            ?: 120,
        upperLimit = properties?.intValue("upperLimit")
            ?: properties?.intValue("upperlimit"),
        description = properties?.stringValue("description")
            ?: properties?.stringValue("message")
    )
}

private fun interactiveLayerIds(): Array<String> =
    MapLayerIds.STATIC_LAYERS
        .flatMap { layer ->
            buildList {
                add(layer.fillLayerId)
                if (layer.usesZebraPattern) add(layer.zebraLayerId)
                add(layer.lineLayerId)
            }
        }
        .toTypedArray()

private fun createNotamZebraPattern(): Bitmap {
    val bitmap = Bitmap.createBitmap(NOTAM_ZEBRA_SIZE_PX, NOTAM_ZEBRA_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(DscZoneMapColors.noFly0m.webHex)
        strokeWidth = NOTAM_ZEBRA_STROKE_PX
    }

    for (offset in -NOTAM_ZEBRA_SIZE_PX..NOTAM_ZEBRA_SIZE_PX step NOTAM_ZEBRA_STEP_PX) {
        canvas.drawLine(
            offset.toFloat(),
            NOTAM_ZEBRA_SIZE_PX.toFloat(),
            (offset + NOTAM_ZEBRA_SIZE_PX).toFloat(),
            0f,
            paint
        )
    }
    return bitmap
}

private fun com.google.gson.JsonObject.stringValue(key: String): String? =
    get(key)
        ?.takeIf { !it.isJsonNull }
        ?.let { value ->
            runCatching { value.asString }.getOrNull()
        }
        ?.takeIf { it.isNotBlank() }

private fun com.google.gson.JsonObject.intValue(key: String): Int? =
    get(key)
        ?.takeIf { !it.isJsonNull }
        ?.let { value ->
            when {
                value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> value.asInt
                else -> value.asString.toDoubleOrNull()?.toInt()
            }
        }

private fun zoneFillColorExpression(): Expression =
    DscZoneMapColors.fillExpression()

private fun zoneLineColorExpression(): Expression =
    DscZoneMapColors.lineExpression()

private const val ROME_LATITUDE = 41.9028
private const val ROME_LONGITUDE = 12.4964
private const val ROME_ZOOM = 10.0
private const val TAP_HIT_SLOP_PX = 18.0f
private const val NOTAM_ZEBRA_PATTERN_ID = "dsc-notam-zebra"
private const val NOTAM_ZEBRA_SIZE_PX = 16
private const val NOTAM_ZEBRA_STEP_PX = 8
private const val NOTAM_ZEBRA_STROKE_PX = 2.5f
private const val NOTAM_ZEBRA_OPACITY = 0.22f
private const val LOG_TAG = "DroneSkyMap"
