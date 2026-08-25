package it.droneskycheck.app.data.weatherMap

import java.time.Duration
import java.time.Instant
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

object WeatherMapDefaults {
    const val SchemaVersion = 1
    const val ModeOperational = "operational"
    const val ExpectedRows = 9
    const val ExpectedCols = 9
    const val ExpectedNodeCount = ExpectedRows * ExpectedCols
    const val ExpectedTimeSteps = 72
    const val CacheMaxEntries = 8
    val TimeMatchTolerance: Duration = Duration.ofMinutes(45)
}

data class WeatherMapForecast(
    val schemaVersion: Int,
    val mode: String,
    val requestedCenter: WeatherMapCoordinates?,
    val grid: WeatherMapGrid,
    val units: WeatherMapUnits,
    val times: List<Instant>,
    val nodes: List<WeatherMapNode>,
    val windSpeedKmh: List<Double?>,
    val windDirectionDegrees: List<Double?>,
    val windGustsKmh: List<Double?>,
    val cache: WeatherMapCache?
) {
    fun valueIndex(timeIndex: Int, nodeIndex: Int): Int {
        require(timeIndex in times.indices) { "Invalid weather map timeIndex $timeIndex" }
        require(nodeIndex in 0 until grid.nodeCount) { "Invalid weather map nodeIndex $nodeIndex" }
        return timeIndex * grid.nodeCount + nodeIndex
    }

    fun windSampleAt(timeIndex: Int, nodeIndex: Int): WeatherMapWindSample? {
        val index = valueIndex(timeIndex, nodeIndex)
        val speed = windSpeedKmh.getOrNull(index) ?: return null
        val direction = windDirectionDegrees.getOrNull(index) ?: return null
        if (!speed.isFinite() || !direction.isFinite()) return null
        return WeatherMapWindSample(
            windSpeedKmh = speed,
            windDirectionDegrees = direction,
            displayDirectionDegrees = meteorologicalToDisplayDirection(direction),
            windGustsKmh = windGustsKmh.getOrNull(index)
        )
    }

    fun nearestTimeIndex(
        target: Instant?,
        tolerance: Duration = WeatherMapDefaults.TimeMatchTolerance
    ): Int? {
        val instant = target ?: return null
        val toleranceMillis = tolerance.toMillis()
        return times.withIndex()
            .minByOrNull { (_, time) -> kotlin.math.abs(Duration.between(time, instant).toMillis()) }
            ?.takeIf { (_, time) -> kotlin.math.abs(Duration.between(time, instant).toMillis()) <= toleranceMillis }
            ?.index
    }

    fun windFieldFor(
        selectedTime: Instant?,
        zoom: Double,
        tolerance: Duration = WeatherMapDefaults.TimeMatchTolerance
    ): WeatherWindField? {
        val timeIndex = nearestTimeIndex(selectedTime, tolerance) ?: return null
        val density = WeatherWindDensity.forZoom(zoom)
        val vectors = nodes.mapIndexedNotNull { nodeIndex, node ->
            if (!density.includes(nodeIndex, grid.rows, grid.cols)) return@mapIndexedNotNull null
            val sample = windSampleAt(timeIndex, nodeIndex) ?: return@mapIndexedNotNull null
            WeatherWindVector(
                nodeIndex = nodeIndex,
                lat = node.lat,
                lon = node.lon,
                windSpeedKmh = sample.windSpeedKmh,
                windDirectionDegrees = sample.windDirectionDegrees,
                displayDirectionDegrees = sample.displayDirectionDegrees,
                colorHex = windSpeedColorHex(sample.windSpeedKmh)
            )
        }
        if (vectors.isEmpty()) return null
        return WeatherWindField(
            selectedTime = times[timeIndex],
            timeIndex = timeIndex,
            density = density,
            vectors = vectors
        )
    }
}

data class WeatherMapGrid(
    val centerLat: Double?,
    val centerLon: Double?,
    val rows: Int,
    val cols: Int,
    val nodeCount: Int,
    val stepKm: Double?,
    val widthKm: Double?,
    val heightKm: Double?
)

data class WeatherMapCoordinates(
    val lat: Double,
    val lon: Double
)

data class WeatherMapNode(
    val lat: Double,
    val lon: Double,
    val providerLat: Double?,
    val providerLon: Double?,
    val elevationMeters: Double?
)

data class WeatherMapUnits(
    val windSpeedKmh: String?,
    val windDirectionDegrees: String?,
    val windGustsKmh: String?
)

