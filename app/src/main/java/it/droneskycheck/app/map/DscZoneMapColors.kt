package it.droneskycheck.app.map

import org.maplibre.android.style.expressions.Expression

object DscZoneMapColors {
    val noFly0m = Rgba(255.0f, 42.0f, 42.0f, "#ff2a2a")
    val limited25m = Rgba(255.0f, 152.0f, 0.0f, "#ff9800")
    val limited45m = Rgba(255.0f, 235.0f, 59.0f, "#ffeb3b")
    val limited60m = Rgba(41.0f, 182.0f, 246.0f, "#29b6f6")
    val fallback = Rgba(158.0f, 158.0f, 158.0f, "#9e9e9e")

    fun fillExpression(): Expression =
        limitColorExpression(
            noFly0m,
            limited25m,
            limited45m,
            limited60m,
            fallback
        )

    fun lineExpression(): Expression =
        limitColorExpression(
            noFly0m.darken(),
            limited25m.darken(),
            limited45m.darken(),
            limited60m.darken(),
            fallback.darken()
        )

    fun fillOpacityExpression(layer: DscMapLayer): Expression =
        fillOpacityExpression(layer.zeroLimitOpacity)

    fun fillOpacityExpression(layer: DscDynamicZonesLayer): Expression =
        fillOpacityExpression(layer.zeroLimitOpacity)

    private fun fillOpacityExpression(zeroLimitOpacity: Float): Expression =
        Expression.match(
            lowerLimitTextExpression(),
            Expression.literal("0"),
            Expression.literal(zeroLimitOpacity),
            Expression.literal("25"),
            Expression.literal(0.19f),
            Expression.literal("45"),
            Expression.literal(0.19f),
            Expression.literal("60"),
            Expression.literal(0.18f),
            Expression.literal("120"),
            Expression.literal(0.0f),
            Expression.literal(0.19f)
        )

    fun lineOpacityExpression(): Expression =
        Expression.match(
            lowerLimitTextExpression(),
            Expression.literal("120"),
            Expression.literal(0.0f),
            Expression.literal(0.92f)
        )

    private fun limitColorExpression(
        noFly: Rgba,
        limit25: Rgba,
        limit45: Rgba,
        limit60: Rgba,
        fallback: Rgba
    ): Expression =
        Expression.match(
            lowerLimitTextExpression(),
            Expression.literal("0"),
            noFly.toExpression(),
            Expression.literal("25"),
            limit25.toExpression(),
            Expression.literal("45"),
            limit45.toExpression(),
            Expression.literal("60"),
            limit60.toExpression(),
            Expression.literal("120"),
            fallback.toExpression(),
            fallback.toExpression()
        )

    private fun lowerLimitTextExpression(): Expression =
        Expression.toString(
            Expression.coalesce(
                Expression.get("lowerLimit"),
                Expression.get("lowerlimit"),
                Expression.get("lowerLimitAGL"),
                Expression.get("maxHeight"),
                Expression.get("maxAltitude"),
                Expression.get("altitudeAGL"),
                Expression.literal(120)
            )
        )
}

data class Rgba(
    val red: Float,
    val green: Float,
    val blue: Float,
    val webHex: String
) {
    fun toExpression(): Expression =
        Expression.rgba(red, green, blue, 1.0f)

    fun darken(): Rgba =
        copy(
            red = red * 0.72f,
            green = green * 0.72f,
            blue = blue * 0.72f
        )
}
