package it.droneskycheck.app.map

import it.droneskycheck.app.data.DscApiConfig

object MapLayerIds {
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
    const val KWOS_DATA_BASE_URL = DscApiConfig.KwosDataBaseUrl
    const val TRAFFIC_AWARENESS_SOURCE_ID = "traffic-awareness-source"
    const val TRAFFIC_AWARENESS_VECTOR_SOURCE_ID = "traffic-awareness-vector-source"
    const val TRAFFIC_AWARENESS_VECTOR_LAYER_ID = "traffic-awareness-vector-layer"
    const val TRAFFIC_AWARENESS_GLYPH_SOURCE_ID = "traffic-awareness-glyph-source"
    const val TRAFFIC_AWARENESS_GLYPH_HALO_LAYER_ID = "traffic-awareness-glyph-halo-layer"
    const val TRAFFIC_AWARENESS_GLYPH_LAYER_ID = "traffic-awareness-glyph-layer"
    const val TRAFFIC_AWARENESS_MARKER_LAYER_ID = "traffic-awareness-marker-layer"
    const val TRAFFIC_AWARENESS_SYMBOL_LAYER_ID = "traffic-awareness-symbol-layer"
    const val TRAFFIC_AWARENESS_ALTITUDE_LABEL_LAYER_ID = "traffic-awareness-altitude-label-layer"
    const val TRAFFIC_AWARENESS_ATTENTION_HALO_LAYER_ID = "traffic-awareness-attention-halo-layer"
    const val TRAFFIC_AWARENESS_RADIUS_SOURCE_ID = "traffic-awareness-radius-source"
    const val TRAFFIC_AWARENESS_RADIUS_FILL_LAYER_ID = "traffic-awareness-radius-fill-layer"
    const val TRAFFIC_AWARENESS_RADIUS_LINE_LAYER_ID = "traffic-awareness-radius-line-layer"

    val STATIC_LAYERS = listOf(
        DscMapLayer("p-parks-enr", "split/P_PARKS_ENR_561.geojson", DscLayerCategory.Parks, "Parks ENR 5.6.1", minZoom = 5.0f, zeroLimitOpacity = 0.05f),
        DscMapLayer("p-parks", "split/P_PARKS.geojson", DscLayerCategory.Parks, "Parks NFZ", minZoom = 5.0f, zeroLimitOpacity = 0.08f),
        DscMapLayer("atm09-parks", "split/ATM09_PARKS.geojson", DscLayerCategory.Parks, "ATM09 parks", minZoom = 6.0f, zeroLimitOpacity = 0.05f),
        DscMapLayer("atm09-ctr", "split/ATM09_CTR.geojson", DscLayerCategory.Ctr, "CTR", minZoom = 6.0f, lineWidth = 1.1f),
        DscMapLayer("other", "split/Other.geojson", DscLayerCategory.Atz, "ATZ", minZoom = 7.0f, lineWidth = 1.2f, featureTypeAliases = setOf("Other_ATZ")),
        DscMapLayer("atm09-other", "split/ATM09_OTHER.geojson", DscLayerCategory.Airports, "Airports", minZoom = 8.0f, lineWidth = 1.3f),
        DscMapLayer("atm09-aviosup-rebuilt", "split/ATM09_AVIOSUP_REBUILT.geojson", DscLayerCategory.Aviosuperfici, "Aviosuperfici rebuilt", minZoom = 9.0f, lineWidth = 1.4f),
        DscMapLayer("atm09-lif", "split/ATM09_LIF.geojson", DscLayerCategory.Lif, "LIF", minZoom = 7.0f),
        DscMapLayer("atm09-danger", "split/ATM09_DANGER.geojson", DscLayerCategory.Danger, "Danger areas", minZoom = 7.0f),
        DscMapLayer("atm09-restricted", "split/ATM09_RESTRICTED.geojson", DscLayerCategory.Restricted, "Restricted areas", minZoom = 7.0f),
        DscMapLayer("atm09-prison", "split/ATM09_PRISON.geojson", DscLayerCategory.Prohibited, "Prohibited areas", minZoom = 8.0f),
        DscMapLayer("p-security", "split/P_SECURITY.geojson", DscLayerCategory.Security, "UAS GZ security", minZoom = 8.0f),
        DscMapLayer("p-sup", "split/P_SUP.geojson", DscLayerCategory.Sup, "SUP AIP", minZoom = 8.0f),
        DscMapLayer("p-notam-dflight", "split/P_NOTAM_DFLIGHT.geojson", DscLayerCategory.Notam, "NOTAM D-Flight", minZoom = 7.0f, zeroLimitOpacity = 0.22f),
        DscMapLayer("p-notam-faa", "split/P_NOTAM_FAA.geojson", DscLayerCategory.Notam, "NOTAM FAA", minZoom = 7.0f, zeroLimitOpacity = 0.22f),
        DscMapLayer("p-notam", "split/P_NOTAM.geojson", DscLayerCategory.Notam, "NOTAM merged", minZoom = 7.0f, zeroLimitOpacity = 0.22f)
    )

