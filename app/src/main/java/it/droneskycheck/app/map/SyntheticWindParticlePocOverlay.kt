package it.droneskycheck.app.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import it.droneskycheck.app.data.DscLogger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

class SyntheticWindParticlePocOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.rgb(84, 205, 238)
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val profile = SyntheticWindParticlePocProfile.selected()
    private val field = SyntheticWindVectorField()
    private val sample = SyntheticWindSample()
    private val projector = SyntheticWindScreenProjector()
    private val particles = Array(profile.particleCount) {
        SyntheticWindParticle(profile.trailPointCount)
    }
    private val random = Random.Default
    private var map: MapLibreMap? = null
    private var pocEnabled = false
    private var cameraMoving = false
    private var running = false
    private var lastFrameAt = 0L
    private var lastStatsAt = 0L
    private var framesInWindow = 0
    private var totalFrameMillis = 0L
    private var fieldCenterLat: Double? = null
    private var fieldCenterLon: Double? = null

    init {
        setWillNotDraw(false)
        setBackgroundColor(Color.TRANSPARENT)
        isEnabled = true
        isClickable = false
        isFocusable = false
        visibility = GONE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setPocEnabled(enabled: Boolean) {
        val targetVisibility = if (enabled) VISIBLE else GONE
        if (visibility != targetVisibility) visibility = targetVisibility
        if (pocEnabled == enabled) return
        pocEnabled = enabled
        if (enabled) {
            ensureFieldAroundAnchor(forceReset = true)
            resetParticles()
            lastFrameAt = 0L
            maybeStart()
        } else {
            stop()
        }
        invalidate()
    }

    fun attachToMap(map: MapLibreMap) {
        this.map = map
        ensureFieldAroundAnchor(forceReset = false)
        resetParticles()
        maybeStart()
    }

    fun detachFromMap() {
        stop()
        map = null
    }

    fun setCameraMoving(moving: Boolean) {
        if (cameraMoving == moving) return
        cameraMoving = moving
        if (moving) {
            pauseForCamera()
        } else {
            ensureFieldAroundAnchor(forceReset = false)
            lastFrameAt = 0L
            maybeStart()
        }
    }

    fun setFieldCenter(lat: Double?, lon: Double?) {
        val changed = when {
            lat == null || lon == null -> fieldCenterLat != null || fieldCenterLon != null
            fieldCenterLat == null || fieldCenterLon == null -> true
            else -> {
                val latDistanceKm = kotlin.math.abs(lat - fieldCenterLat!!) * KmPerDegreeLatitude
                val lonDistanceKm = kotlin.math.abs(lon - fieldCenterLon!!) * KmPerDegreeLatitude *
                    cos(Math.toRadians(lat)).coerceAtLeast(0.1)
                latDistanceKm > CenterChangeThresholdKm || lonDistanceKm > CenterChangeThresholdKm
            }
        }
        fieldCenterLat = lat
        fieldCenterLon = lon
        if (changed && pocEnabled) {
            ensureFieldAroundAnchor(forceReset = true)
            resetParticles()
            lastFrameAt = 0L
            invalidate()
        }
    }

    fun stop() {
        if (running || pocEnabled) {
            DscLogger.debug(PocLogTag, "state=STOPPED")
        }
        running = false
        removeCallbacks(animationTick)
        resetStats()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        maybeStart()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === this) {
            if (visibility == VISIBLE) maybeStart() else stop()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!pocEnabled || cameraMoving) return
        val map = map ?: return
        if (map.cameraPosition.tilt > MaxPocPitchDegrees) return
        if (!field.isInitialized) {
            ensureFieldAroundAnchor(forceReset = true)
            resetParticles()
        }
        if (!projector.update(map, field)) return

        val frameStart = SystemClock.uptimeMillis()
        val deltaSeconds = if (lastFrameAt == 0L) {
            1.0 / profile.targetFps
        } else {
            ((frameStart - lastFrameAt).coerceIn(1L, MaxDeltaMillis) / 1000.0)
        }
        lastFrameAt = frameStart

        updateParticles(deltaSeconds)
        drawParticles(canvas)

        val frameMillis = SystemClock.uptimeMillis() - frameStart
        collectStats(frameStart, frameMillis)
    }

    private fun updateParticles(deltaSeconds: Double) {
        particles.forEach { particle ->
            if (particle.ageSeconds >= particle.lifeSeconds) {
                particle.respawn(field, random)
                return@forEach
            }
            if (!field.sample(particle.xKm, particle.yKm, sample)) {
                particle.respawn(field, random)
                return@forEach
            }

            val vectorMagnitude = hypot(sample.u, sample.v).coerceAtLeast(0.001)
            val visualKmPerSecond = visualSpeedKmPerSecond(vectorMagnitude)
            particle.xKm += (sample.u / vectorMagnitude) * visualKmPerSecond * deltaSeconds
            particle.yKm += (sample.v / vectorMagnitude) * visualKmPerSecond * deltaSeconds
            particle.ageSeconds += deltaSeconds

            if (!field.contains(particle.xKm, particle.yKm)) {
                particle.respawn(field, random)
            } else {
                particle.addTrailPoint(particle.xKm, particle.yKm)
                particle.lastSpeedKmh = vectorMagnitude
            }
        }
    }

    private fun drawParticles(canvas: Canvas) {
        particles.forEach { particle ->
            val lifeAlpha = particle.lifeAlpha()
            if (lifeAlpha <= MinVisibleAlpha || particle.trailCount < 2) return@forEach

            val segmentDenominator = (particle.trailCount - 1).coerceAtLeast(1).toFloat()
            var offset = 1
            while (offset < particle.trailCount) {
                val previousIndex = particle.trailIndexFromOldest(offset - 1)
                val currentIndex = particle.trailIndexFromOldest(offset)
                val previousX = projector.screenX(particle.trailX[previousIndex], particle.trailY[previousIndex])
                val previousY = projector.screenY(particle.trailX[previousIndex], particle.trailY[previousIndex])
                val currentX = projector.screenX(particle.trailX[currentIndex], particle.trailY[currentIndex])
                val currentY = projector.screenY(particle.trailX[currentIndex], particle.trailY[currentIndex])
                if (previousX.isFinite() && previousY.isFinite() && currentX.isFinite() && currentY.isFinite()) {
                    val trailProgress = offset / segmentDenominator
                    val trailAlpha = smoothStep(trailProgress.toDouble()).coerceIn(0.0, 1.0)
                    val alpha = (trailAlpha * lifeAlpha).toFloat()
                    haloPaint.alpha = (profile.haloAlpha * alpha).toInt().coerceIn(0, 255)
                    haloPaint.strokeWidth = dp(profile.haloStrokeMinDp + profile.haloStrokeRangeDp * trailProgress)
                    canvas.drawLine(previousX, previousY, currentX, currentY, haloPaint)
                    corePaint.alpha = (profile.coreAlpha * alpha).toInt().coerceIn(0, 255)
                    corePaint.strokeWidth = dp(profile.coreStrokeMinDp + profile.coreStrokeRangeDp * trailProgress)
                    canvas.drawLine(previousX, previousY, currentX, currentY, corePaint)
                }
                offset += 1
            }

            val headIndex = particle.trailHead
            headPaint.alpha = (profile.headAlpha * lifeAlpha).toInt().coerceIn(0, 255)
            canvas.drawCircle(
                projector.screenX(particle.trailX[headIndex], particle.trailY[headIndex]),
                projector.screenY(particle.trailX[headIndex], particle.trailY[headIndex]),
                dp(profile.headRadiusDp),
                headPaint
            )
        }
    }

    private fun maybeStart() {
        if (
            running ||
            !pocEnabled ||
            map == null ||
            !isAttachedToWindow ||
            visibility != VISIBLE ||
            cameraMoving
        ) {
            return
        }
        running = true
        DscLogger.debug(
            PocLogTag,
            "state=RUNNING particles=${profile.particleCount} fpsTarget=${profile.targetFps} " +
                "trail=${profile.trailPointCount} centerLat=${"%.5f".format(field.originLat)} " +
                "centerLon=${"%.5f".format(field.originLon)}"
        )
        postInvalidateOnAnimation()
        postDelayed(animationTick, profile.frameDelayMillis)
    }

    private fun pauseForCamera() {
        if (running) {
            DscLogger.debug(PocLogTag, "state=PAUSED_GESTURE")
        }
        running = false
        removeCallbacks(animationTick)
        lastFrameAt = 0L
        invalidate()
    }

    private fun ensureFieldAroundAnchor(forceReset: Boolean) {
        val mapCenter = map?.cameraPosition?.target
        val centerLat = fieldCenterLat ?: mapCenter?.latitude ?: return
        val centerLon = fieldCenterLon ?: mapCenter?.longitude ?: return
        if (!forceReset && field.isInitialized && field.containsLatLon(centerLat, centerLon, FieldResetMarginKm)) return
        field.reset(centerLat, centerLon, profile.fieldWidthKm, profile.fieldHeightKm)
        resetParticles()
    }

    private fun resetParticles() {
        if (!field.isInitialized) return
        particles.forEach { it.respawn(field, random) }
        resetStats()
    }

    private fun collectStats(now: Long, frameMillis: Long) {
        framesInWindow += 1
        totalFrameMillis += frameMillis
        if (lastStatsAt == 0L) {
            lastStatsAt = now
            return
        }
        val elapsed = now - lastStatsAt
        if (elapsed < StatsIntervalMillis) return
        val fps = framesInWindow * 1000.0 / elapsed
        val avgFrameMillis = totalFrameMillis.toDouble() / framesInWindow.coerceAtLeast(1)
        DscLogger.debug(
            PocLogTag,
            "state=RUNNING fps=${"%.1f".format(fps)} avgFrameMs=${"%.1f".format(avgFrameMillis)} " +
                "particles=${profile.particleCount} trail=${profile.trailPointCount} " +
                "centerLat=${"%.5f".format(field.originLat)} centerLon=${"%.5f".format(field.originLon)}"
        )
        resetStats(now)
    }

    private fun resetStats(now: Long = 0L) {
        lastStatsAt = now
        framesInWindow = 0
        totalFrameMillis = 0L
    }

    private fun visualSpeedKmPerSecond(speedKmh: Double): Double {
        val normalized = sqrt((speedKmh / profile.speedReferenceKmh).coerceIn(0.0, 1.0))
        return profile.minVisualKmPerSecond +
            (profile.maxVisualKmPerSecond - profile.minVisualKmPerSecond) * normalized
    }

    private val animationTick = object : Runnable {
        override fun run() {
            if (!running) return
            postInvalidateOnAnimation()
            postDelayed(this, profile.frameDelayMillis)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        private const val PocLogTag = "DSC_WIND_POC"
        private const val MaxPocPitchDegrees = 35.0
        private const val MaxDeltaMillis = 96L
        private const val StatsIntervalMillis = 1_500L
        private const val MinVisibleAlpha = 0.01
        private const val FieldResetMarginKm = 8.0
        private const val CenterChangeThresholdKm = 0.25
        private const val KmPerDegreeLatitude = 111.32
    }
}

