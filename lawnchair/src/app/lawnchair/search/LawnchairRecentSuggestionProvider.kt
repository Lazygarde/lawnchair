package app.lawnchair.search

import android.content.SearchRecentSuggestionsProvider
import com.android.launcher3.HostPackage

class LawnchairRecentSuggestionProvider : SearchRecentSuggestionsProvider() {
    companion object {
        val AUTHORITY = HostPackage.get() + ".search.LawnchairRecentSuggestionProvider"
        const val MODE = DATABASE_MODE_QUERIES
    }

    init {
        setupSuggestions(AUTHORITY, MODE)
    }
}
