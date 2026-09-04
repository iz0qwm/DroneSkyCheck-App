package it.droneskycheck.app.data.insights

import android.content.Context
import it.droneskycheck.app.data.DscApiConfig
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class InsightsTool(
    val wireValue: String,
    val entryClass: String
) {
    ZoneInformation("ZONE_CHECK", "MAP"),
    News("NEWS", "NEWS"),
    Weather("WEATHER", "MAP"),
    AirTraffic("AIR_TRAFFIC", "MAP"),
    AiAssistant("DSC_ASSISTANT", "ASSISTANT"),
    BeforeFlight("BEFORE_FLIGHT", "TOOLS_PANEL"),
    DroneWorld("DRONE_WORLD", "TOOLS_PANEL"),
    LegalInfo("LEGAL_INFO", "TOOLS_PANEL")
}

interface InsightsConsentPreferences {
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
}

class AndroidInsightsPreferences(context: Context) : InsightsConsentPreferences {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = preferences.getBoolean(EnabledKey, true)

    override fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(EnabledKey, enabled).apply()
    }

    private companion object {
        const val PreferencesName = "dsc_insights_preferences"
        const val EnabledKey = "product_analytics_enabled_v1"
    }
}

class InsightsClient(
    private val consent: InsightsConsentPreferences,
    private val endpointUrl: String = DscApiConfig.InsightsEventsUrl,
    private val clock: Clock = Clock.systemUTC(),
    private val timeoutMillis: Int = DefaultTimeoutMillis,
    private val sessionTokenFactory: () -> String = { randomIdentifier() },
    private val batchIdFactory: () -> String = { randomIdentifier() }
) {
    private val sendMutex = Mutex()
    private val consentResetGeneration = AtomicInteger(0)
    private var sessionToken = sessionTokenFactory()
    private var sessionStarted = false
    private var activeConsentGeneration = 0

    suspend fun trackToolOpened(tool: InsightsTool): Boolean = sendMutex.withLock {
        val currentGeneration = consentResetGeneration.get()
        if (currentGeneration != activeConsentGeneration) {
            sessionStarted = false
            sessionToken = sessionTokenFactory()
            activeConsentGeneration = currentGeneration
        }
        if (!consent.isEnabled()) return@withLock false

        val includesSessionStart = !sessionStarted
        val events = JSONArray()
        if (includesSessionStart) events.put(sessionStartedEvent())
        events.put(toolOpenedEvent(tool))

        val sent = post(
            JSONObject()
                .put("catalogVersion", CatalogVersion)
                .put("source", "ANDROID")
                .put("environmentHint", "PRODUCTION")
                .put("sessionToken", sessionToken)
                .put("batchId", batchIdFactory())
                .put("events", events)
                .toString()
        )
        if (sent && includesSessionStart) sessionStarted = true
        sent
    }

    fun onConsentChanged(enabled: Boolean) {
        consent.setEnabled(enabled)
        if (!enabled) consentResetGeneration.incrementAndGet()
    }

    private fun sessionStartedEvent(): JSONObject = event(
        name = "session_started",
        properties = JSONObject()
            .put("platform", "ANDROID")
            .put("sourceClass", "ANDROID")
            .put("landingClass", "MAP")
            .put("tierClass", "UNKNOWN")
            .put("deviceClass", "MOBILE")
    )

    private fun toolOpenedEvent(tool: InsightsTool): JSONObject = event(
        name = "tool_opened",
        properties = JSONObject()
            .put("toolClass", tool.wireValue)
            .put("platform", "ANDROID")
            .put("entryClass", tool.entryClass)
            .put("deviceClass", "MOBILE")
    )

    private fun event(name: String, properties: JSONObject): JSONObject = JSONObject()
        .put("schemaVersion", SchemaVersion)
        .put("eventName", name)
        .put("clientOccurredAt", clock.instant().toString())
        .put("properties", properties)

    private suspend fun post(body: String): Boolean = withContext(Dispatchers.IO) {
        val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            useCaches = false
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-DSC-Insights-Consent", "GRANTED")
        }
        val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause is CancellationException) connection.disconnect()
        }
        try {
            connection.outputStream.use { stream ->
                stream.write(body.toByteArray(Charsets.UTF_8))
            }
            currentCoroutineContext().ensureActive()
            connection.responseCode in 200..299
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        } finally {
            cancellationHandle.dispose()
            connection.disconnect()
        }
    }

    private companion object {
        const val CatalogVersion = 1
        const val SchemaVersion = 1
        const val DefaultTimeoutMillis = 5_000

        fun randomIdentifier(): String = UUID.randomUUID().toString().replace("-", "")
    }
}