private data class SyntheticWindParticlePocProfile(
    val particleCount: Int,
    val targetFps: Int,
    val trailPointCount: Int,
    val fieldWidthKm: Double,
    val fieldHeightKm: Double,
    val minVisualKmPerSecond: Double,
    val maxVisualKmPerSecond: Double,
    val speedReferenceKmh: Double,
    val coreAlpha: Int,
    val haloAlpha: Int,
    val headAlpha: Int,
    val coreStrokeMinDp: Float,
    val coreStrokeRangeDp: Float,
    val haloStrokeMinDp: Float,
    val haloStrokeRangeDp: Float,
    val headRadiusDp: Float
) {
    val frameDelayMillis: Long = 1000L / targetFps

    companion object {
        fun selected(): SyntheticWindParticlePocProfile =
            if (SyntheticWindParticlePocUseLongTrail) LongTrail else Medium

        private const val SyntheticWindParticlePocUseLongTrail = false

        val Medium = SyntheticWindParticlePocProfile(
            particleCount = 180,
            targetFps = 25,
            trailPointCount = 9,
            fieldWidthKm = 42.0,
            fieldHeightKm = 32.0,
            minVisualKmPerSecond = 0.85,
            maxVisualKmPerSecond = 2.45,
            speedReferenceKmh = 55.0,
            coreAlpha = 218,
            haloAlpha = 58,
            headAlpha = 228,
            coreStrokeMinDp = 0.75f,
            coreStrokeRangeDp = 1.05f,
            haloStrokeMinDp = 2.2f,
            haloStrokeRangeDp = 1.35f,
            headRadiusDp = 1.6f
        )

        val LongTrail = SyntheticWindParticlePocProfile(
            particleCount = 176,
            targetFps = 25,
            trailPointCount = 13,
            fieldWidthKm = 42.0,
            fieldHeightKm = 32.0,
            minVisualKmPerSecond = 0.85,
            maxVisualKmPerSecond = 2.45,
            speedReferenceKmh = 55.0,
            coreAlpha = 218,
            haloAlpha = 58,
            headAlpha = 228,
            coreStrokeMinDp = 0.75f,
            coreStrokeRangeDp = 1.05f,
            haloStrokeMinDp = 2.2f,
            haloStrokeRangeDp = 1.35f,
            headRadiusDp = 1.6f
        )
    }
}

