package it.droneskycheck.app.map

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.droneskycheck.app.R
import it.droneskycheck.app.data.CachedGeoJsonRepository
import it.droneskycheck.app.data.DscLogger
import it.droneskycheck.app.data.ZonesRepository
import it.droneskycheck.app.data.traffic.TrafficAwarenessDefaults
import it.droneskycheck.app.data.traffic.TrafficAwarenessLogTag
import it.droneskycheck.app.data.traffic.TrafficAwarenessState
import it.droneskycheck.app.data.traffic.TrafficAssessment
import it.droneskycheck.app.data.traffic.TrafficFeedType
import it.droneskycheck.app.data.traffic.TrafficTarget
import it.droneskycheck.app.data.traffic.TrafficTargetKind
import it.droneskycheck.app.data.traffic.coarseTraffic
import it.droneskycheck.app.data.traffic.trafficFeedType
import it.droneskycheck.app.data.traffic.trafficTargetKind
import it.droneskycheck.app.ui.map.CameraBounds
import it.droneskycheck.app.ui.map.DemoZone
import it.droneskycheck.app.ui.map.MapPoint
import it.droneskycheck.app.ui.map.MapTapSelection
import it.droneskycheck.app.ui.map.UserLocation
import java.util.concurrent.Executors
import kotlin.math.roundToInt
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
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
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
    trafficAwarenessCenter: MapPoint?,
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
    var trafficOverlayItems by remember { mutableStateOf<List<TrafficOverlayItem>>(emptyList()) }
    val currentTrafficAwareness = rememberUpdatedState(trafficAwareness)
    val updateTrafficOverlay: (MapLibreMap) -> Unit = remember {
        { map -> trafficOverlayItems = map.trafficOverlayItems(currentTrafficAwareness.value) }
    }
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
                    onMapDataDegraded,
                    updateTrafficOverlay
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

    Box(modifier = modifier) {
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
                        updateTrafficAwareness(style, trafficAwarenessCenter ?: selectedPoint, trafficAwareness, trafficAssessments)
                        updateTrafficOverlay(map)
                        if (shouldCenterOnUserLocation && userLocation != null) {
                            centerOnUserLocation(map, userLocation)
                            onUserLocationCentered()
                        }
                        if (cameraFocusPoint != null) {
                            centerOnPoint(map, cameraFocusPoint, SEARCH_RESULT_CENTER_ZOOM)
                            onCameraFocusHandled()
                            updateTrafficOverlay(map)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        TrafficOverlay(
            items = trafficOverlayItems,
            onTrafficTargetTapped = onTrafficTargetTapped,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun TrafficOverlay(
    items: List<TrafficOverlayItem>,
    onTrafficTargetTapped: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val halfWidthPx = with(density) { TrafficOverlayStyle.MarkerWidth.toPx() / 2.0f }
    val iconCenterYPx = with(density) { TrafficOverlayStyle.IconCenterY.toPx() }
    Box(modifier = modifier) {
        items.forEach { item ->
            TrafficOverlayMarker(
                item = item,
                onTrafficTargetTapped = onTrafficTargetTapped,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (item.screenX - halfWidthPx).roundToInt(),
                            y = (item.screenY - iconCenterYPx).roundToInt()
                        )
                    }
                    .zIndex(3.0f)
            )
        }
    }
}

@Composable
private fun TrafficOverlayMarker(
    item: TrafficOverlayItem,
    onTrafficTargetTapped: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .size(width = TrafficOverlayStyle.MarkerWidth, height = TrafficOverlayStyle.MarkerHeight)
            .clickable { onTrafficTargetTapped(item.targetId) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(TrafficOverlayStyle.IconHaloSize)
                .graphicsLayer(rotationZ = item.rotationDeg.toFloat()),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = item.icon.drawableResId),
                contentDescription = null,
                tint = ComposeColor.White,
                modifier = Modifier.size(TrafficOverlayStyle.IconHaloSize)
            )
            Icon(
                painter = painterResource(id = item.icon.drawableResId),
                contentDescription = null,
                tint = item.color,
                modifier = Modifier.size(TrafficOverlayStyle.IconSize)
            )
        }
        item.altitudeLabel?.let { altitude ->
            Text(
                text = altitude,
                color = ComposeColor.Black,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .widthIn(max = TrafficOverlayStyle.MarkerWidth)
                    .background(ComposeColor.White.copy(alpha = 0.68f))
            )
        }
    }
}

