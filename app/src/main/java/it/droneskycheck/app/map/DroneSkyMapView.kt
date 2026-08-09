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
import android.os.Handler
import android.os.Looper
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
import it.droneskycheck.app.data.CachedGeoJsonRepository
import it.droneskycheck.app.data.ZonesRepository
import it.droneskycheck.app.ui.map.CameraBounds
import it.droneskycheck.app.ui.map.DemoZone
import it.droneskycheck.app.ui.map.MapPoint
import it.droneskycheck.app.ui.map.MapTapSelection
import it.droneskycheck.app.ui.map.UserLocation
import java.util.concurrent.Executors
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleBlur
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.fillPattern
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

@Composable
fun DroneSkyMapView(
    visibleLayerCategories: Set<DscLayerCategory>,
    selectedPoint: MapPoint?,
    authorizationTakeoff: MapPoint?,
    authorizationAreaPoints: List<MapPoint>,
    authorizationAreaClosed: Boolean,
    userLocation: UserLocation?,
    shouldCenterOnUserLocation: Boolean,
    onUserLocationCentered: () -> Unit,
    onMapTapped: (MapTapSelection) -> Unit,
    onCameraIdle: (CameraBounds) -> Unit,
    onMapDataDegraded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
            getMapAsync { map ->
                configureMap(this, map, visibleLayerCategories, onMapTapped, onCameraIdle, onMapDataDegraded)
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
        update = { view ->
            view.getMapAsync { map ->
                map.getStyle { style ->
                    applyLayerVisibility(style, visibleLayerCategories)
                    loadDynamicZonesSources(
                        zonesRepository = ZonesRepository(view.context.applicationContext),
                        style = style,
                        cameraBounds = map.currentCameraBounds(),
                        visibleLayerCategories = visibleLayerCategories,
                        onMapDataDegraded = onMapDataDegraded
                    )
                    updatePointMarkers(style, selectedPoint, userLocation)
                    updateAuthorizationDrawing(style, authorizationTakeoff, authorizationAreaPoints, authorizationAreaClosed)
                    if (shouldCenterOnUserLocation && userLocation != null) {
                        centerOnUserLocation(map, userLocation)
                        onUserLocationCentered()
                    }
                }
            }
        },
        modifier = modifier
    )
}

