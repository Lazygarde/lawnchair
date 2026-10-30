package app.lawnchair.aicleaner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairLauncher
import app.lawnchair.animateToAllApps
import app.lawnchair.overlay.InProcessOverlayManager
import app.lawnchair.overlay.LawnchairOverlayProvider
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.qsb.LawnQsbLayout
import app.lawnchair.qsb.providers.Google
import app.lawnchair.qsb.providers.PixelSearch
import com.android.launcher3.LauncherHost
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.model.data.AppInfo
import kotlinx.coroutines.launch

class AiCleanerMinusOneOverlayProvider : LawnchairOverlayProvider {

    private var attachedLauncher: LawnchairLauncher? = null
    private var openEpoch by mutableIntStateOf(0)

    override fun onAttachedToLauncher(activity: Activity) {
        attachedLauncher = activity as? LawnchairLauncher
        AiCleanerWeatherRepository.ensureInitialized(activity)
    }

    override fun onDetachedFromLauncher() {
        attachedLauncher = null
    }

    override fun onOverlayOpened() {
        openEpoch++
        val launcher = attachedLauncher ?: return
        launcher.lifecycleScope.launch {
            AiCleanerWeatherRepository.refresh(launcher)
        }
    }

    override fun createOverlayView(context: Context, container: ViewGroup): View {
        val launcher = (context as? LawnchairLauncher) ?: attachedLauncher
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                AiCleanerMinusOneScreen(
                    launcher = launcher,
                    openEpoch = openEpoch,
                )
            }
        }
    }
}

private val BgGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF5670D8),
        Color(0xFF7B9CE8),
        Color(0xFFA4C2F4),
        Color(0xFFC4DAFA),
    ),
)

private val TextPrimary = Color(0xFF1A1C1E)
private val TextSecondary = Color(0xFF74777F)
private val DividerGray = Color(0xFFF0F0F2)
private val CardShape = RoundedCornerShape(20.dp)

