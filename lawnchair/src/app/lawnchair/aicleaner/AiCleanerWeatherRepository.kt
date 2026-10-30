package app.lawnchair.aicleaner

import android.content.Context
import androidx.annotation.DrawableRes
import com.android.launcher3.R
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AiCleanerWeatherData(
    val city: String = "Hanoi",
    val tempC: Int = 28,
    val tempMin: Int = 22,
    val tempMax: Int = 32,
    val condition: String = "Sunny",
    val windSpeed: String = "2.47 km/h",
    val humidity: String = "82%",
    val uvIndex: String = "3 Moderate",
    val isDay: Boolean = true,
    val weatherCode: Int = 0,
) {
    @get:DrawableRes
    val iconRes: Int
        get() = when (weatherCode) {
            1, 2, 3, 45, 48 -> R.drawable.ic_ai_cleaner_weather_cloud
            51, 53, 55, 61, 63, 65, 71, 73, 75, 80, 81, 82, 95, 96, 99 -> R.drawable.ic_ai_cleaner_weather_rain
            else -> R.drawable.ic_ai_cleaner_weather_sun
        }
}

object AiCleanerWeatherRepository {
    private const val PREFS_NAME = "launcher_weather_cache_prefs"
    private const val CACHE_TTL_MS = 10 * 60 * 1000L

    private val _weatherFlow = MutableStateFlow(AiCleanerWeatherData())
    val weatherFlow: StateFlow<AiCleanerWeatherData> = _weatherFlow.asStateFlow()

    @Volatile
    private var initialized = false

    @Volatile
    private var lastFetchTimeMs = 0L

    fun ensureInitialized(context: Context) {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    _weatherFlow.value = loadFromPrefs(context.applicationContext)
                    initialized = true
                }
            }
        }
    }

    suspend fun refresh(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        val now = System.currentTimeMillis()
        if (!force && lastFetchTimeMs != 0L && now - lastFetchTimeMs < CACHE_TTL_MS) {
            return
        }

        withContext(Dispatchers.IO) {
            var lat = 21.0245
            var lon = 105.8412
            var city = _weatherFlow.value.city.ifBlank { "Hanoi" }

            try {
                val geoJson = httpGet("https://get.geojs.io/v1/ip/geo.json")
                if (geoJson != null) {
                    val geoObj = JSONObject(geoJson)
                    val parsedLat = geoObj.optString("latitude").toDoubleOrNull()
                    val parsedLon = geoObj.optString("longitude").toDoubleOrNull()
                    val parsedCity = geoObj.optString("city")
                    if (parsedLat != null && parsedLon != null) {
                        lat = parsedLat
                        lon = parsedLon
                    }
                    if (!parsedCity.isNullOrBlank()) {
                        city = parsedCity
                    }
                }
            } catch (_: Throwable) {
            }

            try {
                val weatherUrl = buildString {
                    append("https://api.open-meteo.com/v1/forecast?latitude=")
                    append(lat)
                    append("&longitude=")
                    append(lon)
                    append("&current=temperature_2m,relative_humidity_2m,is_day,weather_code,wind_speed_10m")
                    append("&daily=temperature_2m_max,temperature_2m_min,uv_index_max&timezone=auto")
                }
                val weatherJson = httpGet(weatherUrl) ?: return@withContext
                val root = JSONObject(weatherJson)
                val current = root.optJSONObject("current")
                val daily = root.optJSONObject("daily")

                val tempC = current?.optDouble("temperature_2m", 28.0)?.roundToInt() ?: 28
                val isDay = (current?.optInt("is_day", 1) ?: 1) == 1
                val weatherCode = current?.optInt("weather_code", 0) ?: 0
                val windVal = current?.optDouble("wind_speed_10m", 2.47) ?: 2.47
                val humidityVal = current?.optInt("relative_humidity_2m", 82) ?: 82

                val maxArr = daily?.optJSONArray("temperature_2m_max")
                val minArr = daily?.optJSONArray("temperature_2m_min")
                val uvArr = daily?.optJSONArray("uv_index_max")

                val tempMax = if (maxArr != null && maxArr.length() > 0) {
                    maxArr.optDouble(0, (tempC + 4).toDouble()).roundToInt()
                } else {
                    tempC + 4
                }
                val tempMin = if (minArr != null && minArr.length() > 0) {
                    minArr.optDouble(0, (tempC - 4).toDouble()).roundToInt()
                } else {
                    tempC - 4
                }
                val uvVal = if (uvArr != null && uvArr.length() > 0) {
                    uvArr.optDouble(0, 3.0)
                } else {
                    3.0
                }
                val uvInt = uvVal.roundToInt()
                val uvLabel = when {
                    uvVal < 3.0 -> "$uvInt Low"
                    uvVal < 6.0 -> "$uvInt Moderate"
                    uvVal < 8.0 -> "$uvInt High"
                    else -> "$uvInt Very High"
                }

                val updated = AiCleanerWeatherData(
                    city = city,
                    tempC = tempC,
                    tempMin = tempMin,
                    tempMax = tempMax,
                    condition = mapWeatherCode(weatherCode, isDay),
                    windSpeed = String.format(Locale.US, "%.2f km/h", windVal),
                    humidity = "$humidityVal%",
                    uvIndex = uvLabel,
                    isDay = isDay,
                    weatherCode = weatherCode,
                )
                _weatherFlow.value = updated
                lastFetchTimeMs = System.currentTimeMillis()
                saveToPrefs(appContext, updated)
            } catch (_: Throwable) {
            }
        }
    }

    private fun httpGet(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun mapWeatherCode(code: Int, isDay: Boolean): String = when (code) {
        0 -> if (isDay) "Sunny" else "Clear"
        1, 2 -> "Partly Cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rainy"
        71, 73, 75 -> "Snowy"
        80, 81, 82 -> "Showers"
        95, 96, 99 -> "Thunderstorm"
        else -> if (isDay) "Sunny" else "Clear"
    }

    private fun loadFromPrefs(context: Context): AiCleanerWeatherData = try {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        AiCleanerWeatherData(
            city = prefs.getString("city", "Hanoi") ?: "Hanoi",
            tempC = prefs.getInt("tempC", 28),
            tempMin = prefs.getInt("tempMin", 22),
            tempMax = prefs.getInt("tempMax", 32),
            condition = prefs.getString("condition", "Sunny") ?: "Sunny",
            windSpeed = prefs.getString("windSpeed", "2.47 km/h") ?: "2.47 km/h",
            humidity = prefs.getString("humidity", "82%") ?: "82%",
            uvIndex = prefs.getString("uvIndex", "3 Moderate") ?: "3 Moderate",
            isDay = prefs.getBoolean("isDay", true),
            weatherCode = prefs.getInt("weatherCode", 0),
        )
    } catch (_: Throwable) {
        AiCleanerWeatherData()
    }

    private fun saveToPrefs(context: Context, data: AiCleanerWeatherData) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("city", data.city)
                .putInt("tempC", data.tempC)
                .putInt("tempMin", data.tempMin)
                .putInt("tempMax", data.tempMax)
                .putString("condition", data.condition)
                .putString("windSpeed", data.windSpeed)
                .putString("humidity", data.humidity)
                .putString("uvIndex", data.uvIndex)
                .putBoolean("isDay", data.isDay)
                .putInt("weatherCode", data.weatherCode)
                .apply()
        } catch (_: Throwable) {
        }
    }
}
