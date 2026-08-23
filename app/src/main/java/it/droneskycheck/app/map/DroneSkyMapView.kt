package it.droneskycheck.app.map

import android.content.ComponentCallbacks
import android.content.Context
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
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.droneskycheck.app.data.CachedGeoJson
import it.droneskycheck.app.data.CachedGeoJsonRepository
import it.droneskycheck.app.data.DscLogger
import it.droneskycheck.app.data.ZonesRepository
import it.droneskycheck.app.data.traffic.TrafficAwarenessDefaults
import it.droneskycheck.app.data.traffic.TrafficAwarenessLogTag
import it.droneskycheck.app.data.traffic.TrafficAwarenessState
import it.droneskycheck.app.data.traffic.TrafficAssessment
import it.droneskycheck.app.data.traffic.TrafficHeatmapCellDetail
import it.droneskycheck.app.data.traffic.TrafficHeatmapState
import it.droneskycheck.app.data.traffic.coarseTraffic
import it.droneskycheck.app.data.traffic.trafficHeatmapCellDetailFromFeature
import it.droneskycheck.app.data.traffic.trafficHeatmapCellsToFeatureCollection
import it.droneskycheck.app.ui.map.CameraBounds
import it.droneskycheck.app.ui.map.DemoZone
import it.droneskycheck.app.ui.map.MapPoint
import it.droneskycheck.app.ui.map.MapTapSelection
import it.droneskycheck.app.ui.map.UserLocation
import java.util.Collections
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
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Geometry
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