private class SyntheticWindParticle(trailCapacity: Int) {
    val trailX = DoubleArray(trailCapacity)
    val trailY = DoubleArray(trailCapacity)
    var xKm = 0.0
    var yKm = 0.0
    var ageSeconds = 0.0
    var lifeSeconds = 1.0
    var lastSpeedKmh = 0.0
    var trailHead = 0
    var trailCount = 0

    fun respawn(field: SyntheticWindVectorField, random: Random) {
        xKm = random.nextDouble(field.minXKm, field.maxXKm)
        yKm = random.nextDouble(field.minYKm, field.maxYKm)
        ageSeconds = 0.0
        lifeSeconds = random.nextDouble(MinLifeSeconds, MaxLifeSeconds)
        lastSpeedKmh = 0.0
        trailHead = 0
        trailCount = 1
        var index = 0
        while (index < trailX.size) {
            trailX[index] = xKm
            trailY[index] = yKm
            index += 1
        }
    }

    fun addTrailPoint(x: Double, y: Double) {
        trailHead = (trailHead + 1) % trailX.size
        trailX[trailHead] = x
        trailY[trailHead] = y
        if (trailCount < trailX.size) trailCount += 1
    }

    fun trailIndexFromOldest(offset: Int): Int {
        val oldest = (trailHead - trailCount + 1 + trailX.size) % trailX.size
        return (oldest + offset) % trailX.size
    }