private fun configureMap(
    mapView: MapView,
    map: MapLibreMap,
    visibleLayerCategories: Set<DscLayerCategory>,
    onMapTapped: (MapTapSelection) -> Unit,
    onCameraIdle: (CameraBounds) -> Unit,
    onMapDataDegraded: () -> Unit
) {
    map.cameraPosition = CameraPosition.Builder()
        .target(LatLng(ROME_LATITUDE, ROME_LONGITUDE))
        .zoom(ROME_ZOOM)
        .build()

    val styleBuilder = Style.Builder().fromUri(MapLayerIds.STYLE_URL)
    val geoJsonRepository = CachedGeoJsonRepository(mapView.context.applicationContext)
    val zonesRepository = ZonesRepository(mapView.context.applicationContext)

    map.setStyle(styleBuilder) {
        it.addImage(NOTAM_ZEBRA_PATTERN_ID, createNotamZebraPattern())
        addDscLayers(it)
        addPointMarkerLayers(it)
        updatePointMarkers(it, null, null)
        loadDscGeoJsonSources(geoJsonRepository, it, onMapDataDegraded)
        loadDynamicZonesSources(
            zonesRepository,
            it,
            map.currentCameraBounds(),
            visibleLayerCategories,
            onMapDataDegraded
        )
        applyLayerVisibility(it, visibleLayerCategories)
        logFocusLayerState(mapView, map, it, visibleLayerCategories, map.cameraPosition.zoom)

        map.addOnMapClickListener { latLng ->
            val zones = map.queryRenderedFeatures(
                touchAreaForLatLng(map, latLng),
                *interactiveLayerIds()
            ).map(::featureToDemoZone)
                .distinctBy { zone -> zone.identityKey() }
            val selectedZone = zones.firstOrNull()

            onMapTapped(
                MapTapSelection(
                    point = MapPoint(lat = latLng.latitude, lon = latLng.longitude),
                    zone = selectedZone,
                    zones = zones
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
            map.getStyle { style ->
                loadDynamicZonesSources(
                    zonesRepository,
                    style,
                    cameraBounds,
                    visibleLayerCategories,
                    onMapDataDegraded
                )
                logFocusLayerState(mapView, map, style, visibleLayerCategories, cameraBounds.zoom)
            }
        }

        Log.d(LOG_TAG, "Loaded DSC static GeoJSON layers from ${MapLayerIds.KWOS_DATA_BASE_URL}")
    }
}

private fun addPointMarkerLayers(style: Style) {
    style.addSource(GeoJsonSource(SELECTED_POINT_SOURCE_ID, emptyFeatureCollection()))
    style.addSource(GeoJsonSource(USER_LOCATION_SOURCE_ID, emptyFeatureCollection()))
    style.addSource(GeoJsonSource(AUTH_TAKEOFF_SOURCE_ID, emptyFeatureCollection()))
    style.addSource(GeoJsonSource(AUTH_AREA_VERTICES_SOURCE_ID, emptyFeatureCollection()))
    style.addSource(GeoJsonSource(AUTH_AREA_LINE_SOURCE_ID, emptyFeatureCollection()))
    style.addSource(GeoJsonSource(AUTH_AREA_FILL_SOURCE_ID, emptyFeatureCollection()))

    style.addLayer(
        CircleLayer(SELECTED_POINT_RING_LAYER_ID, SELECTED_POINT_SOURCE_ID)
            .withProperties(
                circleRadius(14.0f),
                circleColor("#ffffff"),
                circleOpacity(0.82f),
                circleStrokeColor(DscZoneMapColors.noFly0m.webHex),
                circleStrokeWidth(3.0f),
                circleStrokeOpacity(0.98f)
            )
    )
    style.addLayer(
        CircleLayer(SELECTED_POINT_DOT_LAYER_ID, SELECTED_POINT_SOURCE_ID)
            .withProperties(
                circleRadius(4.0f),
                circleColor(DscZoneMapColors.noFly0m.webHex),
                circleStrokeColor("#ffffff"),
                circleStrokeWidth(1.4f)
            )
    )

    style.addLayer(
        FillLayer(AUTH_AREA_FILL_LAYER_ID, AUTH_AREA_FILL_SOURCE_ID)
            .withProperties(
                fillColor("#00bcd4"),
                fillOpacity(0.18f)
            )
    )
    style.addLayer(
        LineLayer(AUTH_AREA_LINE_LAYER_ID, AUTH_AREA_LINE_SOURCE_ID)
            .withProperties(
                lineColor("#00bcd4"),
                lineWidth(3.0f),
                lineOpacity(0.92f)
            )
    )
    style.addLayer(
        CircleLayer(AUTH_AREA_VERTICES_LAYER_ID, AUTH_AREA_VERTICES_SOURCE_ID)
            .withProperties(
                circleRadius(6.0f),
                circleColor("#00bcd4"),
                circleStrokeColor("#ffffff"),
                circleStrokeWidth(2.0f)
            )
    )
    style.addLayer(
        CircleLayer(AUTH_TAKEOFF_RING_LAYER_ID, AUTH_TAKEOFF_SOURCE_ID)
            .withProperties(
                circleRadius(16.0f),
                circleColor("#1b5e20"),
                circleOpacity(0.22f),
                circleStrokeColor("#ffffff"),
                circleStrokeWidth(2.5f)
            )
    )
    style.addLayer(
        CircleLayer(AUTH_TAKEOFF_DOT_LAYER_ID, AUTH_TAKEOFF_SOURCE_ID)
            .withProperties(
                circleRadius(6.0f),
                circleColor("#1b5e20"),
                circleStrokeColor("#ffffff"),
                circleStrokeWidth(2.0f)
            )
    )
    style.addLayer(
        CircleLayer(USER_LOCATION_ACCURACY_LAYER_ID, USER_LOCATION_SOURCE_ID)
            .withProperties(
                circleRadius(userAccuracyRadiusExpression()),
                circleColor("#1e88e5"),
                circleOpacity(0.16f),
                circleStrokeColor("#1e88e5"),
                circleStrokeOpacity(0.34f),
                circleStrokeWidth(1.0f)
            )
    )
    style.addLayer(
        CircleLayer(USER_LOCATION_DOT_LAYER_ID, USER_LOCATION_SOURCE_ID)
            .withProperties(
                circleRadius(8.0f),
                circleColor("#1e88e5"),
                circleStrokeColor("#ffffff"),
                circleStrokeWidth(3.0f)
            )
    )
    style.addLayer(
        CircleLayer(USER_LOCATION_PULSE_LAYER_ID, USER_LOCATION_SOURCE_ID)
            .withProperties(
                circleRadius(15.0f),
                circleColor("#1e88e5"),
                circleOpacity(0.0f),
                circleStrokeColor("#1e88e5"),
                circleStrokeWidth(2.0f),
                circleStrokeOpacity(0.45f),
                circleBlur(0.2f)
            )
    )
}

private fun updateAuthorizationDrawing(
    style: Style,
    takeoff: MapPoint?,
    areaPoints: List<MapPoint>,
    areaClosed: Boolean
) {
    style.setGeoJsonSourceIfAvailable(
        AUTH_TAKEOFF_SOURCE_ID,
        takeoff?.toFeatureCollection() ?: emptyFeatureCollection()
    )

    style.setGeoJsonSourceIfAvailable(
        AUTH_AREA_VERTICES_SOURCE_ID,
        FeatureCollection.fromFeatures(
            areaPoints.map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) }
        )
    )

    style.setGeoJsonSourceIfAvailable(AUTH_AREA_LINE_SOURCE_ID, areaPoints.toLineFeatureCollection(areaClosed))

    style.setGeoJsonSourceIfAvailable(
        AUTH_AREA_FILL_SOURCE_ID,
        if (areaClosed && areaPoints.size >= 3) {
            areaPoints.toPolygonFeatureCollection()
        } else {
            emptyFeatureCollection()
        }
    )
}