private fun configureMap(
    mapView: MapView,
    map: MapLibreMap,
    visibleLayerCategories: Set<DscLayerCategory>,
    onTrafficTargetTapped: (String) -> Unit,
    onMapTapped: (MapTapSelection) -> Unit,
    onCameraIdle: (CameraBounds) -> Unit,
    onMapDataDegraded: () -> Unit,
    onTrafficOverlayUpdate: (MapLibreMap) -> Unit
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
        TrafficHelicopterIconStyle.entries.forEach { iconStyle ->
            val trafficIcon = createTrafficHelicopterIcon(iconStyle.fillColor)
            it.addImage(iconStyle.imageId, trafficIcon)
        }
        TrafficFeedIconStyle.entries.forEach { iconStyle ->
            val trafficIcon = createTrafficFeedIcon(iconStyle.feedType, iconStyle.fillColor)
            it.addImage(iconStyle.imageId, trafficIcon)
        }
        it.addImage(TrafficMapIconIds.Drone, createTrafficDroneIcon(mapView.context))
        DscLogger.debug(
            TrafficAwarenessLogTag,
            "map traffic altitude icon variants=${TrafficAircraftIconStyle.entries.size} " +
                "helicopterVariants=${TrafficHelicopterIconStyle.entries.size} feedVariants=${TrafficFeedIconStyle.entries.size} " +
                "symbolLayers=${trafficSymbolLayerStyles().size} markerLayer=${MapLayerIds.TRAFFIC_AWARENESS_MARKER_LAYER_ID}"
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
        onTrafficOverlayUpdate(map)

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
            onTrafficOverlayUpdate(map)
        }
    }
}

private fun MapLibreMap.trafficOverlayItems(state: TrafficAwarenessState): List<TrafficOverlayItem> {
    if (!state.enabled) return emptyList()
    return state.response?.traffic?.targets.orEmpty().mapNotNull { target ->
        val screenPoint = runCatching {
            projection.toScreenLocation(LatLng(target.position.lat, target.position.lon))
        }.getOrNull() ?: return@mapNotNull null
        TrafficOverlayItem(
            targetId = target.id,
            screenX = screenPoint.x,
            screenY = screenPoint.y,
            rotationDeg = target.mapRotationDeg(),
            icon = target.trafficOverlayIcon(),
            color = target.trafficOverlayColor(),
            altitudeLabel = target.mapAltitudeLabel()
        )
    }
}

private data class TrafficOverlayItem(
    val targetId: String,
    val screenX: Float,
    val screenY: Float,
    val rotationDeg: Double,
    val icon: TrafficOverlayIcon,
    val color: ComposeColor,
    val altitudeLabel: String?
)

private enum class TrafficOverlayIcon {
    Aircraft,
    Helicopter,
    Drone,
    Fanet,
    Flarm,
    FreeFlight,
    Unknown
}

private val TrafficOverlayIcon.drawableResId: Int
    get() = when (this) {
        TrafficOverlayIcon.Aircraft -> R.drawable.ic_traffic_airplane_map
        TrafficOverlayIcon.Helicopter -> R.drawable.ic_traffic_helicopter_map
        TrafficOverlayIcon.Drone -> R.drawable.ic_traffic_drone_map
        TrafficOverlayIcon.Fanet -> R.drawable.ic_traffic_freeflight_map
        TrafficOverlayIcon.Flarm -> R.drawable.ic_traffic_glider_map
        TrafficOverlayIcon.FreeFlight -> R.drawable.ic_traffic_freeflight_map
        TrafficOverlayIcon.Unknown -> R.drawable.ic_traffic_airplane_map
    }

