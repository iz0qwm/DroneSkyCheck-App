package it.droneskycheck.app.data.news

import android.content.Context

interface NewsPreferences {
    fun getLastSeenNewsId(): Long
    fun setLastSeenNewsId(id: Long)
}

class NewsPreferencesRepository(context: Context) : NewsPreferences {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    override fun getLastSeenNewsId(): Long = preferences.getLong(KeyLastSeenNewsId, NoNewsSeen)

    override fun setLastSeenNewsId(id: Long) {
        if (id <= getLastSeenNewsId()) return
        preferences.edit().putLong(KeyLastSeenNewsId, id).apply()
    }

    private companion object {
        const val PreferencesName = "dsc_news_preferences"
        const val KeyLastSeenNewsId = "lastSeenNewsId"
        const val NoNewsSeen = -1L
    }
}
