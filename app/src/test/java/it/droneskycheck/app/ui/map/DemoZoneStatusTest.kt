package it.droneskycheck.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class DemoZoneStatusTest {
    @Test
    fun lowerLimitZeroMapsToNoFly() {
        assertEquals(DemoZoneStatus.NoFly, demoStatusForLowerLimit(0))
    }

    @Test
    fun lowerLimitBetweenOneAnd119MapsToLimited() {
        assertEquals(DemoZoneStatus.Limited, demoStatusForLowerLimit(1))
        assertEquals(DemoZoneStatus.Limited, demoStatusForLowerLimit(60))
        assertEquals(DemoZoneStatus.Limited, demoStatusForLowerLimit(119))
    }

    @Test
    fun lowerLimit120OrMoreMapsToOpen() {
        assertEquals(DemoZoneStatus.Open, demoStatusForLowerLimit(120))
        assertEquals(DemoZoneStatus.Open, demoStatusForLowerLimit(150))
    }
}
