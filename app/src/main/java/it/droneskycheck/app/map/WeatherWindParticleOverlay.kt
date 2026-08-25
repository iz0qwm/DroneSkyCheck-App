package it.droneskycheck.app.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import it.droneskycheck.app.data.weatherMap.WeatherLocalPoint
import it.droneskycheck.app.data.weatherMap.WeatherParticleEngine
import it.droneskycheck.app.data.weatherMap.WeatherParticleVectorField
import kotlin.random.Random
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

class WeatherWindParticleOverlay @JvmOverloads constructor(
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
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val profile = RealWindParticleProfile.Approved
    private val particles = Array(profile.particleCount) {
        RealWindTrailParticle(profile.trailPointCount)
    }
    private val sample = WeatherParticleSampleHolder()
    private val projector = RealWindScreenProjector()
    private val random = Random.Default
    private var map: MapLibreMap? = null
    private var field: WeatherParticleVectorField? = null
    private var cameraMoving = false
    private var running = false
    private var lastFrameAt = 0L

    init {
        setWillNotDraw(false)
        setBackgroundColor(Color.TRANSPARENT)
        isEnabled = true
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun attachToMap(map: MapLibreMap) {
        this.map = map
        maybeStart()
    }

    fun detachFromMap() {
        stop()
        map = null
    }

    fun setCameraMoving(moving: Boolean) {
        if (cameraMoving == moving) return
        cameraMoving = moving
        if (!moving) {
            lastFrameAt = 0L
            maybeStart()
        } else {
            invalidate()
        }
    }

    fun setVectorField(nextField: WeatherParticleVectorField?) {
        if (field === nextField) return
        field = nextField
        if (nextField == null) {
            stop()
        } else {
            resetParticles(nextField)
            lastFrameAt = 0L
            maybeStart()
        }
        invalidate()
    }

    fun stop() {
        running = false
        removeCallbacks(animationTick)
        invalidate()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val map = map ?: return
        val field = field ?: return
        if (cameraMoving) return
        if (map.cameraPosition.tilt > MaxParticlePitchDegrees) return
        if (!projector.update(map, field)) return

        val now = SystemClock.uptimeMillis()
        val deltaSeconds = if (lastFrameAt == 0L) {
            1.0 / profile.targetFps
        } else {
            ((now - lastFrameAt).coerceIn(1L, MaxDeltaMillis) / 1000.0)
        }
        lastFrameAt = now
        updateParticles(field, deltaSeconds)
        drawParticles(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        maybeStart()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun maybeStart() {
        if (running || field == null || map == null || !isAttachedToWindow || cameraMoving) return
        running = true
        postInvalidateOnAnimation()
        postDelayed(animationTick, profile.frameDelayMillis)
    }

    private fun updateParticles(field: WeatherParticleVectorField, deltaSeconds: Double) {
        particles.forEach { particle ->
            if (particle.ageSeconds >= particle.lifeSeconds) {
                particle.respawn(field, random)
                return@forEach
            }
            val weatherSample = field.sample(WeatherLocalPoint(particle.xKm, particle.yKm))
            if (weatherSample == null) {
                particle.respawn(field, random)
                return@forEach
            }

            sample.u = weatherSample.u
            sample.v = weatherSample.v
            sample.speedKmh = weatherSample.speedKmh
            val visualKm = deltaSeconds * WeatherParticleEngine.ParticleSpeedScale
            particle.xKm += sample.u * visualKm
            particle.yKm += sample.v * visualKm
            particle.ageSeconds += deltaSeconds

            if (!field.contains(WeatherLocalPoint(particle.xKm, particle.yKm))) {
                particle.respawn(field, random)
            } else {
                particle.addTrailPoint(particle.xKm, particle.yKm, sample.speedKmh)
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
                    corePaint.color = windSpeedColorInt(particle.trailSpeedKmh[currentIndex])
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

    private fun resetParticles(field: WeatherParticleVectorField) {
        particles.forEach { it.respawn(field, random) }
    }

    private val animationTick = object : Runnable {
        override fun run() {
            if (!running) return
            postInvalidateOnAnimation()
            postDelayed(this, profile.frameDelayMillis)
        }
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density

    companion object {
        private const val MaxParticlePitchDegrees = 35.0
        private const val MaxDeltaMillis = 96L
        private const val MinVisibleAlpha = 0.01
    }
}

private data class RealWindParticleProfile(
    val particleCount: Int,
    val targetFps: Int,
    val trailPointCount: Int,
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
        val Approved = RealWindParticleProfile(
            particleCount = 180,
            targetFps = 25,
            trailPointCount = 9,
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

private class RealWindTrailParticle(trailCapacity: Int) {
    val trailX = DoubleArray(trailCapacity)
    val trailY = DoubleArray(trailCapacity)
    val trailSpeedKmh = DoubleArray(trailCapacity)
    var xKm = 0.0
    var yKm = 0.0
    var ageSeconds = 0.0
    var lifeSeconds = 1.0
    var trailHead = 0
    var trailCount = 0

    fun respawn(field: WeatherParticleVectorField, random: Random) {
        xKm = random.nextDouble(field.minXKm, field.maxXKm)
        yKm = random.nextDouble(field.minYKm, field.maxYKm)
        ageSeconds = 0.0
        lifeSeconds = random.nextDouble(MinLifeSeconds, MaxLifeSeconds)
        trailHead = 0
        trailCount = 1
        var index = 0
        while (index < trailX.size) {
            trailX[index] = xKm
            trailY[index] = yKm
            trailSpeedKmh[index] = 0.0
            index += 1
        }
    }

    fun addTrailPoint(x: Double, y: Double, speedKmh: Double) {
        trailHead = (trailHead + 1) % trailX.size
        trailX[trailHead] = x
        trailY[trailHead] = y
        trailSpeedKmh[trailHead] = speedKmh
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

private class WeatherParticleSampleHolder {
    var u = 0.0
    var v = 0.0
    var speedKmh = 0.0
}

private class RealWindScreenProjector {
    private var originX = 0f
    private var originY = 0f
    private var xAxisX = 0f
    private var xAxisY = 0f
    private var yAxisX = 0f
    private var yAxisY = 0f

    fun update(map: MapLibreMap, field: WeatherParticleVectorField): Boolean {
        val origin = field.toLatLon(WeatherLocalPoint(0.0, 0.0))
        val east = field.toLatLon(WeatherLocalPoint(1.0, 0.0))
        val north = field.toLatLon(WeatherLocalPoint(0.0, 1.0))
        val projection = map.projection
        val originScreen = projection.toScreenLocation(LatLng(origin.lat, origin.lon))
        val eastScreen = projection.toScreenLocation(LatLng(east.lat, east.lon))
        val northScreen = projection.toScreenLocation(LatLng(north.lat, north.lon))
        if (
            !originScreen.x.isFinite() ||
            !originScreen.y.isFinite() ||
            !eastScreen.x.isFinite() ||
            !eastScreen.y.isFinite() ||
            !northScreen.x.isFinite() ||
            !northScreen.y.isFinite()
        ) {
            return false
        }
        originX = originScreen.x
        originY = originScreen.y
        xAxisX = eastScreen.x - originScreen.x
        xAxisY = eastScreen.y - originScreen.y
        yAxisX = northScreen.x - originScreen.x
        yAxisY = northScreen.y - originScreen.y
        return true
    }

    fun screenX(xKm: Double, yKm: Double): Float =
        originX + (xAxisX * xKm).toFloat() + (yAxisX * yKm).toFloat()

    fun screenY(xKm: Double, yKm: Double): Float =
        originY + (xAxisY * xKm).toFloat() + (yAxisY * yKm).toFloat()
}

private fun windSpeedColorInt(speedKmh: Double): Int =
    when {
        speedKmh < 10.0 -> Color.rgb(0x4F, 0xC3, 0xF7)
        speedKmh < 20.0 -> Color.rgb(0x66, 0xBB, 0x6A)
        speedKmh < 30.0 -> Color.rgb(0xFD, 0xD8, 0x35)
        speedKmh < 40.0 -> Color.rgb(0xFB, 0x8C, 0x00)
        else -> Color.rgb(0xE5, 0x39, 0x35)
    }

private fun smoothStep(value: Double): Double {
    val t = value.coerceIn(0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)
}
