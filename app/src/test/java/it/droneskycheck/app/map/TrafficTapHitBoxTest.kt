package it.droneskycheck.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficTapHitBoxTest {
    @Test
    fun trafficTapHitboxUsesTwentyEightDpPerSide() {
        val hitBox = trafficTapHitBoxForScreenPoint(centerX = 100.0f, centerY = 200.0f, density = 2.0f)

        assertEquals(44.0f, hitBox.left, 0.0f)
        assertEquals(156.0f, hitBox.right, 0.0f)
        assertEquals(112.0f, hitBox.right - hitBox.left, 0.0f)
        assertEquals(112.0f, hitBox.bottom - hitBox.top, 0.0f)
    }

    @Test
    fun trafficTapHitboxIsWiderThanSinglePoint() {
        val hitBox = trafficTapHitBoxForScreenPoint(centerX = 0.0f, centerY = 0.0f, density = 1.0f)

        assertTrue(hitBox.left < 0.0f)
        assertTrue(hitBox.right > 0.0f)
        assertTrue(hitBox.top < 0.0f)
        assertTrue(hitBox.bottom > 0.0f)
    }
}
