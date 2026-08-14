package it.droneskycheck.app.ui.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class TextScaleTest {
    @Test
    fun switchOffUsesSystemFontScale() {
        assertEquals(1.0f, effectiveDscFontScale(systemFontScale = 1.0f, largeTextEnabled = false), 0.0f)
    }

    @Test
    fun switchOnAppliesMinimumScaleForSmallSystemText() {
        assertEquals(LargeTextMinScale, effectiveDscFontScale(systemFontScale = 1.0f, largeTextEnabled = true), 0.0f)
        assertEquals(LargeTextMinScale, effectiveDscFontScale(systemFontScale = 1.15f, largeTextEnabled = true), 0.0f)
    }

    @Test
    fun switchOnNeverReducesLargerSystemText() {
        assertEquals(1.30f, effectiveDscFontScale(systemFontScale = 1.30f, largeTextEnabled = true), 0.0f)
        assertEquals(1.50f, effectiveDscFontScale(systemFontScale = 1.50f, largeTextEnabled = true), 0.0f)
        assertEquals(2.00f, effectiveDscFontScale(systemFontScale = 2.00f, largeTextEnabled = true), 0.0f)
    }
}
