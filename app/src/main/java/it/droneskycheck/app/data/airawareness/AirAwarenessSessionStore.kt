package it.droneskycheck.app.data.airawareness

import android.content.Context
import org.json.JSONObject

interface AirAwarenessSessionStore {
    fun load(): AirAwarenessSession?
    fun loadPendingActivation(): AirAwarenessActivationRequest?
    fun save(session: AirAwarenessSession)
    fun savePendingActivation(request: AirAwarenessActivationRequest)
    fun clearPendingActivation()
    fun clear()
}

class SharedPreferencesAirAwarenessSessionStore(context: Context) : AirAwarenessSessionStore {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    override fun load(): AirAwarenessSession? =
        preferences.getString(SessionKey, null)?.let { raw ->
            runCatching {
                val json = JSONObject(raw)
                AirAwarenessSession(
                    doa = publishedDoaFromStoredJson(json.getJSONObject("doa")),
                    closePending = json.optBoolean("closePending", false),
                    locationStartedByMode = json.optBoolean("locationStartedByMode", true),
                    trafficStartedByMode = json.optBoolean("trafficStartedByMode", true)
                )
            }.getOrNull()
        }

    override fun loadPendingActivation(): AirAwarenessActivationRequest? =
        preferences.getString(PendingActivationKey, null)?.let { raw ->
            runCatching {
                val json = JSONObject(raw)
                AirAwarenessActivationRequest(
                    operationId = json.getString("operationId"),
                    closeToken = json.getString("closeToken"),
                    lat = json.getDouble("lat"),
                    lon = json.getDouble("lon")
                )
            }.getOrNull()
        }

    override fun save(session: AirAwarenessSession) {
        preferences.edit().putString(
            SessionKey,
            JSONObject()
                .put("doa", session.doa.toJson())
                .put("closePending", session.closePending)
                .put("locationStartedByMode", session.locationStartedByMode)
                .put("trafficStartedByMode", session.trafficStartedByMode)
                .toString()
        ).apply()
    }

    override fun savePendingActivation(request: AirAwarenessActivationRequest) {
        preferences.edit().putString(
            PendingActivationKey,
            JSONObject()
                .put("operationId", request.operationId)
                .put("closeToken", request.closeToken)
                .put("lat", request.lat)
                .put("lon", request.lon)
                .toString()
        ).apply()
    }

    override fun clearPendingActivation() {
        preferences.edit().remove(PendingActivationKey).apply()
    }

    override fun clear() {
        preferences.edit().remove(SessionKey).apply()
    }

    private companion object {
        const val PreferencesName = "air_awareness"
        const val SessionKey = "session"
        const val PendingActivationKey = "pending_activation"
    }
}

class InMemoryAirAwarenessSessionStore(
    private var session: AirAwarenessSession? = null,
    private var pendingActivation: AirAwarenessActivationRequest? = null
) : AirAwarenessSessionStore {
    override fun load(): AirAwarenessSession? = session
    override fun loadPendingActivation(): AirAwarenessActivationRequest? = pendingActivation
    override fun save(session: AirAwarenessSession) {
        this.session = session
    }
    override fun savePendingActivation(request: AirAwarenessActivationRequest) {
        pendingActivation = request
    }
    override fun clearPendingActivation() {
        pendingActivation = null
    }
    override fun clear() {
        session = null
    }
}
