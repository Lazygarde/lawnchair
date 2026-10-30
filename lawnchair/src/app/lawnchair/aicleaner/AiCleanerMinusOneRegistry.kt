package app.lawnchair.aicleaner

import android.content.Context

/**
 * Optional callback registry for host apps embedding Lawnchair with the built-in
 * AI Cleaner Minus-One Dashboard. Allows intercepting feature tile taps
 * (e.g. `"security_scan"`, `"trash_clean"`, `"activity_monitor"`, `"wifi_security"`,
 * `"storage_usage"`, `"network_speed"`) without replacing the entire overlay view.
 */
object AiCleanerMinusOneRegistry {
    fun interface FeatureClickListener {
        fun onFeatureClick(context: Context, featureRoute: String): Boolean
    }

    @Volatile
    @JvmStatic
    var featureClickListener: FeatureClickListener? = null
}
