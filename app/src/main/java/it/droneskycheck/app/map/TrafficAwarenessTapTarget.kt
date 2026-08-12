package it.droneskycheck.app.map

internal data class TrafficTapHitBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

internal object TrafficTapTargetStyle {
    const val HitSlopDp = 16.0f
}

internal fun trafficTapHitBoxForScreenPoint(centerX: Float, centerY: Float, density: Float): TrafficTapHitBox {
    val slopPx = trafficTapHitSlopPx(density)
    return TrafficTapHitBox(
        left = centerX - slopPx,
        top = centerY - slopPx,
        right = centerX + slopPx,
        bottom = centerY + slopPx
    )
}

internal fun trafficTapHitSlopPx(density: Float): Float =
    TrafficTapTargetStyle.HitSlopDp * density.coerceAtLeast(1.0f)