data class WeatherMapCache(
    val hit: Boolean?,
    val ageMs: Long?,
    val ttlMs: Long?
)

data class WeatherMapWindSample(
    val windSpeedKmh: Double,
    val windDirectionDegrees: Double,
    val displayDirectionDegrees: Double,
    val windGustsKmh: Double?
)

data class WeatherWindField(
    val selectedTime: Instant,
    val timeIndex: Int,
    val density: WeatherWindDensity,
    val vectors: List<WeatherWindVector>
)

data class WeatherWindVector(
    val nodeIndex: Int,
    val lat: Double,
    val lon: Double,
    val windSpeedKmh: Double,
    val windDirectionDegrees: Double,
    val displayDirectionDegrees: Double,
    val colorHex: String
)

enum class WeatherWindDensity(val label: String, val expectedGridSize: Int) {
    Low("5x5", 5),
    Medium("7x7", 7),
    High("9x9", 9);

    fun includes(nodeIndex: Int, rows: Int, cols: Int): Boolean {
        if (rows <= 0 || cols <= 0) return false
        val row = nodeIndex / cols
        val col = nodeIndex % cols
        val rowIndexes = sampledIndexes(rows, expectedGridSize)
        val colIndexes = sampledIndexes(cols, expectedGridSize)
        return row in rowIndexes && col in colIndexes
    }

    companion object {
        fun forZoom(zoom: Double): WeatherWindDensity =
            when {
                zoom < 9.25 -> Low
                zoom < 10.5 -> Medium
                else -> High
            }
    }
}

private fun sampledIndexes(total: Int, desired: Int): Set<Int> {
    if (desired >= total) return (0 until total).toSet()
    if (desired <= 1) return setOf(0)
    val last = total - 1
    return (0 until desired)
        .map { index -> kotlin.math.round(index * last.toDouble() / (desired - 1)).toInt() }
        .toSet()
}

sealed class WeatherMapMappingError(message: String) : Exception(message) {
    class UnsupportedSchemaVersion(val schemaVersion: Int) :
        WeatherMapMappingError("Unsupported weather map schemaVersion $schemaVersion")

    class UnsupportedMode(val mode: String) :
        WeatherMapMappingError("Unsupported weather map mode $mode")

    object InvalidGrid :
        WeatherMapMappingError("Invalid weather map grid")

    object InvalidNodes :
        WeatherMapMappingError("Invalid weather map nodes")

    object InvalidTimes :
        WeatherMapMappingError("Invalid weather map times")

    object InsufficientWindData :
        WeatherMapMappingError("Insufficient weather map wind arrays")
}

fun parseWeatherMapResponse(json: JSONObject): WeatherMapForecast {
    val schemaVersion = json.optInt("schemaVersion", 0)
    if (schemaVersion != WeatherMapDefaults.SchemaVersion) {
        throw WeatherMapMappingError.UnsupportedSchemaVersion(schemaVersion)
    }

    val mode = json.optStringOrNull("mode").orEmpty()
    if (!mode.equals(WeatherMapDefaults.ModeOperational, ignoreCase = true)) {
        throw WeatherMapMappingError.UnsupportedMode(mode)
    }

    val gridJson = json.optJSONObject("grid") ?: throw WeatherMapMappingError.InvalidGrid
    val rows = gridJson.optIntOrNull("rows") ?: WeatherMapDefaults.ExpectedRows
    val cols = gridJson.optIntOrNull("cols") ?: WeatherMapDefaults.ExpectedCols
    val nodeCount = gridJson.optIntOrNull("nodeCount") ?: (rows * cols)
    if (rows != WeatherMapDefaults.ExpectedRows ||
        cols != WeatherMapDefaults.ExpectedCols ||
        nodeCount != WeatherMapDefaults.ExpectedNodeCount
    ) {
        throw WeatherMapMappingError.InvalidGrid
    }

    val nodes = json.optJSONArray("nodes").toWeatherMapNodes()
    if (nodes.size != nodeCount) {
        throw WeatherMapMappingError.InvalidNodes
    }

    val times = json.optJSONArray("times").toInstants()
    if (times.size != WeatherMapDefaults.ExpectedTimeSteps) {
        throw WeatherMapMappingError.InvalidTimes
    }

    val data = json.optJSONObject("data") ?: throw WeatherMapMappingError.InsufficientWindData
    val expectedValues = times.size * nodeCount
    val windSpeed = data.optJSONArray("windSpeed").toNullableDoubleList()
    val windDirection = data.optJSONArray("windDirection").toNullableDoubleList()
    val windGusts = data.optJSONArray("windGusts").toNullableDoubleList()
    if (windSpeed.size < expectedValues || windDirection.size < expectedValues || windGusts.size < expectedValues) {
        throw WeatherMapMappingError.InsufficientWindData
    }

    return WeatherMapForecast(
        schemaVersion = schemaVersion,
        mode = mode,
        requestedCenter = json.optJSONObject("requestedCenter")?.toWeatherMapCoordinates(),
        grid = WeatherMapGrid(
            centerLat = gridJson.optDoubleOrNull("centerLat"),
            centerLon = gridJson.optDoubleOrNull("centerLon"),
            rows = rows,
            cols = cols,
            nodeCount = nodeCount,
            stepKm = gridJson.optDoubleOrNull("stepKm"),
            widthKm = gridJson.optDoubleOrNull("widthKm"),
            heightKm = gridJson.optDoubleOrNull("heightKm")
        ),
        units = json.optJSONObject("units").toWeatherMapUnits(),
        times = times,
        nodes = nodes,
        windSpeedKmh = windSpeed.take(expectedValues),
        windDirectionDegrees = windDirection.take(expectedValues),
        windGustsKmh = windGusts.take(expectedValues),
        cache = json.optJSONObject("cache")?.let {
            WeatherMapCache(
                hit = it.optBooleanOrNull("hit"),
                ageMs = it.optLongOrNull("ageMs"),
                ttlMs = it.optLongOrNull("ttlMs")
            )
        }
    )
}