@Composable
private fun AiCleanerMinusOneScreen(
    launcher: LawnchairLauncher?,
    openEpoch: Int,
) {
    val context = LocalContext.current
    val weather by AiCleanerWeatherRepository.weatherFlow.collectAsState()

    LaunchedEffect(openEpoch) {
        AiCleanerWeatherRepository.refresh(context)
    }

    var appsRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(launcher) {
        val store = launcher?.appsView?.appsStore
        val listener = AllAppsStore.OnUpdateListener { appsRevision++ }
        store?.addUpdateListener(listener)
        onDispose {
            store?.removeUpdateListener(listener)
        }
    }

    val toolApps: List<AppInfo> = remember(launcher, appsRevision) {
        val apps = launcher?.appsView?.appsStore?.apps
        if (apps.isNullOrEmpty()) {
            emptyList()
        } else {
            val hostPkg = runCatching { LauncherHost.get() }.getOrNull()
            apps.filter { it.targetPackage != hostPkg }
                .take(8)
        }
    }

    val prefs2 = remember(context) { PreferenceManager2.getInstance(context) }
    val searchProvider = remember(context, openEpoch) {
        LawnQsbLayout.getSearchProvider(context, prefs2.hotseatQsbProvider.firstCached())
    }
    val voiceIntent = remember(context, searchProvider) {
        LawnQsbLayout.getVoiceIntent(searchProvider, context)
    }
    val lensIntent = remember(context, searchProvider) {
        if (searchProvider == Google || searchProvider == PixelSearch) {
            LawnQsbLayout.getLensIntent(context)
        } else {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgGradient),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            MinusOneSearchPill(
                hasMic = voiceIntent != null,
                hasLens = lensIntent != null,
                onSearchClick = {
                    if (launcher != null) {
                        (launcher.overlayManager as? InProcessOverlayManager)?.hideOverlay(180)
                        launcher.lifecycleScope.launch {
                            launcher.animateToAllApps()
                            val searchUi = launcher.appsView.searchUiManager
                            searchUi.setDirectFocus(true)
                            searchUi.editText?.showKeyboard()
                        }
                    }
                },
                onGoogleClick = {
                    if (launcher != null) {
                        (launcher.overlayManager as? InProcessOverlayManager)?.hideOverlay(180)
                        launcher.lifecycleScope.launch {
                            searchProvider.launch(launcher)
                        }
                    }
                },
                onMicClick = {
                    voiceIntent?.let { launchSafely(context, launcher, it) }
                },
                onLensClick = {
                    lensIntent?.let { launchSafely(context, launcher, it) }
                },
            )

            Spacer(modifier = Modifier.height(16.dp))

            WeatherDashboardCard(
                weather = weather,
                onClick = {
                    launcher?.lifecycleScope?.launch {
                        AiCleanerWeatherRepository.refresh(context, force = true)
                    }
                    val query = "${weather.city.ifBlank { "local" }} weather"
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    launchSafely(context, launcher, intent)
                },
            )

            if (toolApps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.ai_cleaner_section_tools),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.ai_cleaner_tools_more),
                        color = Color(0xE6FFFFFF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (launcher != null) {
                                    (launcher.overlayManager as? InProcessOverlayManager)?.hideOverlay(180)
                                    launcher.lifecycleScope.launch {
                                        launcher.animateToAllApps()
                                    }
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                ToolsAppsCard(
                    apps = toolApps,
                    onAppClick = { app ->
                        val intent = app.intent
                        if (intent != null) {
                            launchSafely(context, launcher, intent, app)
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScanActionCard(
                    modifier = Modifier.weight(1f),
                    ringColor = Color(0xFF2ECC71),
                    ringTrackColor = Color(0xFFE8F8F0),
                    buttonBrush = Brush.horizontalGradient(
                        listOf(Color(0xFF2ECC71), Color(0xFF27AE60)),
                    ),
                    icon = Icons.Rounded.VerifiedUser,
                    buttonLabel = stringResource(R.string.ai_cleaner_scan_button),
                    onClick = {
                        handleFeatureRoute(
                            context = context,
                            launcher = launcher,
                            route = "security_scan",
                            fallbackAction = Settings.ACTION_SECURITY_SETTINGS,
                        )
                    },
                )
                ScanActionCard(
                    modifier = Modifier.weight(1f),
                    ringColor = Color(0xFF2F80ED),
                    ringTrackColor = Color(0xFFEAF3FF),
                    buttonBrush = Brush.horizontalGradient(
                        listOf(Color(0xFF3B82F6), Color(0xFF2563EB)),
                    ),
                    icon = Icons.Rounded.CleaningServices,
                    buttonLabel = stringResource(R.string.ai_cleaner_scan_button),
                    onClick = {
                        handleFeatureRoute(
                            context = context,
                            launcher = launcher,
                            route = "trash_clean",
                            fallbackAction = Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FeatureGridTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.MonitorHeart,
                    iconBg = Color(0xFFFFEFEA),
                    iconTint = Color(0xFFFF6B4A),
                    title = stringResource(R.string.ai_cleaner_tool_activity_monitor_title),
                    subtitle = stringResource(R.string.ai_cleaner_tool_activity_monitor_desc),
                    onClick = {
                        handleFeatureRoute(
                            context = context,
                            launcher = launcher,
                            route = "activity_monitor",
                            fallbackAction = Settings.ACTION_APPLICATION_SETTINGS,
                        )
                    },
                )
                FeatureGridTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Security,
                    iconBg = Color(0xFFE8F5E9),
                    iconTint = Color(0xFF2E7D32),
                    title = stringResource(R.string.ai_cleaner_tool_wifi_security_title),
                    subtitle = stringResource(R.string.ai_cleaner_tool_wifi_security_desc),
                    onClick = {
                        handleFeatureRoute(
                            context = context,
                            launcher = launcher,
                            route = "wifi_security",
                            fallbackAction = Settings.ACTION_WIFI_SETTINGS,
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FeatureGridTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Storage,
                    iconBg = Color(0xFFE8F0FE),
                    iconTint = Color(0xFF1A73E8),
                    title = stringResource(R.string.ai_cleaner_tool_storage_usage_title),
                    subtitle = stringResource(R.string.ai_cleaner_tool_storage_usage_desc),
                    onClick = {
                        handleFeatureRoute(
                            context = context,
                            launcher = launcher,
                            route = "storage_usage",
                            fallbackAction = Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
                        )
                    },
                )
                FeatureGridTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Speed,
                    iconBg = Color(0xFFF3E8FF),
                    iconTint = Color(0xFF7C3AED),
                    title = stringResource(R.string.ai_cleaner_tool_network_speed_title),
                    subtitle = stringResource(R.string.ai_cleaner_tool_network_speed_desc),
                    onClick = {
                        handleFeatureRoute(
                            context = context,
                            launcher = launcher,
                            route = "network_speed",
                            fallbackAction = Settings.ACTION_WIRELESS_SETTINGS,
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.ai_cleaner_section_system_shortcuts),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )

            SystemShortcutsCard(
                onOpenSetting = { primary, fallback ->
                    openSystemSettingSafely(context, launcher, primary, fallback)
                },
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MinusOneSearchPill(
    hasMic: Boolean,
    hasLens: Boolean,
    onSearchClick: () -> Unit,
    onGoogleClick: () -> Unit,
    onMicClick: () -> Unit,
    onLensClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .shadow(3.dp, RoundedCornerShape(26.dp), clip = false)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White)
            .border(1.dp, Color(0x1A000000), RoundedCornerShape(26.dp))
            .clickable(onClick = onSearchClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_super_g_color),
            contentDescription = stringResource(R.string.label_search),
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onGoogleClick),
        )
        Text(
            text = stringResource(R.string.ai_cleaner_search_hint),
            color = Color(0xFF5F6368),
            fontSize = 15.sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        if (hasMic) {
            Icon(
                painter = painterResource(R.drawable.ic_mic_flat),
                contentDescription = stringResource(R.string.label_voice_search),
                tint = Color(0xFF5F6368),
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMicClick),
            )
        }
        if (hasLens) {
            Spacer(modifier = Modifier.width(14.dp))
            Icon(
                painter = painterResource(R.drawable.ic_lens),
                contentDescription = stringResource(R.string.label_lens),
                tint = Color(0xFF5F6368),
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onLensClick),
            )
        }
    }
}

@Composable
private fun WeatherDashboardCard(
    weather: AiCleanerWeatherData,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = weather.city,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${weather.tempC}°C",
                        color = TextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${weather.tempMin}°/${weather.tempMax}°C",
                        color = TextSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = weather.condition,
                        color = Color(0xFF1A73E8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Image(
                painter = painterResource(weather.iconRes),
                contentDescription = weather.condition,
                modifier = Modifier.size(40.dp),
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            thickness = 0.8.dp,
            color = DividerGray,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeatherMetricColumn(
                modifier = Modifier.weight(1f),
                label = "Wind",
                value = weather.windSpeed,
                badgeColor = Color(0xFFE8F0FE),
                dotColor = Color(0xFF1A73E8),
            )
            Box(
                modifier = Modifier
                    .width(0.8.dp)
                    .height(24.dp)
                    .background(DividerGray),
            )
            WeatherMetricColumn(
                modifier = Modifier.weight(1f),
                label = "Humidity",
                value = weather.humidity,
                badgeColor = Color(0xFFE1F5FE),
                dotColor = Color(0xFF0288D1),
            )
            Box(
                modifier = Modifier
                    .width(0.8.dp)
                    .height(24.dp)
                    .background(DividerGray),
            )
            WeatherMetricColumn(
                modifier = Modifier.weight(1f),
                label = "UV Index",
                value = weather.uvIndex,
                badgeColor = Color(0xFFFFF3E0),
                dotColor = Color(0xFFFF9800),
            )
        }
    }
}

@Composable
private fun WeatherMetricColumn(
    label: String,
    value: String,
    badgeColor: Color,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ToolsAppsCard(
    apps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        apps.chunked(4).forEach { rowApps ->
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 4) {
                    val app = rowApps.getOrNull(col)
                    if (app != null) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onAppClick(app) }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val iconBitmap = app.bitmap.icon
                            if (!app.bitmap.isNullOrLowRes && iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap.asImageBitmap(),
                                    contentDescription = app.title?.toString(),
                                    modifier = Modifier.size(44.dp),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF2F4F7)),
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = app.title?.toString().orEmpty(),
                                color = TextPrimary,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanActionCard(
    ringColor: Color,
    ringTrackColor: Color,
    buttonBrush: Brush,
    icon: ImageVector,
    buttonLabel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(86.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 7.dp.toPx()
                drawCircle(
                    color = ringTrackColor,
                    style = Stroke(width = stroke),
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 290f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ringColor,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(buttonBrush),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = buttonLabel,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun FeatureGridTile(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            color = TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class SystemShortcutRowItem(
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val titleRes: Int,
    val descRes: Int,
    val primaryAction: String,
    val fallbackAction: String? = null,
)

@Composable
private fun SystemShortcutsCard(
    onOpenSetting: (primary: String, fallback: String?) -> Unit,
) {
    val rows = remember {
        listOf(
            SystemShortcutRowItem(
                icon = Icons.Rounded.Wifi,
                iconBg = Color(0xFFE8F0FE),
                iconTint = Color(0xFF1A73E8),
                titleRes = R.string.ai_cleaner_shortcut_wifi_title,
                descRes = R.string.ai_cleaner_shortcut_wifi_desc,
                primaryAction = Settings.ACTION_WIFI_SETTINGS,
            ),
            SystemShortcutRowItem(
                icon = Icons.Rounded.CellTower,
                iconBg = Color(0xFFE8F5E9),
                iconTint = Color(0xFF2E7D32),
                titleRes = R.string.ai_cleaner_shortcut_cellular_title,
                descRes = R.string.ai_cleaner_shortcut_cellular_desc,
                primaryAction = Settings.ACTION_DATA_ROAMING_SETTINGS,
                fallbackAction = Settings.ACTION_WIRELESS_SETTINGS,
            ),
            SystemShortcutRowItem(
                icon = Icons.Rounded.BatteryChargingFull,
                iconBg = Color(0xFFE8F8F0),
                iconTint = Color(0xFF16A34A),
                titleRes = R.string.ai_cleaner_shortcut_battery_title,
                descRes = R.string.ai_cleaner_shortcut_battery_desc,
                primaryAction = Intent.ACTION_POWER_USAGE_SUMMARY,
                fallbackAction = Settings.ACTION_BATTERY_SAVER_SETTINGS,
            ),
            SystemShortcutRowItem(
                icon = Icons.Rounded.Schedule,
                iconBg = Color(0xFFFFF3E0),
                iconTint = Color(0xFFEA580C),
                titleRes = R.string.ai_cleaner_shortcut_datetime_title,
                descRes = R.string.ai_cleaner_shortcut_datetime_desc,
                primaryAction = Settings.ACTION_DATE_SETTINGS,
            ),
            SystemShortcutRowItem(
                icon = Icons.Rounded.Apps,
                iconBg = Color(0xFFF3E8FF),
                iconTint = Color(0xFF7C3AED),
                titleRes = R.string.ai_cleaner_shortcut_apps_title,
                descRes = R.string.ai_cleaner_shortcut_apps_desc,
                primaryAction = Settings.ACTION_APPLICATION_SETTINGS,
            ),
            SystemShortcutRowItem(
                icon = Icons.Rounded.Folder,
                iconBg = Color(0xFFE1F5FE),
                iconTint = Color(0xFF0288D1),
                titleRes = R.string.ai_cleaner_shortcut_storage_title,
                descRes = R.string.ai_cleaner_shortcut_storage_desc,
                primaryAction = Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
            ),
            SystemShortcutRowItem(
                icon = Icons.Rounded.Brightness6,
                iconBg = Color(0xFFFFF8E1),
                iconTint = Color(0xFFD97706),
                titleRes = R.string.ai_cleaner_shortcut_display_title,
                descRes = R.string.ai_cleaner_shortcut_display_desc,
                primaryAction = Settings.ACTION_DISPLAY_SETTINGS,
            ),
            SystemShortcutRowItem(
                icon = Icons.Rounded.Info,
                iconBg = Color(0xFFF1F5F9),
                iconTint = Color(0xFF475569),
                titleRes = R.string.ai_cleaner_shortcut_device_info_title,
                descRes = R.string.ai_cleaner_shortcut_device_info_desc,
                primaryAction = Settings.ACTION_DEVICE_INFO_SETTINGS,
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSetting(row.primaryAction, row.fallbackAction) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(row.iconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = row.icon,
                        contentDescription = null,
                        tint = row.iconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 8.dp),
                ) {
                    Text(
                        text = stringResource(row.titleRes),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(row.descRes),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier.size(20.dp),
                )
            }
            if (index < rows.lastIndex) {
                HorizontalDivider(
                    thickness = 0.8.dp,
                    color = DividerGray,
                )
            }
        }
    }
}

private fun handleFeatureRoute(
    context: Context,
    launcher: LawnchairLauncher?,
    route: String,
    fallbackAction: String,
) {
    val listener = AiCleanerMinusOneRegistry.featureClickListener
    if (listener != null && listener.onFeatureClick(context, route)) {
        return
    }

    val hostPkg = runCatching { LauncherHost.get() }.getOrNull()
    if (!hostPkg.isNullOrBlank()) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(hostPkg)
        val resolvedClass = launchIntent?.component?.className.orEmpty()
        if (launchIntent != null && !resolvedClass.contains("LawnchairLauncher")) {
            launchIntent.putExtra("feature_route", route)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            launchSafely(context, launcher, launchIntent)
            return
        }
    }

    openSystemSettingSafely(context, launcher, fallbackAction, null)
}

private fun openSystemSettingSafely(
    context: Context,
    launcher: LawnchairLauncher?,
    primaryAction: String,
    fallbackAction: String?,
) {
    val primary = Intent(primaryAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (context.packageManager.resolveActivity(primary, 0) != null) {
        launchSafely(context, launcher, primary)
        return
    }
    if (fallbackAction != null) {
        val fallback = Intent(fallbackAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivity(fallback, 0) != null) {
            launchSafely(context, launcher, fallback)
            return
        }
    }
    val general = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    launchSafely(context, launcher, general)
}

private fun launchSafely(
    context: Context,
    launcher: LawnchairLauncher?,
    intent: Intent,
    appInfo: AppInfo? = null,
) {
    if (launcher != null) {
        launcher.startActivitySafely(null, intent, appInfo)
    } else {
        runCatching { context.startActivity(intent) }
    }
}
