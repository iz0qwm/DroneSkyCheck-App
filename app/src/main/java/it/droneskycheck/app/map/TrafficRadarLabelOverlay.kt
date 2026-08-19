package it.droneskycheck.app.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

class TrafficRadarLabelOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val textFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(16, 24, 32)
        style = Paint.Style.FILL
        textSize = sp(12.0f)
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
    }
    private val textStrokePaint = Paint(textFillPaint).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2.4f)
        strokeJoin = Paint.Join.ROUND
    }
    private val acceptedPlacements = mutableListOf<TrafficRadarLabelPlacement>()
    private val cameraMoveListener = MapLibreMap.OnCameraMoveListener { invalidate() }
    private val cameraIdleListener = MapLibreMap.OnCameraIdleListener { invalidate() }

    private var map: MapLibreMap? = null
    private var targets: List<TrafficRadarLabelTarget> = emptyList()
    private val offsetXPx = dp(16.0f)
    private val offsetYPx = dp(10.0f)
    private val viewportMarginPx = dp(48.0f)
    private val lineHeightPx = textFillPaint.fontSpacing
    private val collisionPaddingPx = dp(4.0f)

    init {
        setWillNotDraw(false)
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun attachToMap(map: MapLibreMap) {
        if (this.map === map) return
        detachFromMap()
        this.map = map
        map.addOnCameraMoveListener(cameraMoveListener)
        map.addOnCameraIdleListener(cameraIdleListener)
        invalidate()
    }

    fun detachFromMap() {
        map?.removeOnCameraMoveListener(cameraMoveListener)
        map?.removeOnCameraIdleListener(cameraIdleListener)
        map = null
    }

    fun setTargets(targets: List<TrafficRadarLabelTarget>) {
        this.targets = sortTrafficRadarLabelTargets(targets)
        invalidate()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val map = map ?: return
        if (targets.isEmpty()) return

        acceptedPlacements.clear()
        targets.forEach { target ->
            val screenPoint = map.projection.toScreenLocation(LatLng(target.latitude, target.longitude))
            if (!screenPoint.isNearViewport()) return@forEach

            val lines = target.displayLines
            val x = screenPoint.x + offsetXPx
            val firstBaseline = screenPoint.y - offsetYPx
            val bounds = labelBounds(lines, x, firstBaseline)
            val placement = TrafficRadarLabelPlacement(target.id, target.relevance, bounds)
            if (!shouldDrawTrafficRadarLabel(acceptedPlacements, placement)) return@forEach

            drawLabel(canvas, lines, x, firstBaseline)
            acceptedPlacements += placement
        }
    }

    private fun labelBounds(
        lines: List<String>,
        x: Float,
        firstBaseline: Float
    ): TrafficRadarLabelBounds {
        val maxWidth = lines.maxOfOrNull { textFillPaint.measureText(it) } ?: 0.0f
        val top = firstBaseline + textFillPaint.fontMetrics.ascent
        val bottom = firstBaseline + ((lines.size - 1).coerceAtLeast(0) * lineHeightPx) + textFillPaint.fontMetrics.descent
        return TrafficRadarLabelBounds(
            left = x - collisionPaddingPx,
            top = top - collisionPaddingPx,
            right = x + maxWidth + collisionPaddingPx,
            bottom = bottom + collisionPaddingPx
        )
    }

    private fun drawLabel(canvas: Canvas, lines: List<String>, x: Float, firstBaseline: Float) {
        lines.forEachIndexed { index, line ->
            val baseline = firstBaseline + index * lineHeightPx
            canvas.drawText(line, x, baseline, textStrokePaint)
            canvas.drawText(line, x, baseline, textFillPaint)
        }
    }

    private fun android.graphics.PointF.isNearViewport(): Boolean =
        x >= -viewportMarginPx &&
            x <= width + viewportMarginPx &&
            y >= -viewportMarginPx &&
            y <= height + viewportMarginPx

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
