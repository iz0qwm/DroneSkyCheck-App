package it.droneskycheck.app.data.airawareness

import java.time.Instant
import org.json.JSONObject

data class PublishedDoa(
    val id: String,
    val closeToken: String,
    val lat: Double,
    val lon: Double,
    val radiusMeters: Double,
    val startTimeMillis: Long,
    val endTimeMillis: Long
)

data class AirAwarenessSession(
    val doa: PublishedDoa,
    val closePending: Boolean = false,
    val locationStartedByMode: Boolean = true,
    val trafficStartedByMode: Boolean = true
)

data class AirAwarenessActivationRequest(
    val operationId: String,
    val closeToken: String,
    val lat: Double,
    val lon: Double
)

enum class AirAwarenessPhase {
    IDLE,
    CONFIRMING,
    REQUESTING_LOCATION,
    PUBLISHING_DOA,
    ACTIVE,
    STOPPING,
    CLOSE_PENDING,
    FAILED
}

data class AirAwarenessState(
    val phase: AirAwarenessPhase = AirAwarenessPhase.IDLE,
    val session: AirAwarenessSession? = null,
    val statusSheetVisible: Boolean = false,
    val error: String? = null
) {
    val active: Boolean
        get() = phase == AirAwarenessPhase.ACTIVE

    val activationInProgress: Boolean
        get() = phase == AirAwarenessPhase.REQUESTING_LOCATION || phase == AirAwarenessPhase.PUBLISHING_DOA
}

fun parsePublishedDoa(json: JSONObject, fallbackCloseToken: String): PublishedDoa {
    val id = json.requiredString("id")
    val lat = json.requiredFiniteDouble("lat")
    val lon = json.requiredFiniteDouble("lon")
    val radius = json.requiredFiniteDouble("radiusM")
    val startTime = json.requiredInstantMillis("startTime")
    val endTime = json.requiredInstantMillis("endTime")
    require(lat in -90.0..90.0 && lon in -180.0..180.0)
    require(radius > 0.0 && endTime > startTime)
    return PublishedDoa(
        id = id,
        closeToken = json.optString("closeToken").trim().ifBlank { fallbackCloseToken },
        lat = lat,
        lon = lon,
        radiusMeters = radius,
        startTimeMillis = startTime,
        endTimeMillis = endTime
    )
}

fun PublishedDoa.toJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("closeToken", closeToken)
        .put("lat", lat)
        .put("lon", lon)
        .put("radiusM", radiusMeters)
        .put("startTimeMillis", startTimeMillis)
        .put("endTimeMillis", endTimeMillis)

fun publishedDoaFromStoredJson(json: JSONObject): PublishedDoa =
    PublishedDoa(
        id = json.requiredString("id"),
        closeToken = json.requiredString("closeToken"),
        lat = json.requiredFiniteDouble("lat"),
        lon = json.requiredFiniteDouble("lon"),
        radiusMeters = json.requiredFiniteDouble("radiusM"),
        startTimeMillis = json.getLong("startTimeMillis"),
        endTimeMillis = json.getLong("endTimeMillis")
    )

private fun JSONObject.requiredString(name: String): String =
    getString(name).trim().also { require(it.isNotBlank()) }

private fun JSONObject.requiredFiniteDouble(name: String): Double =
    getDouble(name).also { require(it.isFinite()) }

private fun JSONObject.requiredInstantMillis(name: String): Long =
    Instant.parse(requiredString(name)).toEpochMilli()
