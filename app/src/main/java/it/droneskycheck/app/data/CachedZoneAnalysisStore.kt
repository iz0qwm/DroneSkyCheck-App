package it.droneskycheck.app.data

import android.content.Context
import java.util.Locale
import org.json.JSONObject

const val CachedZoneAnalysisCoordinateDecimals = 5
const val CachedZoneAnalysisMaxRecords = 200

data class CachedZoneAnalysis(
    val id: String,
    val lat: Double,
    val lon: Double,
    val analyzedAtUtc: Long,
    val responseJson: String,
    val zoneIds: List<String> = emptyList(),
    val notamCodes: List<String> = emptyList()
)

interface CachedZoneAnalysisStore {
    suspend fun get(lat: Double, lon: Double): CachedZoneAnalysis?
    suspend fun upsert(lat: Double, lon: Double, analyzedAtUtc: Long, responseJson: String, response: ZoneCheckV3Response)
}

class RoomCachedZoneAnalysisStore(
    context: Context,
    private val maxRecords: Int = CachedZoneAnalysisMaxRecords
) : CachedZoneAnalysisStore {
    private val dao = LocalPilotDatabase.getInstance(context).localPilotDao()

    override suspend fun get(lat: Double, lon: Double): CachedZoneAnalysis? {
        val normalizedLat = normalizeCachedZoneCoordinate(lat)
        val normalizedLon = normalizeCachedZoneCoordinate(lon)
        return dao.getCachedZoneAnalysis(normalizedLat, normalizedLon)?.toCachedZoneAnalysis()
    }

    override suspend fun upsert(
        lat: Double,
        lon: Double,
        analyzedAtUtc: Long,
        responseJson: String,
        response: ZoneCheckV3Response
    ) {
        val normalizedLat = normalizeCachedZoneCoordinate(lat)
        val normalizedLon = normalizeCachedZoneCoordinate(lon)
        dao.upsertCachedZoneAnalysis(
            CachedZoneAnalysisEntity(
                id = cachedZoneAnalysisId(normalizedLat, normalizedLon),
                lat = lat,
                lon = lon,
                normalizedLat = normalizedLat,
                normalizedLon = normalizedLon,
                analyzedAtUtc = analyzedAtUtc,
                responseJson = responseJson,
                zoneIds = response.zoneIdsJson(),
                notamCodes = response.notamCodesJson()
            )
        )
        dao.trimCachedZoneAnalyses(maxRecords)
    }
}

fun normalizeCachedZoneCoordinate(value: Double): String =
    String.format(Locale.US, "%.${CachedZoneAnalysisCoordinateDecimals}f", value)

fun cachedZoneAnalysisId(normalizedLat: String, normalizedLon: String): String =
    "$normalizedLat,$normalizedLon"

private fun CachedZoneAnalysisEntity.toCachedZoneAnalysis(): CachedZoneAnalysis =
    CachedZoneAnalysis(
        id = id,
        lat = lat,
        lon = lon,
        analyzedAtUtc = analyzedAtUtc,
        responseJson = responseJson,
        zoneIds = zoneIds.toJsonStringList(),
        notamCodes = notamCodes.toJsonStringList()
    )

private fun ZoneCheckV3Response.zoneIdsJson(): String =
    zones.mapNotNull { zone ->
        zone.id ?: zone.code ?: zone.name
    }.toJsonArrayString()

private fun ZoneCheckV3Response.notamCodesJson(): String =
    zones.flatMap { zone -> zone.notams.mapNotNull { it.code } }
        .distinct()
        .toJsonArrayString()

private fun List<String>.toJsonArrayString(): String =
    org.json.JSONArray(this).toString()

private fun String.toJsonStringList(): List<String> =
    runCatching {
        val array = org.json.JSONArray(this)
        (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList())

internal fun parseCachedZoneAnalysisResponse(
    cache: CachedZoneAnalysis,
    reason: ZoneCheckOfflineFallbackReason
): ZoneCheckV3Response =
    parseZoneCheckV3Response(JSONObject(cache.responseJson)).copy(
        offlineCache = ZoneCheckOfflineCacheInfo(
            analyzedAtUtcMillis = cache.analyzedAtUtc,
            reason = reason
        )
    )
