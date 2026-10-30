package app.lawnchair.aicleaner

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.provider.AlarmClock
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairLauncher
import app.lawnchair.animateToAllApps
import app.lawnchair.launcherNullable
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.qsb.LawnQsbLayout
import app.lawnchair.qsb.providers.Google
import app.lawnchair.qsb.providers.PixelSearch
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.Smartspace
import com.android.launcher3.R
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.views.OptionsPopupView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class AiCleanerHomeHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private var weatherJob: Job? = null

    private lateinit var clockSection: View
    private lateinit var weatherSection: View
    private lateinit var weatherIcon: ImageView
    private lateinit var temperatureText: TextView
    private lateinit var weatherConditionText: TextView
    private lateinit var searchCard: View
    private lateinit var googleLogo: ImageView
    private lateinit var micButton: ImageView
    private lateinit var lensButton: ImageView

    private val launcherOrNull: LawnchairLauncher?
        get() = context.launcherNullable

    override fun onFinishInflate() {
        super.onFinishInflate()
        clockSection = findViewById(R.id.ll_clock_section)
        weatherSection = findViewById(R.id.ll_weather_section)
        weatherIcon = findViewById(R.id.iv_weather_icon)
        temperatureText = findViewById(R.id.tv_temperature)
        weatherConditionText = findViewById(R.id.tv_weather_condition)
        searchCard = findViewById(R.id.ai_cleaner_search_card)
        googleLogo = findViewById(R.id.iv_google_logo)
        micButton = findViewById(R.id.iv_mic)
        lensButton = findViewById(R.id.iv_lens)

        AiCleanerWeatherRepository.ensureInitialized(context)
        bindWeather(AiCleanerWeatherRepository.weatherFlow.value)

        val longClickCustomize = OnLongClickListener {
            openCustomizePopup()
            true
        }
        clockSection.setOnLongClickListener(longClickCustomize)
        weatherSection.setOnLongClickListener(longClickCustomize)
        searchCard.setOnLongClickListener(longClickCustomize)

        clockSection.setOnClickListener { openClockApp(it) }
        weatherSection.setOnClickListener { openWeatherAndRefresh(it) }
        searchCard.setOnClickListener { openAllAppsSearch() }
        googleLogo.setOnClickListener { openProviderSearch() }

        updateSearchButtons()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateSearchButtons()
        val launcher = launcherOrNull ?: return
        weatherJob?.cancel()
        weatherJob = AiCleanerWeatherRepository.weatherFlow
            .onEach(::bindWeather)
            .launchIn(launcher.lifecycleScope)
        launcher.lifecycleScope.launch {
            AiCleanerWeatherRepository.refresh(context)
        }
    }

    override fun onDetachedFromWindow() {
        weatherJob?.cancel()
        weatherJob = null
        super.onDetachedFromWindow()
    }

    private fun bindWeather(data: AiCleanerWeatherData) {
        weatherIcon.setImageResource(data.iconRes)
        temperatureText.text = "${data.tempC}°C"
        weatherConditionText.text = data.condition
    }

    private fun updateSearchButtons() {
        val prefs2 = PreferenceManager2.getInstance(context)
        val providerPref = prefs2.hotseatQsbProvider.firstCached()
        val searchProvider = LawnQsbLayout.getSearchProvider(context, providerPref)
        val voiceIntent = LawnQsbLayout.getVoiceIntent(searchProvider, context)
        val supportsLens = searchProvider == Google || searchProvider == PixelSearch
        val lensIntent = if (supportsLens) LawnQsbLayout.getLensIntent(context) else null

        micButton.isVisible = voiceIntent != null
        micButton.setOnClickListener { view ->
            voiceIntent?.let { startIntentSafely(view, it) }
        }

        lensButton.isVisible = lensIntent != null
        lensButton.setOnClickListener { view ->
            lensIntent?.let { startIntentSafely(view, it) }
        }
    }

    private fun openClockApp(view: View) {
        val alarmIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (context.packageManager.resolveActivity(alarmIntent, 0) != null) {
            startIntentSafely(view, alarmIntent)
            return
        }
        val clockComponent = ComponentName.unflattenFromString(
            context.getString(R.string.clock_component_name),
        )
        if (clockComponent != null) {
            val fallback = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(clockComponent)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (context.packageManager.resolveActivity(fallback, 0) != null) {
                startIntentSafely(view, fallback)
            }
        }
    }

    private fun openWeatherAndRefresh(view: View) {
        launcherOrNull?.lifecycleScope?.launch {
            AiCleanerWeatherRepository.refresh(context, force = true)
        }
        val city = AiCleanerWeatherRepository.weatherFlow.value.city.ifBlank { "weather" }
        val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode("$city weather")}")
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startIntentSafely(view, intent)
    }

    private fun openAllAppsSearch() {
        val launcher = launcherOrNull ?: return
        launcher.lifecycleScope.launch {
            launcher.animateToAllApps()
            val searchUiManager = launcher.appsView.searchUiManager
            searchUiManager.setDirectFocus(true)
            searchUiManager.editText?.showKeyboard()
        }
    }

    private fun openProviderSearch() {
        val launcher = launcherOrNull ?: return
        val prefs2 = PreferenceManager2.getInstance(context)
        val searchProvider = LawnQsbLayout.getSearchProvider(
            context,
            prefs2.hotseatQsbProvider.firstCached(),
        )
        launcher.lifecycleScope.launch {
            searchProvider.launch(launcher)
        }
    }

    private fun openCustomizePopup() {
        val launcher = launcherOrNull ?: return
        val pos = Rect()
        launcher.dragLayer.getDescendantRectRelativeToSelf(this, pos)
        val option = OptionsPopupView.OptionItem(
            context,
            R.string.action_customize,
            R.drawable.ic_setting,
            StatsLogManager.LauncherEvent.IGNORE,
        ) {
            context.startActivity(PreferenceActivity.createIntent(context, Smartspace))
            true
        }
        OptionsPopupView.show<LawnchairLauncher>(launcher, RectF(pos), listOf(option), true)
    }

    private fun startIntentSafely(view: View, intent: Intent) {
        val launcher = launcherOrNull
        if (launcher != null) {
            launcher.startActivitySafely(view, intent, null)
        } else {
            runCatching { context.startActivity(intent) }
        }
    }
}