    val DYNAMIC_ZONES_LAYERS = listOf(
        DscDynamicZonesLayer(
            key = "tactical-enr-5-2-2-6",
            zonesType = "TACTICAL",
            category = DscLayerCategory.Tactical,
            label = "Aree tattiche ENR 5.2.2.6",
            minZoom = 6.0f,
            lineWidth = 1.0f,
            featureTypeAliases = setOf("TACTICAL_ENR_5_2_2_6")
        ),
        DscDynamicZonesLayer(
            key = "corridor-enr-5-2-2-5",
            zonesType = "CORRIDOR",
            category = DscLayerCategory.Corridors,
            label = "Corridoi APR ENR 5.2.2.5",
            minZoom = 7.0f,
            lineWidth = 1.1f,
            featureTypeAliases = setOf("CORRIDOR_ENR_5_2_2_5")
        )
    )

    fun categoryForFeatureType(type: String?): DscLayerCategory? {
        if (type.isNullOrBlank()) return null
        return STATIC_LAYERS.firstOrNull { it.matchesFeatureType(type) }?.category
            ?: DYNAMIC_ZONES_LAYERS.firstOrNull { it.matchesFeatureType(type) }?.category
    }
}

enum class DscLayerCategory(
    val title: String,
    val subtitle: String,
    val webappLabel: String,
    val swatchHex: String
) {
    Notam(
        title = "NOTAM",
        subtitle = "Zone temporanee NFZ",
        webappLabel = "NOTAM (NFZ)",
        swatchHex = DscZoneMapColors.noFly0m.webHex
    ),
    Parks(
        title = "Parchi",
        subtitle = "NFZ, LIPROT e aree protette",
        webappLabel = "Parchi (NFZ + LIPROT)",
        swatchHex = DscZoneMapColors.noFly0m.webHex
    ),
    OtherNfz(
        title = "Altre NFZ",
        subtitle = "Zone vietate non classificate come parchi",
        webappLabel = "Altre NFZ",
        swatchHex = DscZoneMapColors.noFly0m.webHex
    ),
    Sup(
        title = "SUP AIP",
        subtitle = "Zone temporanee da supplemento",
        webappLabel = "SUP AIP (temporanee)",
        swatchHex = DscZoneMapColors.noFly0m.webHex
    ),
    Security(
        title = "UAS GZ SEC/PREF",
        subtitle = "Aree vietate per sicurezza",
        webappLabel = "UAS GZ SEC/PREF (aree vietate)",
        swatchHex = DscZoneMapColors.noFly0m.webHex
    ),
    Ctr(
        title = "CTR",
        subtitle = "Zone di controllo",
        webappLabel = "CTR",
        swatchHex = DscZoneMapColors.limited60m.webHex
    ),
    Atz(
        title = "ATZ",
        subtitle = "Aree aeroportuali a 60 m",
        webappLabel = "ATZ (60 m)",
        swatchHex = DscZoneMapColors.limited60m.webHex
    ),
    Airports(
        title = "Aeroporti",
        subtitle = "Aree ATM09 aeroportuali",
        webappLabel = "Aeroporti",
        swatchHex = DscZoneMapColors.limited25m.webHex
    ),
    Aviosuperfici(
        title = "Avio/Eli/Idro superfici",
        subtitle = "Aviosuperfici ed eli/idrosuperfici",
        webappLabel = "Avio/Eli/Idro superfici",
        swatchHex = DscZoneMapColors.limited25m.webHex
    ),
    Lif(
        title = "LIF",
        subtitle = "Poligoni e aree militari",
        webappLabel = "LIF (poligoni militari)",
        swatchHex = DscZoneMapColors.noFly0m.webHex
    ),
    Prohibited(
        title = "Vietate LI P",
        subtitle = "Aree proibite",
        webappLabel = "Vietate (LI P)",
        swatchHex = DscZoneMapColors.noFly0m.webHex
    ),
    Restricted(
        title = "Regolamentate LI R",
        subtitle = "Aree regolamentate",
        webappLabel = "Regolamentate (LI R)",
        swatchHex = DscZoneMapColors.limited45m.webHex
    ),
    Danger(
        title = "Pericolose LI D",
        subtitle = "Aree pericolose",
        webappLabel = "Pericolose (LI D)",
        swatchHex = DscZoneMapColors.limited45m.webHex
    ),
    Tactical(
        title = "Aree tattiche",
        subtitle = "ENR 5.2.2.6, 45 m nei giorni feriali",
        webappLabel = "Aree tattiche (ENR 5.2.2.6)",
        swatchHex = DscZoneMapColors.limited45m.webHex
    ),
    Corridors(
        title = "Corridoi APR",
        subtitle = "ENR 5.2.2.5",
        webappLabel = "Corridoi APR (ENR 5.2.2.5)",
        swatchHex = DscZoneMapColors.noFly0m.webHex
    );

    companion object {
        val defaultVisibility: Map<DscLayerCategory, Boolean> =
            entries.associateWith { true }
    }
}