    fun lifeAlpha(): Double {
        val progress = (ageSeconds / lifeSeconds).coerceIn(0.0, 1.0)
        val fadeIn = smoothStep((progress / FadeInLifeFraction).coerceIn(0.0, 1.0))
        val fadeOutProgress = ((progress - FadeOutStartFraction) / (1.0 - FadeOutStartFraction))
            .coerceIn(0.0, 1.0)
        val fadeOut = 1.0 - smoothStep(fadeOutProgress)
        return fadeIn * fadeOut
    }

    companion object {
        private const val MinLifeSeconds = 5.8
        private const val MaxLifeSeconds = 9.5
        private const val FadeInLifeFraction = 0.10
        private const val FadeOutStartFraction = 0.75
    }
}

private class SyntheticWindVectorField {
    var originLat = 0.0
        private set
    var originLon = 0.0
        private set
    var minXKm = 0.0
        private set
    var maxXKm = 0.0
        private set
    var minYKm = 0.0
        private set
    var maxYKm = 0.0
        private set
    var isInitialized = false
        private set

    fun reset(centerLat: Double, centerLon: Double, widthKm: Double, heightKm: Double) {
        originLat = centerLat
        originLon = centerLon
        minXKm = -widthKm / 2.0
        maxXKm = widthKm / 2.0
        minYKm = -heightKm / 2.0
        maxYKm = heightKm / 2.0
        isInitialized = true
    }

