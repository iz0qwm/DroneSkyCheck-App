package it.droneskycheck.app.data.ai

import android.content.Context
import java.util.UUID

interface DscAiInstallationIdProvider {
    fun getOrCreateInstallationId(): String
}

class DscAiInstallationIdRepository(
    context: Context
) : DscAiInstallationIdProvider {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    override fun getOrCreateInstallationId(): String {
        val existing = preferences.getString(KeyInstallationId, null)
            ?.trim()
            ?.takeIf(::isUuidV4)
        if (existing != null) return existing

        val generated = UUID.randomUUID().toString()
        preferences.edit()
            .putString(KeyInstallationId, generated)
            .apply()
        return generated
    }

    private companion object {
        const val PreferencesName = "dsc_ai_installation"
        const val KeyInstallationId = "installation_id"
    }
}

private fun isUuidV4(value: String): Boolean =
    runCatching { UUID.fromString(value).version() == 4 }.getOrDefault(false)
