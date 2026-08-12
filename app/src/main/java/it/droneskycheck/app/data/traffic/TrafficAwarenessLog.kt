package it.droneskycheck.app.data.traffic

import java.util.Locale

const val TrafficAwarenessLogTag = "DSC_TRAFFIC"

fun Double.coarseTraffic(decimals: Int = 2): String =
    String.format(Locale.US, "%.${decimals}f", this)
