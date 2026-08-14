package it.droneskycheck.app.ui.accessibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

const val LargeTextMinScale = 1.25f

fun effectiveDscFontScale(
    systemFontScale: Float,
    largeTextEnabled: Boolean,
    largeTextMinScale: Float = LargeTextMinScale
): Float =
    if (largeTextEnabled) {
        maxOf(systemFontScale, largeTextMinScale)
    } else {
        systemFontScale
    }

@Composable
fun DroneSkyCheckTextScaleProvider(
    largeTextEnabled: Boolean,
    content: @Composable () -> Unit
) {
    val systemDensity = LocalDensity.current
    val baseTypography = MaterialTheme.typography
    val effectiveFontScale = effectiveDscFontScale(
        systemFontScale = systemDensity.fontScale,
        largeTextEnabled = largeTextEnabled
    )
    val typography = if (largeTextEnabled) {
        baseTypography.withLargeTextFloors()
    } else {
        baseTypography
    }

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = systemDensity.density,
            fontScale = effectiveFontScale
        )
    ) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            typography = typography,
            shapes = MaterialTheme.shapes,
            content = content
        )
    }
}

private fun Typography.withLargeTextFloors(): Typography =
    copy(
        bodySmall = bodyMedium,
        labelMedium = bodyMedium,
        labelSmall = labelMedium
    )