fun meteorologicalToDisplayDirection(windDirectionDegrees: Double): Double {
    val normalized = windDirectionDegrees.modDegrees()
    return (normalized + 180.0).modDegrees()
}

fun windSpeedColorHex(speedKmh: Double): String =
    when {
        speedKmh < 10.0 -> "#4FC3F7"
        speedKmh < 20.0 -> "#66BB6A"
        speedKmh < 30.0 -> "#FDD835"
        speedKmh < 40.0 -> "#FB8C00"
        else -> "#E53935"
    }

fun weatherWindFieldToFeatureCollection(field: WeatherWindField?): FeatureCollection {
    if (field == null) return FeatureCollection.fromFeatures(emptyList())
    return FeatureCollection.fromFeatures(
        field.vectors.flatMap { vector ->
            vector.toArrowFeatures()
        }
    )
}

private fun WeatherWindVector.toArrowFeatures(): List<Feature> {
    val tip = destinationPoint(lat, lon, displayDirectionDegrees, WeatherArrowShaftKm)
    val left = destinationPoint(tip.lat, tip.lon, displayDirectionDegrees + 150.0, WeatherArrowHeadKm)
    val right = destinationPoint(tip.lat, tip.lon, displayDirectionDegrees - 150.0, WeatherArrowHeadKm)
    val base = WeatherMapCoordinates(lat = lat, lon = lon)
    return listOf(
        arrowFeature(base, tip, "shaft"),
        arrowFeature(tip, left, "head-left"),
        arrowFeature(tip, right, "head-right")
    )
}

private fun WeatherWindVector.arrowFeature(
    start: WeatherMapCoordinates,
    end: WeatherMapCoordinates,
    part: String
): Feature =
    Feature.fromGeometry(
        LineString.fromLngLats(
            listOf(
                Point.fromLngLat(start.lon, start.lat),
                Point.fromLngLat(end.lon, end.lat)
            )
        )
    ).apply {
        addNumberProperty(WeatherWindMapProperties.NodeIndex, nodeIndex)
        addNumberProperty(WeatherWindMapProperties.WindSpeed, windSpeedKmh)
        addNumberProperty(WeatherWindMapProperties.WindDirection, windDirectionDegrees)
        addNumberProperty(WeatherWindMapProperties.DisplayDirection, displayDirectionDegrees)
        addStringProperty(WeatherWindMapProperties.Color, colorHex)
        addStringProperty(WeatherWindMapProperties.Part, part)
    }

object WeatherWindMapProperties {
    const val NodeIndex = "weatherNodeIndex"
    const val WindSpeed = "windSpeedKmh"
    const val WindDirection = "windDirectionDegrees"
    const val DisplayDirection = "displayDirectionDegrees"
    const val Color = "weatherWindColor"
    const val Part = "weatherWindPart"
}

