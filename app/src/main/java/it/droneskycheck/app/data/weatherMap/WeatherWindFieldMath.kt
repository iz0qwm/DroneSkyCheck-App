package it.droneskycheck.app.data.weatherMap

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

data class WeatherWindVectorComponents(
    val u: Double,
    val v: Double,
    val speedKmh: Double
)

data class WeatherLocalPoint(
    val xKm: Double,
    val yKm: Double
)

data class WeatherParticleSample(
    val u: Double,
    val v: Double,
    val speedKmh: Double
)

data class WeatherParticle(
    var xKm: Double,
    var yKm: Double,
    var previousXKm: Double,
    var previousYKm: Double,
    var age: Int,
    var maxAge: Int
)

data class WeatherParticleFrame(
    val xKm: Double,
    val yKm: Double,
    val previousXKm: Double,
    val previousYKm: Double,
    val speedKmh: Double
)

data class WeatherParticleVectorField(
    val originLat: Double,
    val originLon: Double,
    val rows: Int,
    val cols: Int,
    val minXKm: Double,
    val maxXKm: Double,
    val minYKm: Double,
    val maxYKm: Double,
    val vectors: List<WeatherWindVectorComponents>
) {
    fun contains(point: WeatherLocalPoint): Boolean =
        point.xKm in minXKm..maxXKm && point.yKm in minYKm..maxYKm

    fun toLocal(lat: Double, lon: Double): WeatherLocalPoint {
        val latKm = (lat - originLat) * KmPerDegreeLatitude
        val lonKm = (lon - originLon) * KmPerDegreeLatitude * cos(Math.toRadians(originLat))
        return WeatherLocalPoint(xKm = lonKm, yKm = latKm)
    }

    fun toLatLon(point: WeatherLocalPoint): WeatherMapCoordinates {
        val lat = originLat + point.yKm / KmPerDegreeLatitude
        val lon = originLon + point.xKm / (KmPerDegreeLatitude * cos(Math.toRadians(originLat)))
        return WeatherMapCoordinates(lat = lat, lon = lon)
    }

    fun sample(point: WeatherLocalPoint): WeatherParticleSample? {
        if (!contains(point) || rows < 2 || cols < 2 || vectors.size < rows * cols) return null
        val xRange = maxXKm - minXKm
        val yRange = maxYKm - minYKm
        if (xRange <= 0.0 || yRange <= 0.0) return null

        val colFloat = ((point.xKm - minXKm) / xRange) * (cols - 1)
        val rowFloat = ((point.yKm - minYKm) / yRange) * (rows - 1)
        val col0 = floor(colFloat).toInt().coerceIn(0, cols - 2)
        val row0 = floor(rowFloat).toInt().coerceIn(0, rows - 2)
        val colT = (colFloat - col0).coerceIn(0.0, 1.0)
        val rowT = (rowFloat - row0).coerceIn(0.0, 1.0)

        val sw = vector(row0, col0) ?: return null
        val se = vector(row0, col0 + 1) ?: return null
        val nw = vector(row0 + 1, col0) ?: return null
        val ne = vector(row0 + 1, col0 + 1) ?: return null
        val u = bilinear(sw.u, se.u, nw.u, ne.u, colT, rowT)
        val v = bilinear(sw.v, se.v, nw.v, ne.v, colT, rowT)
        val speed = bilinear(sw.speedKmh, se.speedKmh, nw.speedKmh, ne.speedKmh, colT, rowT)
        if (!u.isFinite() || !v.isFinite() || !speed.isFinite()) return null
        return WeatherParticleSample(u = u, v = v, speedKmh = speed)
    }

    private fun vector(row: Int, col: Int): WeatherWindVectorComponents? =
        vectors.getOrNull(row * cols + col)
}