private fun updatePointMarkers(
    style: Style,
    selectedPoint: MapPoint?,
    userLocation: UserLocation?
) {
    style.setGeoJsonSourceIfAvailable(
        SELECTED_POINT_SOURCE_ID,
        selectedPoint?.toFeatureCollection() ?: emptyFeatureCollection()
    )
    style.setGeoJsonSourceIfAvailable(
        USER_LOCATION_SOURCE_ID,
        userLocation?.toFeatureCollection() ?: emptyFeatureCollection()
    )
}

private fun centerOnUserLocation(map: MapLibreMap, userLocation: UserLocation) {
    val currentZoom = map.cameraPosition.zoom
    map.animateCamera(
        CameraUpdateFactory.newLatLngZoom(
            LatLng(userLocation.point.lat, userLocation.point.lon),
            maxOf(currentZoom, USER_LOCATION_CENTER_ZOOM)
        )
    )
}

private fun addDscLayers(style: Style) {
    MapLayerIds.STATIC_LAYERS.forEach { layer ->
        logLayerDefinition(layer)
        style.addSource(GeoJsonSource(layer.sourceId))
        style.addLayer(
                FillLayer(
                    layer.fillLayerId,
                    layer.sourceId
                ).withProperties(
                    fillColor(zoneFillColorExpression()),
                    fillOpacity(DscZoneMapColors.fillOpacityExpression(layer))
                ).withZoomRange(layer)
            )
        if (layer.usesZebraPattern) {
            style.addLayer(
                FillLayer(
                    layer.zebraLayerId,
                    layer.sourceId
                ).withProperties(
                    fillPattern(NOTAM_ZEBRA_PATTERN_ID),
                    fillOpacity(NOTAM_ZEBRA_OPACITY)
                ).withZoomRange(layer)
            )
        }
        style.addLayer(
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
    MapLayerIds.DYNAMIC_ZONES_LAYERS.forEach { layer ->
        style.addSource(GeoJsonSource(layer.sourceId, emptyFeatureCollection()))
        style.addLayer(
            FillLayer(
                layer.fillLayerId,
                layer.sourceId
            ).withProperties(
                fillColor(zoneFillColorExpression()),
                fillOpacity(DscZoneMapColors.fillOpacityExpression(layer))
            ).withZoomRange(layer)
        )
        style.addLayer(
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
}

private fun loadDscGeoJsonSources(
    geoJsonRepository: CachedGeoJsonRepository,
    style: Style,
    onMapDataDegraded: () -> Unit
) {
    MapLayerIds.STATIC_LAYERS.forEach { layer ->
        DSC_GEOJSON_EXECUTOR.execute {
            val startedAt = System.currentTimeMillis()
            runCatching {
                val cached = geoJsonRepository.get(layer.url, layer.key)
                cached to FeatureCollection.fromJson(cached.body)
            }.onSuccess { (cached, featureCollection) ->
                val elapsedMs = System.currentTimeMillis() - startedAt
                if (cached.degraded) {
                    MAIN_HANDLER.post(onMapDataDegraded)
                }
                if (layer.category in DIAGNOSTIC_CATEGORIES) {
                    Log.d(
                        LOG_TAG,
                        "[layer-debug] fetch key=${layer.key}, bytes=${cached.body.length}, " +
                            "features=${featureCollection.features()?.size ?: 0}, " +
                            "degraded=${cached.degraded}, elapsedMs=$elapsedMs"
                    )
                }
                MAIN_HANDLER.post {
                    val updated = style.setGeoJsonSourceIfAvailable(layer.sourceId, featureCollection)
                    if (updated && layer.category in DIAGNOSTIC_CATEGORIES) {
                        Log.d(LOG_TAG, "[layer-debug] source populated key=${layer.key}")
                    }
                }
            }.onFailure { error ->
                Log.w(LOG_TAG, "[layer-debug] fetch failed key=${layer.key}, url=${layer.url}", error)
            }
        }
    }
}

private fun loadDynamicZonesSources(
    zonesRepository: ZonesRepository,
    style: Style,
    cameraBounds: CameraBounds,
    visibleLayerCategories: Set<DscLayerCategory>,
    onMapDataDegraded: () -> Unit
) {
    MapLayerIds.DYNAMIC_ZONES_LAYERS
        .filter { layer ->
            layer.category in visibleLayerCategories && cameraBounds.zoom >= layer.minZoom
        }
        .forEach { layer ->
            DSC_GEOJSON_EXECUTOR.execute {
                runCatching {
                    val cached = zonesRepository.getZonesResult(
                        bbox = cameraBounds.bbox,
                        type = layer.zonesType
                    )
                    cached to FeatureCollection.fromJson(cached.body)
                }.onSuccess { (cached, featureCollection) ->
                    if (cached.degraded) {
                        MAIN_HANDLER.post(onMapDataDegraded)
                    }
                    MAIN_HANDLER.post {
                        style.setGeoJsonSourceIfAvailable(layer.sourceId, featureCollection)
                    }
                    Log.d(
                        LOG_TAG,
                        "[zones-api] loaded type=${layer.zonesType}, bbox=${cameraBounds.bbox}, " +
                            "bytes=${cached.body.length}, features=${featureCollection.features()?.size ?: 0}, " +
                            "degraded=${cached.degraded}"
                    )
                }.onFailure { error ->
                    Log.w(LOG_TAG, "[zones-api] fetch failed type=${layer.zonesType}", error)
                }
            }
        }
}

private fun applyLayerVisibility(style: Style, visibleLayerCategories: Set<DscLayerCategory>) {
    MapLayerIds.STATIC_LAYERS.forEach { layer ->
        val nextVisibility = if (layer.category in visibleLayerCategories) {
            Property.VISIBLE
        } else {
            Property.NONE
        }

        style.getLayer(layer.fillLayerId)?.setProperties(visibility(nextVisibility))
        if (layer.usesZebraPattern) {
            style.getLayer(layer.zebraLayerId)?.setProperties(visibility(nextVisibility))
        }
        style.getLayer(layer.lineLayerId)?.setProperties(visibility(nextVisibility))
    }
    MapLayerIds.DYNAMIC_ZONES_LAYERS.forEach { layer ->
        val nextVisibility = if (layer.category in visibleLayerCategories) {
            Property.VISIBLE
        } else {
            Property.NONE
        }

        style.getLayer(layer.fillLayerId)?.setProperties(visibility(nextVisibility))
        style.getLayer(layer.lineLayerId)?.setProperties(visibility(nextVisibility))
    }
}

private fun logLayerDefinition(layer: DscMapLayer) {
    if (layer.category !in DIAGNOSTIC_CATEGORIES) return

    Log.d(
        LOG_TAG,
        "[layer-debug] define key=${layer.key}, category=${layer.category.name}, " +
            "source=${layer.sourceId}, fill=${layer.fillLayerId}, line=${layer.lineLayerId}, " +
            "minZoom=${layer.minZoom}, url=${layer.url}"
    )
}

private fun Style.setGeoJsonSourceIfAvailable(sourceId: String, featureCollection: FeatureCollection): Boolean =
    runCatching {
        getSourceAs<GeoJsonSource>(sourceId)
            ?.setGeoJson(featureCollection) != null
    }.onFailure { error ->
        Log.w(LOG_TAG, "Skipped GeoJSON update for source=$sourceId because the map style is not ready", error)
    }.getOrDefault(false)

private fun logFocusLayerState(
    mapView: MapView,
    map: MapLibreMap,
    style: Style,
    visibleLayerCategories: Set<DscLayerCategory>,
    zoom: Double
) {
    MapLayerIds.STATIC_LAYERS
        .filter { it.category in DIAGNOSTIC_CATEGORIES }
        .forEach { layer ->
            val visibleCategory = layer.category in visibleLayerCategories
            val meetsMinZoom = zoom >= layer.minZoom
            val fillLayerPresent = style.getLayer(layer.fillLayerId) != null
            val lineLayerPresent = style.getLayer(layer.lineLayerId) != null
            val zebraLayerPresent = !layer.usesZebraPattern || style.getLayer(layer.zebraLayerId) != null
            val sourceFeatureCount = countSourceFeatures(style, layer)
            val renderedFeatureCount = countRenderedFeatures(mapView, map, layer)

            Log.d(
                LOG_TAG,
                "[layer-debug] state key=${layer.key}, category=${layer.category.name}, " +
                    "visibleCategory=$visibleCategory, zoom=${"%.2f".format(zoom)}, " +
                    "minZoom=${layer.minZoom}, meetsMinZoom=$meetsMinZoom, " +
                    "fillLayer=$fillLayerPresent, lineLayer=$lineLayerPresent, " +
                    "zebraLayer=$zebraLayerPresent, sourceFeatures=$sourceFeatureCount, " +
                    "renderedFeatures=$renderedFeatureCount"
            )
        }
}

private fun countSourceFeatures(style: Style, layer: DscMapLayer): Int? =
    runCatching {
        style.getSourceAs<GeoJsonSource>(layer.sourceId)
            ?.querySourceFeatures(Expression.literal(true))
            ?.size
    }.onFailure { error ->
        Log.w(LOG_TAG, "[layer-debug] source feature query failed for ${layer.key}", error)
    }.getOrNull()

private fun countRenderedFeatures(
    mapView: MapView,
    map: MapLibreMap,
    layer: DscMapLayer
): Int? {
    val width = mapView.width
    val height = mapView.height
    if (width <= 0 || height <= 0) return null

    return runCatching {
        map.queryRenderedFeatures(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            layer.fillLayerId,
            layer.lineLayerId
        ).size
    }.onFailure { error ->
        Log.w(LOG_TAG, "[layer-debug] rendered feature query failed for ${layer.key}", error)
    }.getOrNull()
}

private fun <T : Layer> T.withZoomRange(layer: DscMapLayer): T {
    minZoom = layer.minZoom
    return this
}

private fun <T : Layer> T.withZoomRange(layer: DscDynamicZonesLayer): T {
    minZoom = layer.minZoom
    return this
}

private fun MapLibreMap.currentCameraBounds(): CameraBounds {
    val bounds = projection.visibleRegion.latLngBounds
    return CameraBounds(
        zoom = cameraPosition.zoom,
        north = bounds.latitudeNorth,
        south = bounds.latitudeSouth,
        east = bounds.longitudeEast,
        west = bounds.longitudeWest
    )
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
            ?: properties?.intValue("lowerLimitAGL")
            ?: properties?.intValue("limitMetersAgl")
            ?: properties?.intValue("maxAltitudeMetersAgl")
            ?: 120,
        upperLimit = properties?.intValue("upperLimit")
            ?: properties?.intValue("upperlimit")
            ?: properties?.intValue("upperLimitAGL"),
        description = properties?.stringValue("description")
            ?: properties?.stringValue("message")
    )
}

private fun DemoZone.identityKey(): String =
    listOf(id, name, type)
        .joinToString("|")
        .lowercase()

private fun emptyFeatureCollection(): FeatureCollection =
    FeatureCollection.fromFeatures(emptyList())

private fun MapPoint.toFeatureCollection(): FeatureCollection =
    FeatureCollection.fromFeature(
        Feature.fromGeometry(Point.fromLngLat(lon, lat))
    )

private fun List<MapPoint>.toLineFeatureCollection(areaClosed: Boolean): FeatureCollection {
    if (size < 2) return emptyFeatureCollection()
    val coordinates = map { Point.fromLngLat(it.lon, it.lat) }
        .let { points -> if (areaClosed && size >= 3) points + points.first() else points }
    return FeatureCollection.fromFeature(
        Feature.fromGeometry(LineString.fromLngLats(coordinates))
    )
}

private fun List<MapPoint>.toPolygonFeatureCollection(): FeatureCollection {
    val ring = map { Point.fromLngLat(it.lon, it.lat) }
        .let { points -> points + points.first() }
    return FeatureCollection.fromFeature(
        Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
    )
}

private fun UserLocation.toFeatureCollection(): FeatureCollection {
    val feature = Feature.fromGeometry(Point.fromLngLat(point.lon, point.lat))
    feature.addBooleanProperty("precise", isPrecise)
    feature.addNumberProperty("accuracyMeters", accuracyMeters ?: DEFAULT_APPROXIMATE_ACCURACY_METERS)
    return FeatureCollection.fromFeature(feature)
}

private fun userAccuracyRadiusExpression(): Expression =
    Expression.interpolate(
        Expression.linear(),
        Expression.get("accuracyMeters"),
        Expression.literal(50),
        Expression.literal(22.0f),
        Expression.literal(500),
        Expression.literal(34.0f),
        Expression.literal(3_000),
        Expression.literal(48.0f)
    )

private fun interactiveLayerIds(): Array<String> =
    (
        MapLayerIds.STATIC_LAYERS.flatMap { layer ->
            buildList {
                add(layer.fillLayerId)
                if (layer.usesZebraPattern) add(layer.zebraLayerId)
                add(layer.lineLayerId)
            }
        } +
            MapLayerIds.DYNAMIC_ZONES_LAYERS.flatMap { layer ->
                listOf(layer.fillLayerId, layer.lineLayerId)
            }
        )
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
private const val USER_LOCATION_CENTER_ZOOM = 15.0
private const val DEFAULT_APPROXIMATE_ACCURACY_METERS = 3_000f
private const val TAP_HIT_SLOP_PX = 18.0f
private const val SELECTED_POINT_SOURCE_ID = "dsc-selected-point-source"
private const val SELECTED_POINT_RING_LAYER_ID = "dsc-selected-point-ring"
private const val SELECTED_POINT_DOT_LAYER_ID = "dsc-selected-point-dot"
private const val USER_LOCATION_SOURCE_ID = "dsc-user-location-source"
private const val AUTH_TAKEOFF_SOURCE_ID = "dsc-auth-takeoff-source"
private const val AUTH_TAKEOFF_RING_LAYER_ID = "dsc-auth-takeoff-ring"
private const val AUTH_TAKEOFF_DOT_LAYER_ID = "dsc-auth-takeoff-dot"
private const val AUTH_AREA_VERTICES_SOURCE_ID = "dsc-auth-area-vertices-source"
private const val AUTH_AREA_VERTICES_LAYER_ID = "dsc-auth-area-vertices"
private const val AUTH_AREA_LINE_SOURCE_ID = "dsc-auth-area-line-source"
private const val AUTH_AREA_LINE_LAYER_ID = "dsc-auth-area-line"
private const val AUTH_AREA_FILL_SOURCE_ID = "dsc-auth-area-fill-source"
private const val AUTH_AREA_FILL_LAYER_ID = "dsc-auth-area-fill"
private const val USER_LOCATION_ACCURACY_LAYER_ID = "dsc-user-location-accuracy"
private const val USER_LOCATION_DOT_LAYER_ID = "dsc-user-location-dot"
private const val USER_LOCATION_PULSE_LAYER_ID = "dsc-user-location-pulse"
private const val NOTAM_ZEBRA_PATTERN_ID = "dsc-notam-zebra"
private const val NOTAM_ZEBRA_SIZE_PX = 16
private const val NOTAM_ZEBRA_STEP_PX = 8
private const val NOTAM_ZEBRA_STROKE_PX = 2.5f
private const val NOTAM_ZEBRA_OPACITY = 0.22f
private const val LOG_TAG = "DroneSkyMap"
private val DIAGNOSTIC_CATEGORIES = setOf(
    DscLayerCategory.Airports,
    DscLayerCategory.Aviosuperfici
)
private val MAIN_HANDLER = Handler(Looper.getMainLooper())
private val DSC_GEOJSON_EXECUTOR = Executors.newFixedThreadPool(3)