private fun JSONArray?.toWeatherMapNodes(): List<WeatherMapNode> {
    if (this == null) return emptyList()
    return (0 until length()).map { index ->
        val node = optJSONObject(index) ?: throw WeatherMapMappingError.InvalidNodes
        WeatherMapNode(
            lat = node.optDoubleOrNull("lat")?.takeIfValidLatitude() ?: throw WeatherMapMappingError.InvalidNodes,
            lon = node.optDoubleOrNull("lon")?.takeIfValidLongitude() ?: throw WeatherMapMappingError.InvalidNodes,
            providerLat = node.optDoubleOrNull("providerLat")?.takeIfValidLatitude(),
            providerLon = node.optDoubleOrNull("providerLon")?.takeIfValidLongitude(),
            elevationMeters = node.optDoubleOrNull("elevationMeters")
        )
    }
}

private fun JSONArray?.toInstants(): List<Instant> {
    if (this == null) return emptyList()
    return (0 until length()).map { index ->
        val value = optString(index, null) ?: throw WeatherMapMappingError.InvalidTimes
        runCatching { Instant.parse(value) }.getOrElse { throw WeatherMapMappingError.InvalidTimes }
    }
}

private fun JSONArray?.toNullableDoubleList(): List<Double?> {
    if (this == null) return emptyList()
    return (0 until length()).map { index ->
        if (isNull(index)) null else when (val value = opt(index)) {
            is Number -> value.toDouble().takeIf { it.isFinite() }
            is String -> value.toDoubleOrNull()?.takeIf { it.isFinite() }
            else -> null
        }
    }
}

private fun JSONObject?.toWeatherMapUnits(): WeatherMapUnits {
    val json = this
    return WeatherMapUnits(
        windSpeedKmh = json?.optStringOrNull("windSpeedKmh"),
        windDirectionDegrees = json?.optStringOrNull("windDirectionDegrees"),
        windGustsKmh = json?.optStringOrNull("windGustsKmh")
    )
}

private fun JSONObject.toWeatherMapCoordinates(): WeatherMapCoordinates? {
    val lat = optDoubleOrNull("lat")?.takeIfValidLatitude() ?: return null
    val lon = optDoubleOrNull("lon")?.takeIfValidLongitude() ?: return null
    return WeatherMapCoordinates(lat = lat, lon = lon)
}

private fun destinationPoint(lat: Double, lon: Double, bearingDegrees: Double, distanceKm: Double): WeatherMapCoordinates {
    val angularDistance = distanceKm / EarthRadiusKm
    val bearing = Math.toRadians(bearingDegrees)
    val lat1 = Math.toRadians(lat)
    val lon1 = Math.toRadians(lon)
    val lat2 = asin(
        sin(lat1) * cos(angularDistance) +
            cos(lat1) * sin(angularDistance) * cos(bearing)
    )
    val lon2 = lon1 + atan2(
        sin(bearing) * sin(angularDistance) * cos(lat1),
        cos(angularDistance) - sin(lat1) * sin(lat2)
    )
    return WeatherMapCoordinates(
        lat = Math.toDegrees(lat2),
        lon = Math.toDegrees(lon2)
    )
}

private fun Double.modDegrees(): Double =
    ((this % 360.0) + 360.0) % 360.0

private fun Double.takeIfValidLatitude(): Double? =
    takeIf { it.isFinite() && it >= -90.0 && it <= 90.0 }

private fun Double.takeIfValidLongitude(): Double? =
    takeIf { it.isFinite() && it >= -180.0 && it <= 180.0 }

private fun JSONObject.optStringOrNull(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return opt(name)?.toString()?.trim()?.takeIf { it.isNotBlank() }
}

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    when {
        !has(name) || isNull(name) -> null
        opt(name) is Number -> optDouble(name)
        else -> optString(name).toDoubleOrNull()
    }?.takeIf { it.isFinite() }

private fun JSONObject.optIntOrNull(name: String): Int? =
    when {
        !has(name) || isNull(name) -> null
        opt(name) is Number -> optInt(name)
        else -> optString(name).toIntOrNull()
    }

private fun JSONObject.optBooleanOrNull(name: String): Boolean? =
    when {
        !has(name) || isNull(name) -> null
        opt(name) is Boolean -> optBoolean(name)
        else -> optString(name).toBooleanStrictOrNull()
    }

private fun JSONObject.optLongOrNull(name: String): Long? =
    when {
        !has(name) || isNull(name) -> null
        opt(name) is Number -> optLong(name)
        else -> optString(name).toLongOrNull()
    }

private const val EarthRadiusKm = 6371.0
private const val WeatherArrowShaftKm = 0.72
private const val WeatherArrowHeadKm = 0.22