class WeatherParticleEngine(
    private val particleCount: Int = DefaultParticleCount,
    private val random: Random = Random.Default
) {
    private val particles = MutableList(particleCount) { WeatherParticle(0.0, 0.0, 0.0, 0.0, 0, 1) }

    fun reset(field: WeatherParticleVectorField) {
        particles.forEach { it.respawn(field) }
    }

    fun step(field: WeatherParticleVectorField, deltaSeconds: Double): List<WeatherParticleFrame> {
        val frames = ArrayList<WeatherParticleFrame>(particles.size)
        particles.forEach { particle ->
            val sample = field.sample(WeatherLocalPoint(particle.xKm, particle.yKm))
            if (sample == null || particle.age >= particle.maxAge) {
                particle.respawn(field)
                return@forEach
            }

            particle.previousXKm = particle.xKm
            particle.previousYKm = particle.yKm
            val visualKm = deltaSeconds * ParticleSpeedScale
            particle.xKm += sample.u * visualKm
            particle.yKm += sample.v * visualKm
            particle.age += 1

            if (!field.contains(WeatherLocalPoint(particle.xKm, particle.yKm))) {
                particle.respawn(field)
            } else {
                frames += WeatherParticleFrame(
                    xKm = particle.xKm,
                    yKm = particle.yKm,
                    previousXKm = particle.previousXKm,
                    previousYKm = particle.previousYKm,
                    speedKmh = sample.speedKmh
                )
            }
        }
        return frames
    }

    fun snapshot(): List<WeatherParticle> = particles.map { it.copy() }

    private fun WeatherParticle.respawn(field: WeatherParticleVectorField) {
        xKm = random.nextDouble(field.minXKm, field.maxXKm)
        yKm = random.nextDouble(field.minYKm, field.maxYKm)
        previousXKm = xKm
        previousYKm = yKm
        age = 0
        maxAge = random.nextInt(MinParticleAge, MaxParticleAge + 1)
    }

    companion object {
        const val DefaultParticleCount = 190
        const val TargetFrameMillis = 33L
        const val ParticleSpeedScale = 0.10
        const val MinParticleAge = 35
        const val MaxParticleAge = 95
    }
}

fun meteorologicalWindToComponents(speedKmh: Double, directionDegrees: Double): WeatherWindVectorComponents? {
    if (!speedKmh.isFinite() || !directionDegrees.isFinite()) return null
    val directionRadians = Math.toRadians(directionDegrees)
    return WeatherWindVectorComponents(
        u = -speedKmh * sin(directionRadians),
        v = -speedKmh * cos(directionRadians),
        speedKmh = speedKmh
    )
}

fun WeatherMapForecast.particleVectorFieldFor(selectedTime: java.time.Instant?): WeatherParticleVectorField? {
    val timeIndex = nearestTimeIndex(selectedTime) ?: return null
    val origin = requestedCenter ?: grid.centerCoordinates() ?: nodes.centerCoordinatesOrNull() ?: return null
    val localNodes = nodes.map { node -> WeatherParticleVectorFieldOrigin.toLocal(origin, node.lat, node.lon) }
    val minX = localNodes.minOfOrNull { it.xKm } ?: return null
    val maxX = localNodes.maxOfOrNull { it.xKm } ?: return null
    val minY = localNodes.minOfOrNull { it.yKm } ?: return null
    val maxY = localNodes.maxOfOrNull { it.yKm } ?: return null
    val vectors = nodes.indices.map { nodeIndex ->
        val sample = windSampleAt(timeIndex, nodeIndex)
        sample?.let { meteorologicalWindToComponents(it.windSpeedKmh, it.windDirectionDegrees) }
            ?: WeatherWindVectorComponents(Double.NaN, Double.NaN, Double.NaN)
    }
    return WeatherParticleVectorField(
        originLat = origin.lat,
        originLon = origin.lon,
        rows = grid.rows,
        cols = grid.cols,
        minXKm = minX,
        maxXKm = maxX,
        minYKm = minY,
        maxYKm = maxY,
        vectors = vectors
    )
}

object WeatherParticleVectorFieldOrigin {
    fun toLocal(origin: WeatherMapCoordinates, lat: Double, lon: Double): WeatherLocalPoint {
        val latKm = (lat - origin.lat) * KmPerDegreeLatitude
        val lonKm = (lon - origin.lon) * KmPerDegreeLatitude * cos(Math.toRadians(origin.lat))
        return WeatherLocalPoint(lonKm, latKm)
    }
}

private fun WeatherMapGrid.centerCoordinates(): WeatherMapCoordinates? {
    val lat = centerLat ?: return null
    val lon = centerLon ?: return null
    return WeatherMapCoordinates(lat, lon)
}

private fun List<WeatherMapNode>.centerCoordinatesOrNull(): WeatherMapCoordinates? {
    if (isEmpty()) return null
    return WeatherMapCoordinates(
        lat = sumOf { it.lat } / size,
        lon = sumOf { it.lon } / size
    )
}

private fun bilinear(sw: Double, se: Double, nw: Double, ne: Double, x: Double, y: Double): Double {
    val south = sw + (se - sw) * x
    val north = nw + (ne - nw) * x
    return south + (north - south) * y
}

private const val KmPerDegreeLatitude = 111.32
