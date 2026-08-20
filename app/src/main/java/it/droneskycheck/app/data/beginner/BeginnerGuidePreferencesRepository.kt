package it.droneskycheck.app.data.beginner

import android.content.Context

data class BeginnerGuideReadingState(
    val started: Boolean,
    val completed: Boolean,
    val lastPageIndex: Int,
    val autoStartupEnabled: Boolean,
    val zoomHintShown: Boolean,
    val localContentVersion: String?
)

interface BeginnerGuidePreferences {
    fun getReadingState(): BeginnerGuideReadingState
    fun setStarted(started: Boolean)
    fun setCompleted(completed: Boolean)
    fun setLastPageIndex(index: Int)
    fun setAutoStartupEnabled(enabled: Boolean)
    fun setZoomHintShown(shown: Boolean)
    fun setLocalContentVersion(contentVersion: String)
}

class BeginnerGuidePreferencesRepository(
    context: Context
) : BeginnerGuidePreferences {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    override fun getReadingState(): BeginnerGuideReadingState =
        BeginnerGuideReadingState(
            started = preferences.getBoolean(KeyStarted, false),
            completed = preferences.getBoolean(KeyCompleted, false),
            lastPageIndex = preferences.getInt(KeyLastPageIndex, -1),
            autoStartupEnabled = preferences.getBoolean(KeyAutoStartupEnabled, true),
            zoomHintShown = preferences.getBoolean(KeyZoomHintShown, false),
            localContentVersion = preferences.getString(KeyLocalContentVersion, null)
        )

    override fun setStarted(started: Boolean) {
        preferences.edit().putBoolean(KeyStarted, started).apply()
    }

    override fun setCompleted(completed: Boolean) {
        preferences.edit().putBoolean(KeyCompleted, completed).apply()
    }

    override fun setLastPageIndex(index: Int) {
        preferences.edit().putInt(KeyLastPageIndex, index.coerceAtLeast(-1)).apply()
    }

    override fun setAutoStartupEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KeyAutoStartupEnabled, enabled).apply()
    }

    override fun setZoomHintShown(shown: Boolean) {
        preferences.edit().putBoolean(KeyZoomHintShown, shown).apply()
    }

    override fun setLocalContentVersion(contentVersion: String) {
        preferences.edit().putString(KeyLocalContentVersion, contentVersion).apply()
    }

    private companion object {
        const val PreferencesName = "dsc_beginner_guide_preferences"
        const val KeyStarted = "started"
        const val KeyCompleted = "completed"
        const val KeyLastPageIndex = "last_page_index"
        const val KeyAutoStartupEnabled = "auto_startup_enabled"
        const val KeyZoomHintShown = "zoom_hint_shown"
        const val KeyLocalContentVersion = "local_content_version"
    }
}

class InMemoryBeginnerGuidePreferences(
    initialState: BeginnerGuideReadingState = BeginnerGuideReadingState(
        started = false,
        completed = false,
        lastPageIndex = -1,
        autoStartupEnabled = true,
        zoomHintShown = false,
        localContentVersion = null
    )
) : BeginnerGuidePreferences {
    private var state = initialState

    override fun getReadingState(): BeginnerGuideReadingState = state

    override fun setStarted(started: Boolean) {
        state = state.copy(started = started)
    }

    override fun setCompleted(completed: Boolean) {
        state = state.copy(completed = completed)
    }

    override fun setLastPageIndex(index: Int) {
        state = state.copy(lastPageIndex = index.coerceAtLeast(-1))
    }

    override fun setAutoStartupEnabled(enabled: Boolean) {
        state = state.copy(autoStartupEnabled = enabled)
    }

    override fun setZoomHintShown(shown: Boolean) {
        state = state.copy(zoomHintShown = shown)
    }

    override fun setLocalContentVersion(contentVersion: String) {
        state = state.copy(localContentVersion = contentVersion)
    }
}
