package app.lawnchair.search

import android.content.SearchRecentSuggestionsProvider
import com.android.launcher3.LauncherHost

class LawnchairRecentSuggestionProvider : SearchRecentSuggestionsProvider() {
    companion object {
        val AUTHORITY = LauncherHost.get() + ".search.LawnchairRecentSuggestionProvider"
        const val MODE = DATABASE_MODE_QUERIES
    }

    init {
        setupSuggestions(AUTHORITY, MODE)
    }
}