private fun TrafficTarget.trafficOverlayIcon(): TrafficOverlayIcon =
    when (trafficFeedType()) {
        TrafficFeedType.FANET -> TrafficOverlayIcon.Fanet
        TrafficFeedType.FLARM -> TrafficOverlayIcon.Flarm
        TrafficFeedType.FREEFLIGHT -> TrafficOverlayIcon.FreeFlight
        TrafficFeedType.ADSB,
        TrafficFeedType.UNKNOWN -> when (trafficTargetKind()) {
            TrafficTargetKind.DRONE -> TrafficOverlayIcon.Drone
            TrafficTargetKind.HELICOPTER -> TrafficOverlayIcon.Helicopter
            TrafficTargetKind.AIRCRAFT -> TrafficOverlayIcon.Aircraft
        }
    }

private fun TrafficTarget.trafficOverlayColor(): ComposeColor =
    ComposeColor(android.graphics.Color.parseColor(trafficAltitudeBand().overlayColorHex()))

private fun TrafficAltitudeBand.overlayColorHex(): String =
    when (this) {
        TrafficAltitudeBand.VERY_LOW -> TRAFFIC_ALTITUDE_VERY_LOW_COLOR
        TrafficAltitudeBand.LOW -> TRAFFIC_ALTITUDE_LOW_COLOR
        TrafficAltitudeBand.HIGH -> TRAFFIC_ALTITUDE_HIGH_COLOR
        TrafficAltitudeBand.UNKNOWN -> TRAFFIC_AWARENESS_COLOR
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
        MapLayerIds.TRAFFIC_AWARENESS_SYMBOL_LAYER_ID
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
        MapLayerIds.TRAFFIC_AWARENESS_SYMBOL_LAYER_ID
    )
    val markerLayerCreated = style.addLayerIfMissing(
        MapLayerIds.TRAFFIC_AWARENESS_MARKER_LAYER_ID,
        CircleLayer(
            MapLayerIds.TRAFFIC_AWARENESS_MARKER_LAYER_ID,
            MapLayerIds.TRAFFIC_AWARENESS_SOURCE_ID
        ).withProperties(
            circleRadius(7.0f),
            circleColor(trafficVectorColorExpression()),
            circleOpacity(0.92f),
            circleStrokeColor("#ffffff"),
            circleStrokeWidth(2.2f),
            circleStrokeOpacity(0.96f)
        )
    )
    val glyphHaloLayerCreated = style.addLayerIfMissing(
        MapLayerIds.TRAFFIC_AWARENESS_GLYPH_HALO_LAYER_ID,
        LineLayer(
            MapLayerIds.TRAFFIC_AWARENESS_GLYPH_HALO_LAYER_ID,
            MapLayerIds.TRAFFIC_AWARENESS_GLYPH_SOURCE_ID
        ).withProperties(
            lineColor("#ffffff"),
            lineOpacity(0.92f),
            lineWidth(9.0f),
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
            lineWidth(4.4f),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    val symbolLayerCreated = addTrafficSymbolLayers(style)
    val altitudeLabelLayerCreated = style.addLayerIfMissing(
        MapLayerIds.TRAFFIC_AWARENESS_ALTITUDE_LABEL_LAYER_ID,
        SymbolLayer(
            MapLayerIds.TRAFFIC_AWARENESS_ALTITUDE_LABEL_LAYER_ID,
            MapLayerIds.TRAFFIC_AWARENESS_SOURCE_ID
        ).withProperties(
            textField(Expression.get(TrafficAwarenessMapProperties.AltitudeLabel)),
            textSize(TrafficMapStyle.AltitudeLabelTextSize),
            textOffset(arrayOf(0.0f, 2.0f)),
            textColor("#1f2933"),
            textHaloColor("#ffffff"),
            textHaloWidth(1.8f),
            textAllowOverlap(true),
            textIgnorePlacement(true)
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
        glyphHaloLayerCreated ||
        glyphLayerCreated ||
        symbolLayerCreated ||
        altitudeLabelLayerCreated
    ) {
        DscLogger.debug(
            TrafficAwarenessLogTag,
                "map install sourceCreated=$targetSourceCreated symbolLayerCreated=$symbolLayerCreated " +
                "attentionHaloLayerCreated=$attentionHaloLayerCreated " +
                "vectorLayerCreated=$vectorLayerCreated markerLayerCreated=$markerLayerCreated " +
                "glyphLayerCreated=${glyphHaloLayerCreated || glyphLayerCreated} " +
                "altitudeLabelLayerCreated=$altitudeLabelLayerCreated " +
                "radiusSourceCreated=$radiusSourceCreated radiusLayersCreated=${radiusFillLayerCreated || radiusLineLayerCreated}"
        )
    }
    if (targetSourceCreated || markerLayerCreated || symbolLayerCreated) {
        DscLogger.debug(
            TrafficAwarenessLogTag,
            "map traffic layers ready marker=${style.getLayer(MapLayerIds.TRAFFIC_AWARENESS_MARKER_LAYER_ID) != null} " +
                "symbols=${trafficSymbolLayerStyles().count { style.getLayer(it.layerId) != null }}/${trafficSymbolLayerStyles().size} " +
                "source=${style.getSourceAs<GeoJsonSource>(MapLayerIds.TRAFFIC_AWARENESS_SOURCE_ID) != null} " +
                "vectorSource=${style.getSourceAs<GeoJsonSource>(MapLayerIds.TRAFFIC_AWARENESS_VECTOR_SOURCE_ID) != null} " +
                "glyph=${style.getLayer(MapLayerIds.TRAFFIC_AWARENESS_GLYPH_LAYER_ID) != null}"
        )
    }
}

private fun addTrafficSymbolLayers(style: Style): Boolean {
    var created = false
    trafficSymbolLayerStyles().forEach { iconStyle ->
        created = style.addLayerIfMissing(
            iconStyle.layerId,
            SymbolLayer(
                iconStyle.layerId,
                MapLayerIds.TRAFFIC_AWARENESS_SOURCE_ID
            ).withProperties(
                iconImage(iconStyle.imageId),
                iconRotate(Expression.get(TrafficAwarenessMapProperties.RotationDeg)),
                iconSize(TrafficMapStyle.AircraftIconScale),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
            ).withFilter(
                Expression.eq(
                    Expression.get(TrafficAwarenessMapProperties.IconImage),
                    Expression.literal(iconStyle.imageId)
                )
            )
        ) || created
    }
    return created
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
        trafficAircraftGlyphFeatureCollection(targets)
    } else {
        emptyTrafficFeatureCollection()
    }
    style.setGeoJsonSourceIfAvailable(
        MapLayerIds.TRAFFIC_AWARENESS_GLYPH_SOURCE_ID,
        glyphFeatures
    )
    if (enabled || targets.isNotEmpty()) {
        val targetFeatureList = targetFeatures.features().orEmpty()
        val iconCounts = targetFeatureList
            .mapNotNull { feature -> feature.properties()?.stringValue(TrafficAwarenessMapProperties.IconImage) }
            .groupingBy { it }
            .eachCount()
            .entries
            .joinToString(limit = 6) { (iconId, count) -> "$iconId=$count" }
        val firstFeature = targetFeatureList.firstOrNull()
        val firstProperties = firstFeature?.properties()
        DscLogger.debug(
            TrafficAwarenessLogTag,
            "map traffic render update enabled=$enabled targets=${targets.size} " +
                "pointFeatures=${targetFeatureList.size} vectorFeatures=${vectorFeatures.features().orEmpty().size} " +
                "glyphFeatures=${glyphFeatures.features().orEmpty().size} " +
                "targetSourceUpdated=$targetUpdated targetLayer=${style.getLayer(MapLayerIds.TRAFFIC_AWARENESS_MARKER_LAYER_ID) != null} " +
                "symbolLayer=${style.getLayer(MapLayerIds.TRAFFIC_AWARENESS_SYMBOL_LAYER_ID) != null} " +
                "glyphLayer=${style.getLayer(MapLayerIds.TRAFFIC_AWARENESS_GLYPH_LAYER_ID) != null} " +
                "firstId=${firstProperties?.stringValue(TrafficAwarenessMapProperties.TargetId) ?: "none"} " +
                "firstIcon=${firstProperties?.stringValue(TrafficAwarenessMapProperties.IconImage) ?: "none"} " +
                "firstBand=${firstProperties?.stringValue(TrafficAwarenessMapProperties.AltitudeBand) ?: "none"} " +
                "firstFeed=${firstProperties?.stringValue(TrafficAwarenessMapProperties.FeedType) ?: "none"} " +
                "iconCounts=$iconCounts"
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

private fun createTrafficHelicopterIcon(fillColor: String): Bitmap {
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
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val cx = TRAFFIC_AWARENESS_ICON_SIZE_PX / 2f
    val cy = TRAFFIC_AWARENESS_ICON_SIZE_PX / 2f

    val body = android.graphics.Path().apply {
        moveTo(cx, 8f)
        cubicTo(cx + 8f, 15f, cx + 9f, 36f, cx + 3f, 43f)
        lineTo(cx + 3f, 50f)
        lineTo(cx - 3f, 50f)
        lineTo(cx - 3f, 43f)
        cubicTo(cx - 9f, 36f, cx - 8f, 15f, cx, 8f)
        close()
    }
    val rotor = android.graphics.Path().apply {
        moveTo(8f, cy - 2f)
        lineTo(48f, cy + 2f)
        moveTo(48f, cy - 2f)
        lineTo(8f, cy + 2f)
    }
    val tail = android.graphics.Path().apply {
        moveTo(cx, 43f)
        lineTo(cx, 55f)
        moveTo(cx - 7f, 55f)
        lineTo(cx + 7f, 55f)
    }

    canvas.drawPath(body, paint)
    canvas.drawPath(body, stroke)
    canvas.drawPath(rotor, stroke)
    canvas.drawPath(tail, stroke)
    return bitmap
}

private fun createTrafficFeedIcon(feedType: TrafficFeedType, fillColor: String): Bitmap {
    val bitmap = Bitmap.createBitmap(TRAFFIC_AWARENESS_ICON_SIZE_PX, TRAFFIC_AWARENESS_ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(fillColor)
        style = Paint.Style.FILL
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val cx = TRAFFIC_AWARENESS_ICON_SIZE_PX / 2f
    val cy = TRAFFIC_AWARENESS_ICON_SIZE_PX / 2f

    when (feedType) {
        TrafficFeedType.FANET -> {
            val wing = android.graphics.Path().apply {
                moveTo(8f, 25f)
                cubicTo(16f, 10f, 40f, 10f, 48f, 25f)
                cubicTo(38f, 20f, 18f, 20f, 8f, 25f)
                close()
            }
            canvas.drawPath(wing, paint)
            canvas.drawPath(wing, stroke)
            canvas.drawLine(cx, 26f, cx, 47f, stroke)
            canvas.drawLine(20f, 34f, cx, 47f, stroke)
            canvas.drawLine(36f, 34f, cx, 47f, stroke)
        }
        TrafficFeedType.FLARM -> {
            val glider = android.graphics.Path().apply {
                moveTo(cx, 8f)
                lineTo(cx + 5f, 27f)
                lineTo(52f, 31f)
                lineTo(52f, 36f)
                lineTo(cx + 3f, 35f)
                lineTo(cx + 2f, 48f)
                lineTo(cx + 10f, 51f)
                lineTo(cx + 10f, 55f)
                lineTo(cx, 53f)
                lineTo(cx - 10f, 55f)
                lineTo(cx - 10f, 51f)
                lineTo(cx - 2f, 48f)
                lineTo(cx - 3f, 35f)
                lineTo(4f, 36f)
                lineTo(4f, 31f)
                lineTo(cx - 5f, 27f)
                close()
            }
            canvas.drawPath(glider, paint)
            canvas.drawPath(glider, stroke)
        }
        TrafficFeedType.FREEFLIGHT -> {
            val marker = android.graphics.Path().apply {
                moveTo(cx, 6f)
                cubicTo(42f, 10f, 49f, 20f, 45f, 31f)
                cubicTo(41f, 42f, 31f, 47f, cx, 54f)
                cubicTo(25f, 47f, 15f, 42f, 11f, 31f)
                cubicTo(7f, 20f, 14f, 10f, cx, 6f)
                close()
            }
            canvas.drawPath(marker, paint)
            canvas.drawPath(marker, stroke)
            canvas.drawLine(cx, 12f, cx, 48f, stroke)
        }
        TrafficFeedType.ADSB,
        TrafficFeedType.UNKNOWN -> return createTrafficAircraftIcon(fillColor)
    }
    return bitmap
}

private fun createTrafficDroneIcon(context: Context): Bitmap {
    val bitmap = Bitmap.createBitmap(TRAFFIC_AWARENESS_ICON_SIZE_PX, TRAFFIC_AWARENESS_ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val source = BitmapFactory.decodeResource(context.resources, R.drawable.dsc_traffic_drone)
    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(TRAFFIC_AWARENESS_DRONE_COLOR)
        style = Paint.Style.FILL
        alpha = 82
    }
    val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val cx = TRAFFIC_AWARENESS_ICON_SIZE_PX / 2.0f
    val cy = TRAFFIC_AWARENESS_ICON_SIZE_PX / 2.0f
    val half = TRAFFIC_AWARENESS_DRONE_PNG_SIZE_PX / 2.0f
    val destination = RectF(cx - half, cy - half, cx + half, cy + half)

    canvas.drawCircle(cx, cy, TRAFFIC_AWARENESS_DRONE_HALO_RADIUS_PX, haloPaint)
    if (source != null) {
        canvas.drawBitmap(source, null, destination, imagePaint)
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
        Expression.literal(DscZoneMapColors.limited25m.webHex),
        Expression.literal(TrafficAltitudeBand.LOW.name),
        Expression.literal(DscZoneMapColors.limited60m.webHex),
        Expression.literal(TrafficAltitudeBand.HIGH.name),
        Expression.literal(TRAFFIC_AWARENESS_HIGH_ALTITUDE_COLOR),
        Expression.literal(TRAFFIC_AWARENESS_COLOR)
    )

private data class TrafficSymbolLayerStyle(
    val layerId: String,
    val imageId: String
)

private fun trafficSymbolLayerIds(): Array<String> =
    (listOf(
        MapLayerIds.TRAFFIC_AWARENESS_MARKER_LAYER_ID,
        MapLayerIds.TRAFFIC_AWARENESS_VECTOR_LAYER_ID,
        MapLayerIds.TRAFFIC_AWARENESS_GLYPH_LAYER_ID,
        MapLayerIds.TRAFFIC_AWARENESS_GLYPH_HALO_LAYER_ID
    ) + trafficSymbolLayerStyles()
        .map { it.layerId }
    ).toTypedArray()

private fun trafficSymbolLayerStyles(): List<TrafficSymbolLayerStyle> {
    val droneStyle = TrafficSymbolLayerStyle(
        layerId = MapLayerIds.TRAFFIC_AWARENESS_SYMBOL_LAYER_ID,
        imageId = TrafficMapIconIds.Drone
    )
    val aircraftStyles = TrafficAircraftIconStyle.entries.map { it.toSymbolLayerStyle() }
    val helicopterStyles = TrafficHelicopterIconStyle.entries.map { it.toSymbolLayerStyle() }
    val feedStyles = TrafficFeedIconStyle.entries.map { it.toSymbolLayerStyle() }
    return listOf(droneStyle) + aircraftStyles + helicopterStyles + feedStyles
}

private fun TrafficAircraftIconStyle.toSymbolLayerStyle(): TrafficSymbolLayerStyle =
    TrafficSymbolLayerStyle(
        layerId = "${MapLayerIds.TRAFFIC_AWARENESS_SYMBOL_LAYER_ID}-${imageId}",
        imageId = imageId
    )

private fun TrafficHelicopterIconStyle.toSymbolLayerStyle(): TrafficSymbolLayerStyle =
    TrafficSymbolLayerStyle(
        layerId = "${MapLayerIds.TRAFFIC_AWARENESS_SYMBOL_LAYER_ID}-${imageId}",
        imageId = imageId
    )

private fun TrafficFeedIconStyle.toSymbolLayerStyle(): TrafficSymbolLayerStyle =
    TrafficSymbolLayerStyle(
        layerId = "${MapLayerIds.TRAFFIC_AWARENESS_SYMBOL_LAYER_ID}-${imageId}",
        imageId = imageId
    )

private enum class TrafficAircraftIconStyle(
    val imageId: String,
    val fillColor: String
) {
    VeryLow(TrafficMapIconIds.aircraft(TrafficAltitudeBand.VERY_LOW), DscZoneMapColors.limited25m.webHex),
    Low(TrafficMapIconIds.aircraft(TrafficAltitudeBand.LOW), DscZoneMapColors.limited60m.webHex),
    High(TrafficMapIconIds.aircraft(TrafficAltitudeBand.HIGH), TRAFFIC_AWARENESS_HIGH_ALTITUDE_COLOR),
    Unknown(TrafficMapIconIds.aircraft(TrafficAltitudeBand.UNKNOWN), TRAFFIC_AWARENESS_COLOR)
}

private enum class TrafficHelicopterIconStyle(
    val imageId: String,
    val fillColor: String
) {
    VeryLow(TrafficMapIconIds.helicopter(TrafficAltitudeBand.VERY_LOW), DscZoneMapColors.limited25m.webHex),
    Low(TrafficMapIconIds.helicopter(TrafficAltitudeBand.LOW), DscZoneMapColors.limited60m.webHex),
    High(TrafficMapIconIds.helicopter(TrafficAltitudeBand.HIGH), TRAFFIC_AWARENESS_HIGH_ALTITUDE_COLOR),
    Unknown(TrafficMapIconIds.helicopter(TrafficAltitudeBand.UNKNOWN), TRAFFIC_AWARENESS_COLOR)
}

private enum class TrafficFeedIconStyle(
    val feedType: TrafficFeedType,
    val altitudeBand: TrafficAltitudeBand,
    val imageId: String,
    val fillColor: String
) {
    FanetVeryLow(TrafficFeedType.FANET, TrafficAltitudeBand.VERY_LOW, TrafficMapIconIds.feed(TrafficFeedType.FANET, TrafficAltitudeBand.VERY_LOW), DscZoneMapColors.limited25m.webHex),
    FanetLow(TrafficFeedType.FANET, TrafficAltitudeBand.LOW, TrafficMapIconIds.feed(TrafficFeedType.FANET, TrafficAltitudeBand.LOW), DscZoneMapColors.limited60m.webHex),
    FanetHigh(TrafficFeedType.FANET, TrafficAltitudeBand.HIGH, TrafficMapIconIds.feed(TrafficFeedType.FANET, TrafficAltitudeBand.HIGH), TRAFFIC_AWARENESS_HIGH_ALTITUDE_COLOR),
    FanetUnknown(TrafficFeedType.FANET, TrafficAltitudeBand.UNKNOWN, TrafficMapIconIds.feed(TrafficFeedType.FANET, TrafficAltitudeBand.UNKNOWN), TRAFFIC_AWARENESS_COLOR),
    FlarmVeryLow(TrafficFeedType.FLARM, TrafficAltitudeBand.VERY_LOW, TrafficMapIconIds.feed(TrafficFeedType.FLARM, TrafficAltitudeBand.VERY_LOW), DscZoneMapColors.limited25m.webHex),
    FlarmLow(TrafficFeedType.FLARM, TrafficAltitudeBand.LOW, TrafficMapIconIds.feed(TrafficFeedType.FLARM, TrafficAltitudeBand.LOW), DscZoneMapColors.limited60m.webHex),
    FlarmHigh(TrafficFeedType.FLARM, TrafficAltitudeBand.HIGH, TrafficMapIconIds.feed(TrafficFeedType.FLARM, TrafficAltitudeBand.HIGH), TRAFFIC_AWARENESS_HIGH_ALTITUDE_COLOR),
    FlarmUnknown(TrafficFeedType.FLARM, TrafficAltitudeBand.UNKNOWN, TrafficMapIconIds.feed(TrafficFeedType.FLARM, TrafficAltitudeBand.UNKNOWN), TRAFFIC_AWARENESS_COLOR),
    FreeFlightVeryLow(TrafficFeedType.FREEFLIGHT, TrafficAltitudeBand.VERY_LOW, TrafficMapIconIds.feed(TrafficFeedType.FREEFLIGHT, TrafficAltitudeBand.VERY_LOW), DscZoneMapColors.limited25m.webHex),
    FreeFlightLow(TrafficFeedType.FREEFLIGHT, TrafficAltitudeBand.LOW, TrafficMapIconIds.feed(TrafficFeedType.FREEFLIGHT, TrafficAltitudeBand.LOW), DscZoneMapColors.limited60m.webHex),
    FreeFlightHigh(TrafficFeedType.FREEFLIGHT, TrafficAltitudeBand.HIGH, TrafficMapIconIds.feed(TrafficFeedType.FREEFLIGHT, TrafficAltitudeBand.HIGH), TRAFFIC_AWARENESS_HIGH_ALTITUDE_COLOR),
    FreeFlightUnknown(TrafficFeedType.FREEFLIGHT, TrafficAltitudeBand.UNKNOWN, TrafficMapIconIds.feed(TrafficFeedType.FREEFLIGHT, TrafficAltitudeBand.UNKNOWN), TRAFFIC_AWARENESS_COLOR)
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
private const val TRAFFIC_AWARENESS_DRONE_PNG_SIZE_PX = 34.0f
private const val TRAFFIC_AWARENESS_DRONE_HALO_RADIUS_PX = 21.0f
private const val TRAFFIC_AWARENESS_COLOR = "#455a64"
private const val TRAFFIC_ALTITUDE_VERY_LOW_COLOR = "#FFC928"
private const val TRAFFIC_ALTITUDE_LOW_COLOR = "#32D4E8"
private const val TRAFFIC_ALTITUDE_HIGH_COLOR = "#8FA9C4"
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
    const val AltitudeLabelTextSize = 12.0f
}

private object TrafficOverlayStyle {
    val MarkerWidth = 96.dp
    val MarkerHeight = 76.dp
    val IconHaloSize = 54.dp
    val IconSize = 46.dp
    val IconCenterY = 23.dp
}
private val MAIN_HANDLER = Handler(Looper.getMainLooper())
private val DSC_GEOJSON_EXECUTOR = Executors.newFixedThreadPool(3)