data class DscDynamicZonesLayer(
    val key: String,
    val zonesType: String,
    val category: DscLayerCategory,
    val label: String,
    val minZoom: Float,
    val zeroLimitOpacity: Float = 0.19f,
    val lineWidth: Float = 0.9f,
    val featureTypeAliases: Set<String> = emptySet()
) {
    val sourceId: String = "dsc-$key-source"
    val fillLayerId: String = "dsc-$key-fill"
    val lineLayerId: String = "dsc-$key-line"

    fun matchesFeatureType(type: String): Boolean =
        type.equals(key, ignoreCase = true) ||
            type.equals(label, ignoreCase = true) ||
            type.equals(zonesType, ignoreCase = true) ||
            featureTypeAliases.any { it.equals(type, ignoreCase = true) }
}

data class DscMapLayer(
    val key: String,
    val relativePath: String,
    val category: DscLayerCategory,
    val label: String,
    val minZoom: Float,
    val zeroLimitOpacity: Float = 0.19f,
    val lineWidth: Float = 0.9f,
    val featureTypeAliases: Set<String> = emptySet()
) {
    val sourceId: String = "dsc-$key-source"
    val fillLayerId: String = "dsc-$key-fill"
    val zebraLayerId: String = "dsc-$key-zebra"
    val lineLayerId: String = "dsc-$key-line"
    val usesZebraPattern: Boolean = key.startsWith("p-notam")
    val url: String = "${MapLayerIds.KWOS_DATA_BASE_URL}/$relativePath"
    private val fileStem: String = relativePath.substringAfterLast('/').substringBeforeLast('.')

    fun matchesFeatureType(type: String): Boolean =
        type.equals(key, ignoreCase = true) ||
            type.equals(label, ignoreCase = true) ||
            type.equals(fileStem, ignoreCase = true) ||
            featureTypeAliases.any { it.equals(type, ignoreCase = true) }
}
