package it.droneskycheck.app.map

object MapLayerIds {
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
    const val KWOS_DATA_BASE_URL = "https://www.kwos.org/appoggio/droni/DroneSkyCheck"

    val STATIC_LAYERS = listOf(
        DscMapLayer("nfz-parks", "split/NFZ_PARKS.geojson", "NFZ parks", minZoom = 5.0f),
        DscMapLayer("p", "split/P.geojson", "Protected areas", minZoom = 5.0f),
        DscMapLayer("p-parks-enr", "split/P_PARKS_ENR_561.geojson", "Parks ENR 5.6.1", minZoom = 5.0f, zeroLimitOpacity = 0.05f),
        DscMapLayer("p-parks", "split/P_PARKS.geojson", "Parks NFZ", minZoom = 5.0f, zeroLimitOpacity = 0.08f),
        DscMapLayer("atm09-parks", "split/ATM09_PARKS.geojson", "ATM09 parks", minZoom = 6.0f, zeroLimitOpacity = 0.05f),
        DscMapLayer("atm09-ctr", "split/ATM09_CTR.geojson", "CTR", minZoom = 6.0f, lineWidth = 1.1f),
        DscMapLayer("other", "split/Other.geojson", "ATZ", minZoom = 7.0f, lineWidth = 1.2f),
        DscMapLayer("atm09-other", "split/ATM09_OTHER.geojson", "Airports", minZoom = 8.0f, lineWidth = 1.3f),
        DscMapLayer("atm09-aviosup", "split/ATM09_AVIOSUP.geojson", "Aviosuperfici", minZoom = 10.0f, lineWidth = 1.2f),
        DscMapLayer("atm09-aviosup-rebuilt", "split/ATM09_AVIOSUP_REBUILT.geojson", "Aviosuperfici rebuilt", minZoom = 10.0f, lineWidth = 1.4f),
        DscMapLayer("atm09-lif", "split/ATM09_LIF.geojson", "LIF", minZoom = 7.0f),
        DscMapLayer("atm09-danger", "split/ATM09_DANGER.geojson", "Danger areas", minZoom = 7.0f),
        DscMapLayer("atm09-restricted", "split/ATM09_RESTRICTED.geojson", "Restricted areas", minZoom = 7.0f),
        DscMapLayer("atm09-prison", "split/ATM09_PRISON.geojson", "Prohibited areas", minZoom = 8.0f),
        DscMapLayer("p-security", "split/P_SECURITY.geojson", "UAS GZ security", minZoom = 8.0f),
        DscMapLayer("p-sup", "split/P_SUP.geojson", "SUP AIP", minZoom = 8.0f),
        DscMapLayer("p-notam-dflight", "split/P_NOTAM_DFLIGHT.geojson", "NOTAM D-Flight", minZoom = 7.0f, zeroLimitOpacity = 0.22f),
        DscMapLayer("p-notam-faa", "split/P_NOTAM_FAA.geojson", "NOTAM FAA", minZoom = 7.0f, zeroLimitOpacity = 0.22f),
        DscMapLayer("p-notam", "split/P_NOTAM.geojson", "NOTAM merged", minZoom = 7.0f, zeroLimitOpacity = 0.22f)
    )
}

data class DscMapLayer(
    val key: String,
    val relativePath: String,
    val label: String,
    val minZoom: Float,
    val zeroLimitOpacity: Float = 0.19f,
    val lineWidth: Float = 0.9f
) {
    val sourceId: String = "dsc-$key-source"
    val fillLayerId: String = "dsc-$key-fill"
    val zebraLayerId: String = "dsc-$key-zebra"
    val lineLayerId: String = "dsc-$key-line"
    val usesZebraPattern: Boolean = key.startsWith("p-notam")
    val url: String = "${MapLayerIds.KWOS_DATA_BASE_URL}/$relativePath"
}
