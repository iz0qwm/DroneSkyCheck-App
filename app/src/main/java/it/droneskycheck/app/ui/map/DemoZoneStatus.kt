package it.droneskycheck.app.ui.map

enum class DemoZoneStatus(val label: String) {
    NoFly("NON CONSENTITO"),
    Limited("LIMITATO"),
    Open("APERTO")
}

fun demoStatusForLowerLimit(lowerLimit: Int): DemoZoneStatus = when {
    lowerLimit <= 0 -> DemoZoneStatus.NoFly
    lowerLimit in 1..119 -> DemoZoneStatus.Limited
    else -> DemoZoneStatus.Open
}
