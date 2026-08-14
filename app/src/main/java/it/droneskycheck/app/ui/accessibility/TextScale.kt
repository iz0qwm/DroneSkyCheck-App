package it.droneskycheck.app.ui.accessibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    val effectiveFontScale = effectiveDscFontScale(
        systemFontScale = systemDensity.fontScale,
        largeTextEnabled = largeTextEnabled
    )

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = systemDensity.density,
            fontScale = effectiveFontScale
        ),
        content = content
    )
}
