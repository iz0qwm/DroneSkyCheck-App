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
import it.droneskycheck.app.data.DscLogger
import it.droneskycheck.app.data.ZonesRepository
import it.droneskycheck.app.data.traffic.TrafficAwarenessDefaults
import it.droneskycheck.app.data.traffic.TrafficAwarenessLogTag
import it.droneskycheck.app.data.traffic.TrafficAwarenessState
import it.droneskycheck.app.data.traffic.TrafficAssessment
import it.droneskycheck.app.data.traffic.TrafficTargetKind
import it.droneskycheck.app.data.traffic.coarseTraffic
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
import org.maplibre.android.style.layers.SymbolLayer
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
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconSize
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
    trafficAwareness: TrafficAwarenessState,
    trafficAssessments: Map<String, TrafficAssessment>,
    userLocation: UserLocation?,
    shouldCenterOnUserLocation: Boolean,
    cameraFocusPoint: MapPoint?,
    onUserLocationCentered: () -> Unit,
    onCameraFocusHandled: () -> Unit,
    onTrafficTargetTapped: (String) -> Unit,
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
                configureMap(
                    this,
                    map,
                    visibleLayerCategories,
                    onTrafficTargetTapped,
                    onMapTapped,
                    onCameraIdle,
                    onMapDataDegraded
                )
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
                    addTrafficAwarenessLayers(style)
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
                    updateTrafficAwareness(style, selectedPoint, trafficAwareness, trafficAssessments)
                    if (shouldCenterOnUserLocation && userLocation != null) {
                        centerOnUserLocation(map, userLocation)
                        onUserLocationCentered()
                    }
                    if (cameraFocusPoint != null) {
                        centerOnPoint(map, cameraFocusPoint, SEARCH_RESULT_CENTER_ZOOM)
                        onCameraFocusHandled()
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
    onTrafficTargetTapped: (String) -> Unit,
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
    val touchDensity = mapView.context.resources.displayMetrics.density

    map.setStyle(styleBuilder) {
        it.addImage(NOTAM_ZEBRA_PATTERN_ID, createNotamZebraPattern())
        TrafficAircraftIconStyle.entries.forEach { iconStyle ->
            val trafficIcon = createTrafficAircraftIcon(iconStyle.fillColor)
            it.addImage(iconStyle.imageId, trafficIcon)
            DscLogger.trace(
                TrafficAwarenessLogTag,
                "map icon registered id=${iconStyle.imageId} width=${trafficIcon.width} height=${trafficIcon.height}"
            )
        }
        it.addImage(TRAFFIC_AWARENESS_DRONE_ICON_ID, createTrafficDroneIcon())
        DscLogger.trace(
            TrafficAwarenessLogTag,
            "map traffic altitude icon variants=${TrafficAircraftIconStyle.entries.size}"
        )
        addDscLayers(it)
        addTrafficAwarenessLayers(it)
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

        map.addOnMapClickListener { latLng ->
            val trafficFeatures = map.queryRenderedFeatures(
                touchAreaForLatLng(map, latLng, touchDensity),
                MapLayerIds.TRAFFIC_AWARENESS_SYMBOL_LAYER_ID
            )
            val trafficTargetId = trafficFeatures.firstNotNullOfOrNull(::featureToTrafficTargetId)
            // Paused noisy traffic tap diagnostics during field testing.
            // DscLogger.trace(
            //     TrafficAwarenessLogTag,
            //     "target tap featuresHit=${trafficFeatures.size} id=${trafficTargetId ?: "none"}"
            // )
            if (trafficTargetId != null) {
                onTrafficTargetTapped(trafficTargetId)
                return@addOnMapClickListener true
            }

            val zones = map.queryRenderedFeatures(
                touchAreaForLatLng(map, latLng, touchDensity),
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
            onCameraIdle(cameraBounds)
            map.getStyle { style ->
                loadDynamicZonesSources(
                    zonesRepository,
                    style,
                    cameraBounds,
                    visibleLayerCategories,
                    onMapDataDegraded
                )
            }
        }
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

private fun addTrafficAwarenessLayers(style: Style) {
    val radiusSourceCreated = style.addGeoJsonSourceIfMissing(
        MapLayerIds.TRAFFIC_AWARENESS_RADIUS_SOURCE_ID,
        emptyTrafficFeatureCollection()
    )
    val targetSourceCreated = style.addGeoJsonSourceIfMissing(
        MapLayerIds.TRAFFIC_AWARENESS_SOURCE_ID,
        emptyTrafficFeatureCollection()
    )

    val radiusFillLayerCreated = style.addLayerIfMissing(
        MapLayerIds.TRAFFIC_AWARENESS_RADIUS_FILL_LAYER_ID,
        FillLayer(
            MapLayerIds.TRAFFIC_AWARENESS_RADIUS_FILL_LAYER_ID,
            MapLayerIds.TRAFFIC_AWARENESS_RADIUS_SOURCE_ID
        ).withProperties(
            fillColor(TRAFFIC_AWARENESS_COLOR),
            fillOpacity(0.045f)
        )
    )
    val radiusLineLayerCreated = style.addLayerIfMissing(
        MapLayerIds.TRAFFIC_AWARENESS_RADIUS_LINE_LAYER_ID,
        LineLayer(
            MapLayerIds.TRAFFIC_AWARENESS_RADIUS_LINE_LAYER_ID,
            MapLayerIds.TRAFFIC_AWARENESS_RADIUS_SOURCE_ID
        ).withProperties(
            lineColor(TRAFFIC_AWARENESS_COLOR),
            lineOpacity(0.72f),
            lineWidth(1.6f)
        )
    )
    val attentionHaloLayerCreated = style.addLayerBelowIfMissing(
        MapLayerIds.TRAFFIC_AWARENESS_ATTENTION_HALO_LAYER_ID,
        CircleLayer(
            MapLayerIds.TRAFFIC_AWARENESS_ATTENTION_HALO_LAYER_ID,
            MapLayerIds.TRAFFIC_AWARENESS_SOURCE_ID
        ).withProperties(
            circleRadius(TrafficMapStyle.AttentionHaloRadius),
            circleColor(TRAFFIC_AWARENESS_ATTENTION_COLOR),
            circleOpacity(0.24f),
            circleStrokeColor(TRAFFIC_AWARENESS_ATTENTION_COLOR),
            circleStrokeOpacity(0.76f),
            circleStrokeWidth(1.4f)
        ).withFilter(
            Expression.eq(
                Expression.get(TrafficAwarenessMapProperties.Relevance),
                Expression.literal(TrafficRelevanceAttention)
            )
        ),
        MapLayerIds.TRAFFIC_AWARENESS_SYMBOL_LAYER_ID
    )
    val symbolLayerCreated = style.addLayerIfMissing(
        MapLayerIds.TRAFFIC_AWARENESS_SYMBOL_LAYER_ID,
        SymbolLayer(
            MapLayerIds.TRAFFIC_AWARENESS_SYMBOL_LAYER_ID,
            MapLayerIds.TRAFFIC_AWARENESS_SOURCE_ID
        ).withProperties(
            iconImage(trafficIconImageExpression()),
            iconRotate(Expression.get(TrafficAwarenessMapProperties.RotationDeg)),
            iconSize(TrafficMapStyle.AircraftIconScale),
            iconAllowOverlap(true),
            iconIgnorePlacement(true)
        )
    )
    if (
        radiusSourceCreated ||
        targetSourceCreated ||
        radiusFillLayerCreated ||
        radiusLineLayerCreated ||
        attentionHaloLayerCreated ||
        symbolLayerCreated
    ) {
        DscLogger.debug(
            TrafficAwarenessLogTag,
            "map install sourceCreated=$targetSourceCreated symbolLayerCreated=$symbolLayerCreated " +
                "attentionHaloLayerCreated=$attentionHaloLayerCreated " +
                "radiusSourceCreated=$radiusSourceCreated radiusLayersCreated=${radiusFillLayerCreated || radiusLineLayerCreated}"
        )
    }
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

private fun updateTrafficAwareness(
    style: Style,
    selectedPoint: MapPoint?,
    trafficAwareness: TrafficAwarenessState,
    trafficAssessments: Map<String, TrafficAssessment>
) {
    val enabled = trafficAwareness.enabled
    val targets = if (enabled) {
        trafficAwareness.response?.traffic?.targets.orEmpty()
    } else {
        emptyList()
    }
    addTrafficAwarenessLayers(style)

    val targetFeatures = trafficTargetsFeatureCollection(targets, trafficAssessments)
    val targetUpdated = style.setGeoJsonSourceIfAvailable(
        MapLayerIds.TRAFFIC_AWARENESS_SOURCE_ID,
        targetFeatures
    )
    // Paused noisy traffic source diagnostics during field testing.
    // DscLogger.trace(
    //     TrafficAwarenessLogTag,
    //     "map source update source=${MapLayerIds.TRAFFIC_AWARENESS_SOURCE_ID} " +
    //         "features=${targetFeatures.features().orEmpty().size} styleLoaded=true sourceFound=$targetUpdated"
    // )

    val radiusFeatures = if (enabled) {
        trafficRadiusFeatureCollection(
            center = selectedPoint,
            radiusKm = TrafficAwarenessDefaults.DefaultRadiusKm
        )
    } else {
        emptyTrafficFeatureCollection()
    }
    val radiusUpdated = style.setGeoJsonSourceIfAvailable(
        MapLayerIds.TRAFFIC_AWARENESS_RADIUS_SOURCE_ID,
        radiusFeatures
    )
    // Paused noisy traffic radius diagnostics during field testing.
    // DscLogger.trace(
    //     TrafficAwarenessLogTag,
    //     "radius update center=${selectedPoint?.let { "${it.lat.coarseTraffic()},${it.lon.coarseTraffic()}" } ?: "none"} " +
    //         "radiusKm=${TrafficAwarenessDefaults.DefaultRadiusKm.coarseTraffic(0)} " +
    //         "features=${radiusFeatures.features().orEmpty().size} sourceFound=$radiusUpdated"
    // )
}

private fun centerOnUserLocation(map: MapLibreMap, userLocation: UserLocation) {
    centerOnPoint(map, userLocation.point, USER_LOCATION_CENTER_ZOOM)
}

private fun centerOnPoint(map: MapLibreMap, point: MapPoint, minZoom: Double) {
    val currentZoom = map.cameraPosition.zoom
    map.animateCamera(
        CameraUpdateFactory.newLatLngZoom(
            LatLng(point.lat, point.lon),
            maxOf(currentZoom, minZoom)
        )
    )
}

private fun addDscLayers(style: Style) {
    MapLayerIds.STATIC_LAYERS.forEach { layer ->
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
            runCatching {
                val cached = geoJsonRepository.get(layer.url, layer.key)
                cached to FeatureCollection.fromJson(cached.body)
            }.onSuccess { (cached, featureCollection) ->
                if (cached.degraded) {
                    MAIN_HANDLER.post(onMapDataDegraded)
                }
                MAIN_HANDLER.post {
                    style.setGeoJsonSourceIfAvailable(layer.sourceId, featureCollection)
                }
            }.onFailure { error ->
                Log.w(LOG_TAG, "GeoJSON fetch failed key=${layer.key}, url=${layer.url}", error)
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

private fun Style.setGeoJsonSourceIfAvailable(sourceId: String, featureCollection: FeatureCollection): Boolean =
    runCatching {
        val source = getSourceAs<GeoJsonSource>(sourceId)
        if (source == null) {
            if (sourceId == MapLayerIds.TRAFFIC_AWARENESS_SOURCE_ID ||
                sourceId == MapLayerIds.TRAFFIC_AWARENESS_RADIUS_SOURCE_ID
            ) {
                DscLogger.warn(TrafficAwarenessLogTag, "map source missing source=$sourceId")
            }
            false
        } else {
            source.setGeoJson(featureCollection)
            true
        }
    }.onFailure { error ->
        if (sourceId == MapLayerIds.TRAFFIC_AWARENESS_SOURCE_ID ||
            sourceId == MapLayerIds.TRAFFIC_AWARENESS_RADIUS_SOURCE_ID
        ) {
            DscLogger.warn(TrafficAwarenessLogTag, "map style not ready source=$sourceId", error)
        } else {
            Log.w(LOG_TAG, "Skipped GeoJSON update for source=$sourceId because the map style is not ready", error)
        }
    }.getOrDefault(false)

private fun Style.addGeoJsonSourceIfMissing(sourceId: String, featureCollection: FeatureCollection): Boolean =
    if (getSourceAs<GeoJsonSource>(sourceId) == null) {
        addSource(GeoJsonSource(sourceId, featureCollection))
        true
    } else {
        false
    }

private fun Style.addLayerIfMissing(layerId: String, layer: Layer): Boolean =
    if (getLayer(layerId) == null) {
        addLayer(layer)
        true
    } else {
        false
    }

private fun Style.addLayerBelowIfMissing(layerId: String, layer: Layer, belowLayerId: String): Boolean =
    if (getLayer(layerId) == null) {
        if (getLayer(belowLayerId) == null) {
            addLayer(layer)
        } else {
            addLayerBelow(layer, belowLayerId)
        }
        true
    } else {
        false
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

private fun touchAreaForLatLng(map: MapLibreMap, latLng: LatLng, density: Float): RectF {
    val center: PointF = map.projection.toScreenLocation(latLng)
    val hitBox = trafficTapHitBoxForScreenPoint(center.x, center.y, density)
    return RectF(
        hitBox.left,
        hitBox.top,
        hitBox.right,
        hitBox.bottom
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

private fun featureToTrafficTargetId(feature: Feature): String? =
    feature.properties()?.stringValue(TrafficAwarenessMapProperties.TargetId)

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

private fun createTrafficAircraftIcon(fillColor: String): Bitmap {
    val bitmap = Bitmap.createBitmap(TRAFFIC_AWARENESS_ICON_SIZE_PX, TRAFFIC_AWARENESS_ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(fillColor)
        style = Paint.Style.FILL
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
    }
    val cx = TRAFFIC_AWARENESS_ICON_SIZE_PX / 2f
    val path = android.graphics.Path().apply {
        moveTo(cx, 4f)
        lineTo(cx + 7f, 22f)
        lineTo(cx + 19f, 27f)
        lineTo(cx + 19f, 34f)
        lineTo(cx + 4f, 31f)
        lineTo(cx + 4f, 43f)
        lineTo(cx + 10f, 47f)
        lineTo(cx + 10f, 52f)
        lineTo(cx, 49f)
        lineTo(cx - 10f, 52f)
        lineTo(cx - 10f, 47f)
        lineTo(cx - 4f, 43f)
        lineTo(cx - 4f, 31f)
        lineTo(cx - 19f, 34f)
        lineTo(cx - 19f, 27f)
        lineTo(cx - 7f, 22f)
        close()
    }
    canvas.drawPath(path, paint)
    canvas.drawPath(path, stroke)
    return bitmap
}

private fun createTrafficDroneIcon(): Bitmap {
    val bitmap = Bitmap.createBitmap(TRAFFIC_AWARENESS_ICON_SIZE_PX, TRAFFIC_AWARENESS_ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5.0f
        strokeCap = Paint.Cap.ROUND
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(TRAFFIC_AWARENESS_DRONE_COLOR)
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
    }
    val cx = TRAFFIC_AWARENESS_ICON_SIZE_PX / 2f
    val cy = TRAFFIC_AWARENESS_ICON_SIZE_PX / 2f
    val rotorCenters = listOf(
        PointF(cx - 16f, cy - 16f),
        PointF(cx + 16f, cy - 16f),
        PointF(cx - 16f, cy + 16f),
        PointF(cx + 16f, cy + 16f)
    )

    canvas.drawLine(cx - 12f, cy - 12f, cx + 12f, cy + 12f, armPaint)
    canvas.drawLine(cx + 12f, cy - 12f, cx - 12f, cy + 12f, armPaint)
    rotorCenters.forEach { center ->
        canvas.drawCircle(center.x, center.y, 8.5f, fillPaint)
        canvas.drawCircle(center.x, center.y, 8.5f, strokePaint)
    }
    canvas.drawRoundRect(RectF(cx - 9f, cy - 7f, cx + 9f, cy + 7f), 5f, 5f, fillPaint)
    canvas.drawRoundRect(RectF(cx - 9f, cy - 7f, cx + 9f, cy + 7f), 5f, 5f, strokePaint)
    canvas.drawCircle(cx, cy, 2.8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    })
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

private fun trafficIconImageExpression(): Expression =
    Expression.match(
        Expression.get(TrafficAwarenessMapProperties.TargetKind),
        Expression.literal(TrafficTargetKind.DRONE.name),
        Expression.literal(TRAFFIC_AWARENESS_DRONE_ICON_ID),
        trafficAircraftIconImageExpression()
    )

private fun trafficAircraftIconImageExpression(): Expression =
    Expression.match(
        Expression.get(TrafficAwarenessMapProperties.AltitudeBand),
        Expression.literal(TrafficAltitudeBand.VERY_LOW.name),
        Expression.literal(TrafficAircraftIconStyle.VeryLow.imageId),
        Expression.literal(TrafficAltitudeBand.LOW.name),
        Expression.literal(TrafficAircraftIconStyle.Low.imageId),
        Expression.literal(TrafficAltitudeBand.HIGH.name),
        Expression.literal(TrafficAircraftIconStyle.High.imageId),
        Expression.literal(TrafficAircraftIconStyle.Unknown.imageId)
    )

private enum class TrafficAircraftIconStyle(
    val imageId: String,
    val fillColor: String
) {
    VeryLow("dsc-traffic-awareness-aircraft-very-low", DscZoneMapColors.limited25m.webHex),
    Low("dsc-traffic-awareness-aircraft-low", DscZoneMapColors.limited60m.webHex),
    High("dsc-traffic-awareness-aircraft-high", TRAFFIC_AWARENESS_HIGH_ALTITUDE_COLOR),
    Unknown("dsc-traffic-awareness-aircraft-unknown", TRAFFIC_AWARENESS_COLOR)
}

private const val ROME_LATITUDE = 41.9028
private const val ROME_LONGITUDE = 12.4964
private const val ROME_ZOOM = 10.0
private const val USER_LOCATION_CENTER_ZOOM = 15.0
private const val SEARCH_RESULT_CENTER_ZOOM = 13.5
private const val DEFAULT_APPROXIMATE_ACCURACY_METERS = 3_000f
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
private const val TRAFFIC_AWARENESS_ICON_SIZE_PX = 56
private const val TRAFFIC_AWARENESS_DRONE_ICON_ID = "dsc-traffic-awareness-drone"
private const val TRAFFIC_AWARENESS_COLOR = "#455a64"
private const val TRAFFIC_AWARENESS_DRONE_COLOR = "#00796b"
private const val TRAFFIC_AWARENESS_HIGH_ALTITUDE_COLOR = "#78909c"
private const val TRAFFIC_AWARENESS_ATTENTION_COLOR = "#f9ab00"
private const val TrafficRelevanceAttention = "ATTENTION"
private const val NOTAM_ZEBRA_SIZE_PX = 32
private const val NOTAM_ZEBRA_STEP_PX = 16
private const val NOTAM_ZEBRA_STROKE_PX = 2.2f
private const val NOTAM_ZEBRA_OPACITY = 0.22f
private const val LOG_TAG = "DroneSkyMap"

private object TrafficMapStyle {
    const val AircraftIconScale = 1.50f
    const val AttentionHaloRadius = 17.0f
}
private val MAIN_HANDLER = Handler(Looper.getMainLooper())
private val DSC_GEOJSON_EXECUTOR = Executors.newFixedThreadPool(3)