@Composable
fun DroneSkyMapView(
    visibleLayerCategories: Set<DscLayerCategory>,
    selectedPoint: MapPoint?,
    trafficAwarenessCenter: MapPoint?,
    authorizationTakeoff: MapPoint?,
    authorizationAreaPoints: List<MapPoint>,
    authorizationAreaClosed: Boolean,
    trafficAwareness: TrafficAwarenessState,
    trafficAssessments: Map<String, TrafficAssessment>,
    trafficHeatmap: TrafficHeatmapState,
    mapDarkeningEnabled: Boolean,
    enhancedZoneOutlinesEnabled: Boolean,
    userLocation: UserLocation?,
    shouldCenterOnUserLocation: Boolean,
    cameraFocusPoint: MapPoint?,
    onUserLocationCentered: () -> Unit,
    onCameraFocusHandled: () -> Unit,
    onTrafficTargetTapped: (String) -> Unit,
    onTrafficHeatmapCellTapped: (TrafficHeatmapCellDetail) -> Unit,
    onMapTapped: (MapTapSelection) -> Unit,
    onCameraIdle: (CameraBounds) -> Unit,
    onMapDataDegraded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentTrafficHeatmap by rememberUpdatedState(trafficHeatmap)
    val currentTrafficHeatmapCellTapped by rememberUpdatedState(onTrafficHeatmapCellTapped)
    val radarLabelOverlay = remember {
        TrafficRadarLabelOverlay(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }
    val geoJsonRepository = remember(context) {
        CachedGeoJsonRepository(context.applicationContext)
    }
    val requestedStaticLayerKeys = remember { Collections.synchronizedSet(mutableSetOf<String>()) }
    val requestedDynamicZoneKeys = remember { Collections.synchronizedSet(mutableSetOf<String>()) }
    val loadedStaticLayerCollections = remember { Collections.synchronizedMap(mutableMapOf<String, FeatureCollection>()) }
    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
            getMapAsync { map ->
                configureMap(
                    this,
                    map,
                    geoJsonRepository,
                    requestedStaticLayerKeys,
                    requestedDynamicZoneKeys,
                    loadedStaticLayerCollections,
                    visibleLayerCategories,
                    onTrafficTargetTapped,
                    { feature ->
                        trafficHeatmapCellDetailFromFeature(
                            feature = feature,
                            fallbackMaxAgl = currentTrafficHeatmap.maxAgl,
                            periodDays = currentTrafficHeatmap.periodDays
                        )?.let(currentTrafficHeatmapCellTapped)
                    },
                    onMapTapped,
                    onCameraIdle,
                    onMapDataDegraded,
                    mapDarkeningEnabled,
                    enhancedZoneOutlinesEnabled,
                    radarLabelOverlay
                )
            }
        }
    }
    val mapContainer = remember {
        FrameLayout(context).apply {
            addView(
                mapView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            addView(radarLabelOverlay)
        }
    }

    DisposableEffect(lifecycleOwner, mapView, radarLabelOverlay) {
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
            stopTrafficAttentionPulse()
            radarLabelOverlay.detachFromMap()
            radarLabelOverlay.setTargets(emptyList())
            context.applicationContext.unregisterComponentCallbacks(callbacks)
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!isDestroyed) {
                mapView.onDestroy()
                isDestroyed = true
            }
        }
    }

    AndroidView(
        factory = { mapContainer },
        update = { view ->
            mapView.getMapAsync { map ->
                map.getStyle { style ->
                    addTrafficAwarenessLayers(style)
                    addTrafficHeatmapLayer(style)
                    addMapDarkeningLayer(style)
                    updateMapDarkening(style, mapDarkeningEnabled)
                    updateZoneOutlines(style, enhancedZoneOutlinesEnabled)
                    applyLayerVisibility(style, visibleLayerCategories)
                    updateViewportFilteredStaticSources(
                        style = style,
                        loadedLayerCollections = loadedStaticLayerCollections,
                        cameraBounds = map.currentCameraBounds(),
                        visibleLayerCategories = visibleLayerCategories
                    )
                    loadDscGeoJsonSources(
                        geoJsonRepository = geoJsonRepository,
                        style = style,
                        cameraBounds = map.currentCameraBounds(),
                        visibleLayerCategories = visibleLayerCategories,
                        requestedLayerKeys = requestedStaticLayerKeys,
                        loadedLayerCollections = loadedStaticLayerCollections,
                        onMapDataDegraded = onMapDataDegraded
                    )
                    loadDynamicZonesSources(
                        zonesRepository = ZonesRepository(mapView.context.applicationContext),
                        style = style,
                        cameraBounds = map.currentCameraBounds(),
                        visibleLayerCategories = visibleLayerCategories,
                        requestedLayerKeys = requestedDynamicZoneKeys,
                        onMapDataDegraded = onMapDataDegraded
                    )
                    updatePointMarkers(style, selectedPoint, userLocation)
                    updateAuthorizationDrawing(style, authorizationTakeoff, authorizationAreaPoints, authorizationAreaClosed)
                    updateTrafficAwareness(
                        style,
                        trafficAwarenessCenter ?: selectedPoint,
                        trafficAwareness,
                        trafficAssessments,
                        radarLabelOverlay
                    )
                    updateTrafficHeatmap(style, trafficHeatmap)
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
    geoJsonRepository: CachedGeoJsonRepository,
    requestedStaticLayerKeys: MutableSet<String>,
    requestedDynamicZoneKeys: MutableSet<String>,
    loadedStaticLayerCollections: MutableMap<String, FeatureCollection>,
    visibleLayerCategories: Set<DscLayerCategory>,
    onTrafficTargetTapped: (String) -> Unit,
    onTrafficHeatmapFeatureTapped: (Feature) -> Unit,
    onMapTapped: (MapTapSelection) -> Unit,
    onCameraIdle: (CameraBounds) -> Unit,
    onMapDataDegraded: () -> Unit,
    mapDarkeningEnabled: Boolean,
    enhancedZoneOutlinesEnabled: Boolean,
    radarLabelOverlay: TrafficRadarLabelOverlay
) {
    map.cameraPosition = CameraPosition.Builder()
        .target(LatLng(ROME_LATITUDE, ROME_LONGITUDE))
        .zoom(ROME_ZOOM)
        .build()

    val styleBuilder = Style.Builder().fromUri(MapLayerIds.STYLE_URL)
    val zonesRepository = ZonesRepository(mapView.context.applicationContext)
    val touchDensity = mapView.context.resources.displayMetrics.density

    map.setStyle(styleBuilder) {
        it.addImage(NOTAM_ZEBRA_PATTERN_ID, createNotamZebraPattern())
        DscLogger.debug(
            TrafficAwarenessLogTag,
            "map traffic style=map-glyph markerLayer=${MapLayerIds.TRAFFIC_AWARENESS_MARKER_LAYER_ID}"
        )
        addDscLayers(it)
        addTrafficAwarenessLayers(it)
        addTrafficHeatmapLayer(it)
        addMapDarkeningLayer(it)
        updateMapDarkening(it, mapDarkeningEnabled)
        updateZoneOutlines(it, enhancedZoneOutlinesEnabled)
        addPointMarkerLayers(it)
        radarLabelOverlay.attachToMap(map)
        startTrafficAttentionPulse(map)
        updatePointMarkers(it, null, null)
        loadDscGeoJsonSources(
            geoJsonRepository = geoJsonRepository,
            style = it,
            cameraBounds = map.currentCameraBounds(),
            visibleLayerCategories = visibleLayerCategories,
            requestedLayerKeys = requestedStaticLayerKeys,
            loadedLayerCollections = loadedStaticLayerCollections,
            onMapDataDegraded = onMapDataDegraded
        )
        loadDynamicZonesSources(
            zonesRepository,
            it,
            map.currentCameraBounds(),
            visibleLayerCategories,
            requestedDynamicZoneKeys,
            onMapDataDegraded
        )
        applyLayerVisibility(it, visibleLayerCategories)

        map.addOnMapClickListener { latLng ->
            val trafficFeatures = map.queryRenderedFeatures(
                touchAreaForLatLng(map, latLng, touchDensity),
                *trafficSymbolLayerIds()
            )
            val trafficTargetId = trafficFeatures.firstNotNullOfOrNull(::featureToTrafficTargetId)
            DscLogger.debug(
                TrafficAwarenessLogTag,
                "target tap featuresHit=${trafficFeatures.size} id=${trafficTargetId ?: "none"} " +
                    "layers=${trafficSymbolLayerIds().joinToString(",")}"
            )
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

            if (selectedZone != null) {
                onMapTapped(
                    MapTapSelection(
                        point = MapPoint(lat = latLng.latitude, lon = latLng.longitude),
                        zone = selectedZone,
                        zones = zones
                    )
                )
                return@addOnMapClickListener true
            }

            val heatmapFeature = map.queryRenderedFeatures(
                touchAreaForLatLng(map, latLng, touchDensity),
                MapLayerIds.TRAFFIC_HEATMAP_LAYER_ID
            ).firstOrNull()
            if (heatmapFeature != null) {
                onTrafficHeatmapFeatureTapped(heatmapFeature)
                return@addOnMapClickListener true
            }

            onMapTapped(
                MapTapSelection(
                    point = MapPoint(lat = latLng.latitude, lon = latLng.longitude),
                    zone = null,
                    zones = emptyList()
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
                updateViewportFilteredStaticSources(
                    style = style,
                    loadedLayerCollections = loadedStaticLayerCollections,
                    cameraBounds = cameraBounds,
                    visibleLayerCategories = visibleLayerCategories
                )
                loadDynamicZonesSources(
                    zonesRepository,
                    style,
                    cameraBounds,
                    visibleLayerCategories,
                    requestedDynamicZoneKeys,
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
    val vectorSourceCreated = style.addGeoJsonSourceIfMissing(
        MapLayerIds.TRAFFIC_AWARENESS_VECTOR_SOURCE_ID,
        emptyTrafficFeatureCollection()
    )
    val glyphSourceCreated = style.addGeoJsonSourceIfMissing(
        MapLayerIds.TRAFFIC_AWARENESS_GLYPH_SOURCE_ID,
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
        MapLayerIds.TRAFFIC_AWARENESS_MARKER_LAYER_ID
    )
    val vectorLayerCreated = style.addLayerBelowIfMissing(
        MapLayerIds.TRAFFIC_AWARENESS_VECTOR_LAYER_ID,
        LineLayer(
            MapLayerIds.TRAFFIC_AWARENESS_VECTOR_LAYER_ID,
            MapLayerIds.TRAFFIC_AWARENESS_VECTOR_SOURCE_ID
        ).withProperties(
            lineColor(trafficVectorColorExpression()),
            lineOpacity(0.68f),
            lineWidth(2.4f),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND)
        ),
        MapLayerIds.TRAFFIC_AWARENESS_MARKER_LAYER_ID
    )
    val markerLayerCreated = style.addLayerIfMissing(
        MapLayerIds.TRAFFIC_AWARENESS_MARKER_LAYER_ID,
        CircleLayer(
            MapLayerIds.TRAFFIC_AWARENESS_MARKER_LAYER_ID,
            MapLayerIds.TRAFFIC_AWARENESS_SOURCE_ID
        ).withProperties(
            circleRadius(18.0f),
            circleColor(trafficVectorColorExpression()),
            circleOpacity(0.0f),
            circleStrokeColor("#ffffff"),
            circleStrokeWidth(2.2f),
            circleStrokeOpacity(0.0f)
        )
    )
    val attentionGlyphLayerCreated = style.addLayerIfMissing(
        MapLayerIds.TRAFFIC_AWARENESS_ATTENTION_GLYPH_LAYER_ID,
        LineLayer(
            MapLayerIds.TRAFFIC_AWARENESS_ATTENTION_GLYPH_LAYER_ID,
            MapLayerIds.TRAFFIC_AWARENESS_GLYPH_SOURCE_ID
        ).withProperties(
            lineColor(TRAFFIC_AWARENESS_ATTENTION_COLOR),
            lineOpacity(TRAFFIC_ATTENTION_PULSE_MAX_OPACITY),
            lineWidth(TRAFFIC_ATTENTION_GLYPH_MIN_WIDTH),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND)
        ).withFilter(
            Expression.eq(
                Expression.get(TrafficAwarenessMapProperties.Relevance),
                Expression.literal(TrafficRelevanceAttention)
            )
        )
    )
    val glyphHaloLayerCreated = style.addLayerIfMissing(
        MapLayerIds.TRAFFIC_AWARENESS_GLYPH_HALO_LAYER_ID,
        LineLayer(
            MapLayerIds.TRAFFIC_AWARENESS_GLYPH_HALO_LAYER_ID,
            MapLayerIds.TRAFFIC_AWARENESS_GLYPH_SOURCE_ID
        ).withProperties(
            lineColor("#ffffff"),
            lineOpacity(0.96f),
            lineWidth(11.0f),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    val glyphLayerCreated = style.addLayerIfMissing(
        MapLayerIds.TRAFFIC_AWARENESS_GLYPH_LAYER_ID,
        LineLayer(
            MapLayerIds.TRAFFIC_AWARENESS_GLYPH_LAYER_ID,
            MapLayerIds.TRAFFIC_AWARENESS_GLYPH_SOURCE_ID
        ).withProperties(
            lineColor(trafficVectorColorExpression()),
            lineOpacity(1.0f),
            lineWidth(5.4f),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    if (
        radiusSourceCreated ||
        targetSourceCreated ||
        vectorSourceCreated ||
        glyphSourceCreated ||
        radiusFillLayerCreated ||
        radiusLineLayerCreated ||
        attentionHaloLayerCreated ||
        vectorLayerCreated ||
        markerLayerCreated ||
        attentionGlyphLayerCreated ||
        glyphHaloLayerCreated ||
        glyphLayerCreated
    ) {
        DscLogger.debug(
            TrafficAwarenessLogTag,
            "map install sourceCreated=$targetSourceCreated " +
                "attentionHaloLayerCreated=$attentionHaloLayerCreated " +
                "vectorLayerCreated=$vectorLayerCreated markerLayerCreated=$markerLayerCreated " +
                "attentionGlyphLayerCreated=$attentionGlyphLayerCreated " +
                "glyphLayerCreated=${glyphHaloLayerCreated || glyphLayerCreated} " +
                "radiusSourceCreated=$radiusSourceCreated radiusLayersCreated=${radiusFillLayerCreated || radiusLineLayerCreated}"
        )
    }
    if (targetSourceCreated || markerLayerCreated || glyphLayerCreated || glyphHaloLayerCreated || attentionGlyphLayerCreated) {
        DscLogger.debug(
            TrafficAwarenessLogTag,
            "map traffic layers ready marker=${style.getLayer(MapLayerIds.TRAFFIC_AWARENESS_MARKER_LAYER_ID) != null} " +
                "source=${style.getSourceAs<GeoJsonSource>(MapLayerIds.TRAFFIC_AWARENESS_SOURCE_ID) != null} " +
                "vectorSource=${style.getSourceAs<GeoJsonSource>(MapLayerIds.TRAFFIC_AWARENESS_VECTOR_SOURCE_ID) != null} " +
                "glyph=${style.getLayer(MapLayerIds.TRAFFIC_AWARENESS_GLYPH_LAYER_ID) != null}"
        )
    }
}

private fun addTrafficHeatmapLayer(style: Style) {
    style.addGeoJsonSourceIfMissing(
        MapLayerIds.TRAFFIC_HEATMAP_SOURCE_ID,
        emptyTrafficFeatureCollection()
    )
    style.addLayerBelowIfMissing(
        MapLayerIds.TRAFFIC_HEATMAP_LAYER_ID,
        CircleLayer(
            MapLayerIds.TRAFFIC_HEATMAP_LAYER_ID,
            MapLayerIds.TRAFFIC_HEATMAP_SOURCE_ID
        ).withProperties(
            circleRadius(trafficHeatmapRadiusExpression()),
            circleColor(trafficHeatmapColorExpression()),
            circleOpacity(0.42f),
            circleBlur(0.72f),
            circleStrokeWidth(0.0f),
            visibility(Property.NONE)
        ),
        MapLayerIds.STATIC_LAYERS.first().fillLayerId
    )
}

private fun updateTrafficHeatmap(style: Style, trafficHeatmap: TrafficHeatmapState) {
    addTrafficHeatmapLayer(style)
    val featureCollection = if (trafficHeatmap.enabled) {
        trafficHeatmapCellsToFeatureCollection(
            cells = trafficHeatmap.cells,
            maxAgl = trafficHeatmap.maxAgl
        )
    } else {
        emptyTrafficFeatureCollection()
    }
    style.setGeoJsonSourceIfAvailable(MapLayerIds.TRAFFIC_HEATMAP_SOURCE_ID, featureCollection)
    style.getLayer(MapLayerIds.TRAFFIC_HEATMAP_LAYER_ID)?.setProperties(
        visibility(if (trafficHeatmap.enabled) Property.VISIBLE else Property.NONE)
    )
}

private fun addMapDarkeningLayer(style: Style) {
    style.addGeoJsonSourceIfMissing(MAP_DARKENING_SOURCE_ID, mapDarkeningFeatureCollection())
    style.addLayerBelowIfMissing(
        MAP_DARKENING_LAYER_ID,
        FillLayer(MAP_DARKENING_LAYER_ID, MAP_DARKENING_SOURCE_ID)
            .withProperties(
                fillColor("#000000"),
                fillOpacity(0.0f)
            ),
        MapLayerIds.TRAFFIC_AWARENESS_RADIUS_FILL_LAYER_ID
    )
}

private fun updateMapDarkening(style: Style, enabled: Boolean) {
    style.getLayer(MAP_DARKENING_LAYER_ID)?.setProperties(
        fillOpacity(if (enabled) MAP_DARKENING_OPACITY else 0.0f)
    )
}

private fun updateZoneOutlines(style: Style, enhanced: Boolean) {
    MapLayerIds.STATIC_LAYERS.forEach { layer ->
        val mapLayer = style.getLayer(layer.lineLayerId) ?: return@forEach
        if (layer.isEnvironmentalProtectedArea) {
            mapLayer.setProperties(
                lineOpacity(if (enhanced) 0.96f else 0.82f),
                lineWidth(zoneOutlineWidth(layer.lineWidth, enhanced))
            )
        } else {
            mapLayer.setProperties(
                lineOpacity(DscZoneMapColors.lineOpacityExpression(enhanced)),
                lineWidth(zoneOutlineWidth(layer.lineWidth, enhanced))
            )
        }
    }
    MapLayerIds.DYNAMIC_ZONES_LAYERS.forEach { layer ->
        style.getLayer(layer.lineLayerId)?.setProperties(
            lineOpacity(DscZoneMapColors.lineOpacityExpression(enhanced)),
            lineWidth(zoneOutlineWidth(layer.lineWidth, enhanced))
        )
    }
}

private fun zoneOutlineWidth(baseWidth: Float, enhanced: Boolean): Float =
    if (enhanced) baseWidth + 1.1f else baseWidth

private fun startTrafficAttentionPulse(map: MapLibreMap) {
    if (trafficAttentionPulseRunnable != null) return
    val runnable = object : Runnable {
        override fun run() {
            val progress = (System.currentTimeMillis() % TRAFFIC_ATTENTION_PULSE_DURATION_MS).toFloat() /
                TRAFFIC_ATTENTION_PULSE_DURATION_MS.toFloat()
            val radius = TRAFFIC_ATTENTION_PULSE_MIN_RADIUS + (TRAFFIC_ATTENTION_PULSE_MAX_RADIUS - TRAFFIC_ATTENTION_PULSE_MIN_RADIUS) * progress
            val opacity = TRAFFIC_ATTENTION_PULSE_MAX_OPACITY * (1.0f - progress)
            val strokeOpacity = TRAFFIC_ATTENTION_PULSE_MAX_STROKE_OPACITY * (1.0f - progress)
            map.getStyle { style ->
                style.getLayer(MapLayerIds.TRAFFIC_AWARENESS_ATTENTION_HALO_LAYER_ID)?.setProperties(
                    circleRadius(radius),
                    circleOpacity(opacity),
                    circleStrokeOpacity(strokeOpacity),
                    circleStrokeWidth(1.2f + 1.4f * progress)
                )
                style.getLayer(MapLayerIds.TRAFFIC_AWARENESS_ATTENTION_GLYPH_LAYER_ID)?.setProperties(
                    lineOpacity(TRAFFIC_ATTENTION_GLYPH_MAX_OPACITY * (1.0f - progress)),
                    lineWidth(
                        TRAFFIC_ATTENTION_GLYPH_MIN_WIDTH +
                            (TRAFFIC_ATTENTION_GLYPH_MAX_WIDTH - TRAFFIC_ATTENTION_GLYPH_MIN_WIDTH) * progress
                    )
                )
            }
            MAIN_HANDLER.postDelayed(this, TRAFFIC_ATTENTION_PULSE_FRAME_MS)
        }
    }
    trafficAttentionPulseRunnable = runnable
    MAIN_HANDLER.post(runnable)
}

private fun stopTrafficAttentionPulse() {
    trafficAttentionPulseRunnable?.let(MAIN_HANDLER::removeCallbacks)
    trafficAttentionPulseRunnable = null
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
    trafficAssessments: Map<String, TrafficAssessment>,
    radarLabelOverlay: TrafficRadarLabelOverlay
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
    radarLabelOverlay.setTargets(
        if (enabled) targets.toTrafficRadarLabelTargets(trafficAssessments) else emptyList()
    )
    val targetFeatureList = targetFeatures.features().orEmpty()
    val vectorFeatures = if (enabled) {
        trafficDirectionVectorFeatureCollection(targets)
    } else {
        emptyTrafficFeatureCollection()
    }
    style.setGeoJsonSourceIfAvailable(
        MapLayerIds.TRAFFIC_AWARENESS_VECTOR_SOURCE_ID,
        vectorFeatures
    )
    val glyphFeatures = if (enabled) {
        trafficAircraftGlyphFeatureCollection(targets, trafficAssessments)
    } else {
        emptyTrafficFeatureCollection()
    }
    style.setGeoJsonSourceIfAvailable(
        MapLayerIds.TRAFFIC_AWARENESS_GLYPH_SOURCE_ID,
        glyphFeatures
    )
    if (enabled || targets.isNotEmpty()) {
        val firstFeature = targetFeatureList.firstOrNull()
        val firstProperties = firstFeature?.properties()
        DscLogger.debug(
            TrafficAwarenessLogTag,
            "map traffic render update enabled=$enabled targets=${targets.size} " +
                "pointFeatures=${targetFeatureList.size} vectorFeatures=${vectorFeatures.features().orEmpty().size} " +
                "glyphFeatures=${glyphFeatures.features().orEmpty().size} radarLabelFeatures=${targetFeatureList.count { it.properties()?.has(TrafficAwarenessMapProperties.RadarLabel) == true }} " +
                "targetSourceUpdated=$targetUpdated targetLayer=${style.getLayer(MapLayerIds.TRAFFIC_AWARENESS_MARKER_LAYER_ID) != null} " +
                "glyphLayer=${style.getLayer(MapLayerIds.TRAFFIC_AWARENESS_GLYPH_LAYER_ID) != null} " +
                "firstId=${firstProperties?.stringValue(TrafficAwarenessMapProperties.TargetId) ?: "none"} " +
                "firstAltLabel=${firstProperties?.stringValue(TrafficAwarenessMapProperties.AltitudeLabel) ?: "none"} " +
                "firstRadarLabel=${firstProperties?.stringValue(TrafficAwarenessMapProperties.RadarLabel) ?: "none"} " +
                "firstBand=${firstProperties?.stringValue(TrafficAwarenessMapProperties.AltitudeBand) ?: "none"} " +
                "firstFeed=${firstProperties?.stringValue(TrafficAwarenessMapProperties.FeedType) ?: "none"}"
        )
    }

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
        val fillLayer = FillLayer(
            layer.fillLayerId,
            layer.sourceId
        ).withZoomRange(layer)
        if (layer.isEnvironmentalProtectedArea) {
            fillLayer.withProperties(
                fillColor(DscZoneMapColors.environmentalProtectedAreaFill.webHex),
                fillOpacity(layer.zeroLimitOpacity)
            )
        } else {
            fillLayer.withProperties(
                fillColor(zoneFillColorExpression()),
                fillOpacity(DscZoneMapColors.fillOpacityExpression(layer))
            )
        }
        style.addLayer(fillLayer)
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
        val lineLayer = LineLayer(
            layer.lineLayerId,
            layer.sourceId
        ).withZoomRange(layer)
        if (layer.isEnvironmentalProtectedArea) {
            lineLayer.withProperties(
                lineColor(DscZoneMapColors.environmentalProtectedAreaLine.webHex),
                lineOpacity(0.82f),
                lineWidth(layer.lineWidth)
            )
        } else {
            lineLayer.withProperties(
                lineColor(zoneLineColorExpression()),
                lineOpacity(DscZoneMapColors.lineOpacityExpression()),
                lineWidth(layer.lineWidth)
            )
        }
        style.addLayer(lineLayer)
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
    cameraBounds: CameraBounds,
    visibleLayerCategories: Set<DscLayerCategory>,
    requestedLayerKeys: MutableSet<String>,
    loadedLayerCollections: MutableMap<String, FeatureCollection>,
    onMapDataDegraded: () -> Unit
) {
    MapLayerIds.STATIC_LAYERS
        .filter { layer -> !layer.loadOnlyWhenVisible || layer.category in visibleLayerCategories }
        .forEach { layer ->
            if (!requestedLayerKeys.add(layer.key)) return@forEach
            val executor = if (layer.loadOnlyWhenVisible) {
                ON_DEMAND_GEOJSON_EXECUTOR
            } else {
                STATIC_GEOJSON_EXECUTOR
            }
            executor.execute {
                Log.i(LOG_TAG, "GeoJSON load start key=${layer.key}, url=${layer.url}")
                runCatching {
                    loadStaticGeoJsonLayer(geoJsonRepository, layer)
                }.onSuccess { (cached, featureCollection) ->
                    if (cached.degraded) {
                        MAIN_HANDLER.post(onMapDataDegraded)
                    }
                    loadedLayerCollections[layer.key] = featureCollection
                    Log.i(
                        LOG_TAG,
                        "GeoJSON load success key=${layer.key}, degraded=${cached.degraded}, " +
                            "features=${featureCollection.features().orEmpty().size}"
                    )
                    MAIN_HANDLER.post {
                        style.setGeoJsonSourceIfAvailable(
                            layer.sourceId,
                            featureCollection.forVisibleMapSource(layer, cameraBounds, visibleLayerCategories)
                        )
                    }
                }.onFailure { error ->
                    requestedLayerKeys.remove(layer.key)
                    Log.w(LOG_TAG, "GeoJSON fetch failed key=${layer.key}, url=${layer.url}", error)
                }
            }
        }
}

private fun loadStaticGeoJsonLayer(
    geoJsonRepository: CachedGeoJsonRepository,
    layer: DscMapLayer
): Pair<CachedGeoJson, FeatureCollection> {
    val cached = geoJsonRepository.get(
        url = layer.url,
        cacheKey = layer.key,
        ttlMillis = layer.cacheTtlMillis,
        timeoutMillis = layer.networkTimeoutMillis
    )
    val parsed = runCatching {
        FeatureCollection.fromJson(cached.body).withStaticLayerProperties(layer)
            .requireUsableStaticLayer(layer)
    }.getOrElse { parseError ->
        Log.w(LOG_TAG, "GeoJSON cache invalid key=${layer.key}; deleting cache and refetching", parseError)
        geoJsonRepository.invalidate(layer.key)
        val refreshed = geoJsonRepository.get(
            url = layer.url,
            cacheKey = layer.key,
            ttlMillis = layer.cacheTtlMillis,
            timeoutMillis = layer.networkTimeoutMillis,
            forceRefresh = true
        )
        return refreshed to FeatureCollection.fromJson(refreshed.body).withStaticLayerProperties(layer)
            .requireUsableStaticLayer(layer)
    }
    return cached to parsed
}

private fun FeatureCollection.requireUsableStaticLayer(layer: DscMapLayer): FeatureCollection {
    if (layer.isEnvironmentalProtectedArea && features().orEmpty().isEmpty()) {
        throw IllegalStateException("Environmental GeoJSON has no features")
    }
    return this
}

private fun updateViewportFilteredStaticSources(
    style: Style,
    loadedLayerCollections: Map<String, FeatureCollection>,
    cameraBounds: CameraBounds,
    visibleLayerCategories: Set<DscLayerCategory>
) {
    MapLayerIds.STATIC_LAYERS
        .filter { layer -> layer.loadOnlyWhenVisible && layer.category in visibleLayerCategories }
        .forEach { layer ->
            val featureCollection = loadedLayerCollections[layer.key] ?: return@forEach
            style.setGeoJsonSourceIfAvailable(
                layer.sourceId,
                featureCollection.forVisibleMapSource(layer, cameraBounds, visibleLayerCategories)
            )
        }
}

private fun FeatureCollection.forVisibleMapSource(
    layer: DscMapLayer,
    cameraBounds: CameraBounds,
    visibleLayerCategories: Set<DscLayerCategory>
): FeatureCollection {
    if (!layer.isEnvironmentalProtectedArea) return this
    if (layer.category !in visibleLayerCategories || cameraBounds.zoom < layer.minZoom) return emptyFeatureCollection()

    val bounds = cameraBounds.expanded(EnvironmentalViewportPaddingDegrees)
    val visibleFeatures = features()
        .orEmpty()
        .asSequence()
        .filter { feature -> feature.geometry()?.intersects(bounds) == true }
        .take(MaxEnvironmentalFeaturesInSource + 1)
        .toList()

    val cappedFeatures = visibleFeatures.take(MaxEnvironmentalFeaturesInSource)
    if (visibleFeatures.size > MaxEnvironmentalFeaturesInSource) {
        Log.w(
            LOG_TAG,
            "GeoJSON viewport capped key=${layer.key}, features=${visibleFeatures.size}, " +
                "cap=$MaxEnvironmentalFeaturesInSource, zoom=${cameraBounds.zoom}"
        )
    }
    return FeatureCollection.fromFeatures(cappedFeatures)
}

private fun CameraBounds.expanded(paddingDegrees: Double): CameraBounds =
    copy(
        north = north + paddingDegrees,
        south = south - paddingDegrees,
        east = east + paddingDegrees,
        west = west - paddingDegrees
    )

private fun Geometry.intersects(bounds: CameraBounds): Boolean =
    featureBounds()?.intersects(bounds) ?: true

private fun Geometry.featureBounds(): FeatureBounds? =
    when (this) {
        is Point -> FeatureBounds(
            west = longitude(),
            south = latitude(),
            east = longitude(),
            north = latitude()
        )
        is Polygon -> coordinates().flatten().toFeatureBounds()
        is MultiPolygon -> coordinates().flatten().flatten().toFeatureBounds()
        else -> null
    }

private fun List<Point>.toFeatureBounds(): FeatureBounds? {
    if (isEmpty()) return null
    var west = Double.POSITIVE_INFINITY
    var south = Double.POSITIVE_INFINITY
    var east = Double.NEGATIVE_INFINITY
    var north = Double.NEGATIVE_INFINITY
    forEach { point ->
        west = minOf(west, point.longitude())
        south = minOf(south, point.latitude())
        east = maxOf(east, point.longitude())
        north = maxOf(north, point.latitude())
    }
    return FeatureBounds(west = west, south = south, east = east, north = north)
}

private data class FeatureBounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double
) {
    fun intersects(bounds: CameraBounds): Boolean =
        east >= bounds.west &&
            west <= bounds.east &&
            north >= bounds.south &&
            south <= bounds.north
}

private fun loadDynamicZonesSources(
    zonesRepository: ZonesRepository,
    style: Style,
    cameraBounds: CameraBounds,
    visibleLayerCategories: Set<DscLayerCategory>,
    requestedLayerKeys: MutableSet<String>,
    onMapDataDegraded: () -> Unit
) {
    MapLayerIds.DYNAMIC_ZONES_LAYERS
        .filter { layer ->
            layer.category in visibleLayerCategories && cameraBounds.zoom >= layer.minZoom
        }
        .forEach { layer ->
            val requestKey = "${layer.key}:${cameraBounds.bbox}"
            if (!requestedLayerKeys.add(requestKey)) return@forEach
            DYNAMIC_ZONES_EXECUTOR.execute {
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
                    requestedLayerKeys.remove(requestKey)
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
                sourceId == MapLayerIds.TRAFFIC_AWARENESS_RADIUS_SOURCE_ID ||
                sourceId == MapLayerIds.TRAFFIC_AWARENESS_VECTOR_SOURCE_ID ||
                sourceId == MapLayerIds.TRAFFIC_AWARENESS_GLYPH_SOURCE_ID
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
            sourceId == MapLayerIds.TRAFFIC_AWARENESS_RADIUS_SOURCE_ID ||
            sourceId == MapLayerIds.TRAFFIC_AWARENESS_VECTOR_SOURCE_ID ||
            sourceId == MapLayerIds.TRAFFIC_AWARENESS_GLYPH_SOURCE_ID
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
        name = properties?.stringValue("name")
            ?: properties?.stringValue("denominazi")
            ?: properties?.stringValue("denominazione")
            ?: properties?.stringValue("nome")
            ?: "Zona senza nome",
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

private fun FeatureCollection.withStaticLayerProperties(layer: DscMapLayer): FeatureCollection {
    features().orEmpty().forEach { feature ->
        if (feature.properties()?.stringValue("_splitType").isNullOrBlank()) {
            feature.addStringProperty(
                "_splitType",
                if (layer.isEnvironmentalProtectedArea) "PARKS_ENV" else layer.key
            )
        }
        if (layer.isEnvironmentalProtectedArea) {
            val properties = feature.properties()
            val name = properties?.stringValue("name")
                ?: properties?.stringValue("denominazi")
                ?: properties?.stringValue("denominazione")
                ?: properties?.stringValue("nome")
            if (!name.isNullOrBlank()) {
                feature.addStringProperty("name", name)
            }
            feature.addBooleanProperty("_environmentalOnly", true)
        }
    }
    return this
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

private fun trafficVectorColorExpression(): Expression =
    Expression.match(
        Expression.get(TrafficAwarenessMapProperties.AltitudeBand),
        Expression.literal(TrafficAltitudeBand.VERY_LOW.name),
        Expression.literal(TRAFFIC_ALTITUDE_VERY_LOW_COLOR),
        Expression.literal(TrafficAltitudeBand.LOW.name),
        Expression.literal(TRAFFIC_ALTITUDE_LOW_COLOR),
        Expression.literal(TrafficAltitudeBand.HIGH.name),
        Expression.literal(TRAFFIC_ALTITUDE_HIGH_COLOR),
        Expression.literal(TRAFFIC_AWARENESS_COLOR)
    )

private fun trafficHeatmapColorExpression(): Expression =
    Expression.interpolate(
        Expression.linear(),
        Expression.get("weight"),
        Expression.literal(1.0),
        Expression.literal(TRAFFIC_HEATMAP_LOW_COLOR),
        Expression.literal(3.0),
        Expression.literal(TRAFFIC_HEATMAP_MID_COLOR),
        Expression.literal(5.0),
        Expression.literal(TRAFFIC_HEATMAP_HIGH_COLOR)
    )

private fun trafficHeatmapRadiusExpression(): Expression =
    Expression.interpolate(
        Expression.linear(),
        Expression.get("weight"),
        Expression.literal(1.0),
        Expression.literal(18.0f),
        Expression.literal(3.0),
        Expression.literal(30.0f),
        Expression.literal(5.0),
        Expression.literal(44.0f)
    )

private fun trafficSymbolLayerIds(): Array<String> =
    listOf(
        MapLayerIds.TRAFFIC_AWARENESS_MARKER_LAYER_ID,
        MapLayerIds.TRAFFIC_AWARENESS_ATTENTION_GLYPH_LAYER_ID,
        MapLayerIds.TRAFFIC_AWARENESS_GLYPH_LAYER_ID,
        MapLayerIds.TRAFFIC_AWARENESS_GLYPH_HALO_LAYER_ID
    ).toTypedArray()

private fun mapDarkeningFeatureCollection(): FeatureCollection =
    FeatureCollection.fromFeature(
        Feature.fromGeometry(
            Polygon.fromLngLats(
                listOf(
                    listOf(
                        Point.fromLngLat(-180.0, -85.0),
                        Point.fromLngLat(180.0, -85.0),
                        Point.fromLngLat(180.0, 85.0),
                        Point.fromLngLat(-180.0, 85.0),
                        Point.fromLngLat(-180.0, -85.0)
                    )
                )
            )
        )
    )

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
private const val MAP_DARKENING_SOURCE_ID = "dsc-map-darkening-source"
private const val MAP_DARKENING_LAYER_ID = "dsc-map-darkening-layer"
private const val MAP_DARKENING_OPACITY = 0.24f
private const val TRAFFIC_AWARENESS_COLOR = "#455a64"
private const val TRAFFIC_ALTITUDE_VERY_LOW_COLOR = "#FFC928"
private const val TRAFFIC_ALTITUDE_LOW_COLOR = "#32D4E8"
private const val TRAFFIC_ALTITUDE_HIGH_COLOR = "#8FA9C4"
private const val TRAFFIC_AWARENESS_ATTENTION_COLOR = "#f9ab00"
private const val TRAFFIC_HEATMAP_LOW_COLOR = "#4AA3FF"
private const val TRAFFIC_HEATMAP_MID_COLOR = "#A967FF"
private const val TRAFFIC_HEATMAP_HIGH_COLOR = "#FFC457"
private const val TRAFFIC_ATTENTION_PULSE_DURATION_MS = 1_100L
private const val TRAFFIC_ATTENTION_PULSE_FRAME_MS = 90L
private const val TRAFFIC_ATTENTION_PULSE_MIN_RADIUS = 18.0f
private const val TRAFFIC_ATTENTION_PULSE_MAX_RADIUS = 31.0f
private const val TRAFFIC_ATTENTION_PULSE_MAX_OPACITY = 0.30f
private const val TRAFFIC_ATTENTION_PULSE_MAX_STROKE_OPACITY = 0.82f
private const val TRAFFIC_ATTENTION_GLYPH_MIN_WIDTH = 13.0f
private const val TRAFFIC_ATTENTION_GLYPH_MAX_WIDTH = 23.0f
private const val TRAFFIC_ATTENTION_GLYPH_MAX_OPACITY = 0.82f
private const val TrafficRelevanceAttention = "ATTENTION"
private const val NOTAM_ZEBRA_SIZE_PX = 32
private const val NOTAM_ZEBRA_STEP_PX = 16
private const val NOTAM_ZEBRA_STROKE_PX = 2.2f
private const val NOTAM_ZEBRA_OPACITY = 0.22f
private const val MaxEnvironmentalFeaturesInSource = 450
private const val EnvironmentalViewportPaddingDegrees = 0.35
private const val LOG_TAG = "DroneSkyMap"

private object TrafficMapStyle {
    const val AttentionHaloRadius = 17.0f
}

private val MAIN_HANDLER = Handler(Looper.getMainLooper())
private val STATIC_GEOJSON_EXECUTOR = Executors.newFixedThreadPool(2)
private val ON_DEMAND_GEOJSON_EXECUTOR = Executors.newSingleThreadExecutor()
private val DYNAMIC_ZONES_EXECUTOR = Executors.newSingleThreadExecutor()
private var trafficAttentionPulseRunnable: Runnable? = null