    fun contains(xKm: Double, yKm: Double): Boolean =
        xKm in minXKm..maxXKm && yKm in minYKm..maxYKm

    fun containsLatLon(lat: Double, lon: Double, marginKm: Double): Boolean {
        val xKm = (lon - originLon) * kmPerDegreeLon()
        val yKm = (lat - originLat) * KmPerDegreeLatitude
        return xKm in (minXKm + marginKm)..(maxXKm - marginKm) &&
            yKm in (minYKm + marginKm)..(maxYKm - marginKm)
    }

    fun sample(xKm: Double, yKm: Double, out: SyntheticWindSample): Boolean {
        if (!contains(xKm, yKm)) return false
        val width = maxXKm - minXKm
        val height = maxYKm - minYKm
        val xNorm = ((xKm - minXKm) / width).coerceIn(0.0, 1.0)
        val yNorm = ((yKm - minYKm) / height).coerceIn(0.0, 1.0)
        val wave = sin((xNorm * 2.2 * PI) + (yNorm * 1.1 * PI))
        val curveCenterX = minXKm + width * 0.58
        val curveCenterY = minYKm + height * 0.46
        val dx = (xKm - curveCenterX) / (width * 0.24)
        val dy = (yKm - curveCenterY) / (height * 0.28)
        val curve = exp(-(dx * dx + dy * dy))
        val baseSpeed = 24.0 + 13.0 * xNorm
        out.u = baseSpeed + 7.0 * curve - 3.0 * dy * curve
        out.v = 7.5 * wave + 18.0 * curve - 4.5 * (yNorm - 0.5)
        return true
    }

    fun latForY(yKm: Double): Double = originLat + yKm / KmPerDegreeLatitude

    fun lonForX(xKm: Double): Double = originLon + xKm / kmPerDegreeLon()

    private fun kmPerDegreeLon(): Double =
        KmPerDegreeLatitude * cos(Math.toRadians(originLat)).coerceAtLeast(0.1)

    companion object {
        private const val KmPerDegreeLatitude = 111.32
    }
}

private class SyntheticWindSample {
    var u = 0.0
    var v = 0.0
}

private class SyntheticWindScreenProjector {
    private var originX = 0f
    private var originY = 0f
    private var xAxisX = 0f
    private var xAxisY = 0f
    private var yAxisX = 0f
    private var yAxisY = 0f

    fun update(map: MapLibreMap, field: SyntheticWindVectorField): Boolean {
        val projection = map.projection
        val origin = projection.toScreenLocation(LatLng(field.originLat, field.originLon))
        val east = projection.toScreenLocation(LatLng(field.originLat, field.lonForX(1.0)))
        val north = projection.toScreenLocation(LatLng(field.latForY(1.0), field.originLon))
        if (
            !origin.x.isFinite() ||
            !origin.y.isFinite() ||
            !east.x.isFinite() ||
            !east.y.isFinite() ||
            !north.x.isFinite() ||
            !north.y.isFinite()
        ) {
            return false
        }
        originX = origin.x
        originY = origin.y
        xAxisX = east.x - origin.x
        xAxisY = east.y - origin.y
        yAxisX = north.x - origin.x
        yAxisY = north.y - origin.y
        return true
    }

    fun screenX(xKm: Double, yKm: Double): Float =
        originX + (xAxisX * xKm).toFloat() + (yAxisX * yKm).toFloat()

    fun screenY(xKm: Double, yKm: Double): Float =
        originY + (xAxisY * xKm).toFloat() + (yAxisY * yKm).toFloat()
}

private fun smoothStep(value: Double): Double {
    val t = value.coerceIn(0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)
}
