package it.droneskycheck.app.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import it.droneskycheck.app.data.weatherMap.WeatherLocalPoint
import it.droneskycheck.app.data.weatherMap.WeatherParticleEngine
import it.droneskycheck.app.data.weatherMap.WeatherParticleFrame
import it.droneskycheck.app.data.weatherMap.WeatherParticleVectorField
import it.droneskycheck.app.data.weatherMap.windSpeedColorHex
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

class WeatherWindParticleOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(1.35f)
        alpha = 150
    }
    private val engine = WeatherParticleEngine()
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
        nextField?.let(engine::reset)
        lastFrameAt = 0L
        if (nextField == null) stop() else maybeStart()
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

        val now = android.os.SystemClock.uptimeMillis()
        val deltaSeconds = if (lastFrameAt == 0L) {
            1.0 / 30.0
        } else {
            ((now - lastFrameAt).coerceIn(1L, 80L) / 1000.0)
        }
        lastFrameAt = now
        engine.step(field, deltaSeconds).forEach { frame -> drawParticle(canvas, map, field, frame) }
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
        if (running || field == null || map == null || !isAttachedToWindow) return
        running = true
        postInvalidateOnAnimation()
        postDelayed(animationTick, WeatherParticleEngine.TargetFrameMillis)
    }

    private fun drawParticle(
        canvas: Canvas,
        map: MapLibreMap,
        field: WeatherParticleVectorField,
        frame: WeatherParticleFrame
    ) {
        val start = field.toLatLon(WeatherLocalPoint(frame.previousXKm, frame.previousYKm))
        val end = field.toLatLon(WeatherLocalPoint(frame.xKm, frame.yKm))
        val startScreen = map.projection.toScreenLocation(LatLng(start.lat, start.lon))
        val endScreen = map.projection.toScreenLocation(LatLng(end.lat, end.lon))
        if (!startScreen.x.isFinite() || !startScreen.y.isFinite() || !endScreen.x.isFinite() || !endScreen.y.isFinite()) {
            return
        }
        val dx = endScreen.x - startScreen.x
        val dy = endScreen.y - startScreen.y
        val movementPx = kotlin.math.hypot(dx.toDouble(), dy.toDouble())
        if (movementPx < MinDirectionalMovementPx || !movementPx.isFinite()) {
            return
        }
        val trailLengthPx = visualTrailLengthPx(frame.speedKmh)
        val directionX = (dx / movementPx).toFloat()
        val directionY = (dy / movementPx).toFloat()
        val tailX = endScreen.x - directionX * trailLengthPx
        val tailY = endScreen.y - directionY * trailLengthPx

        paint.color = Color.WHITE
        paint.alpha = HaloAlpha
        paint.strokeWidth = dp(HaloStrokeDp)
        canvas.drawLine(tailX, tailY, endScreen.x, endScreen.y, paint)
        paint.color = Color.parseColor(windSpeedColorHex(frame.speedKmh))
        paint.alpha = CoreAlpha
        paint.strokeWidth = dp(CoreStrokeDp)
        canvas.drawLine(tailX, tailY, endScreen.x, endScreen.y, paint)
    }

    private val animationTick = object : Runnable {
        override fun run() {
            if (!running) return
            postInvalidateOnAnimation()
            postDelayed(this, WeatherParticleEngine.TargetFrameMillis)
        }
    }

    private fun visualTrailLengthPx(speedKmh: Double): Float {
        val normalizedSpeed = ((speedKmh - TrailMinSpeedKmh) / (TrailMaxSpeedKmh - TrailMinSpeedKmh))
            .coerceIn(0.0, 1.0)
        return dp(MinTrailLengthDp + ((MaxTrailLengthDp - MinTrailLengthDp) * normalizedSpeed).toFloat())
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density

    companion object {
        private const val MaxParticlePitchDegrees = 35.0
        private const val MinDirectionalMovementPx = 0.001
        private const val MinTrailLengthDp = 5.5f
        private const val MaxTrailLengthDp = 14.0f
        private const val TrailMinSpeedKmh = 4.0
        private const val TrailMaxSpeedKmh = 45.0
        private const val HaloStrokeDp = 3.2f
        private const val CoreStrokeDp = 1.75f
        private const val HaloAlpha = 68
        private const val CoreAlpha = 216
    }
}
