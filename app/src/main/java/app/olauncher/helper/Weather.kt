package app.olauncher.helper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import app.olauncher.R
import app.olauncher.data.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

// Weather from Open-Meteo: free, no API key, no account
private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
private const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
private const val TIMEOUT_MILLIS = 10_000

data class WeatherInfo(
    val temperatureCelsius: Float,
    val code: Int,
    val isDay: Boolean,
)

data class GeoPoint(val latitude: Double, val longitude: Double)

fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

/** Last known coarse location, or a fresh fix on Android 11+ when none is cached. */
@SuppressLint("MissingPermission")
suspend fun Context.getDeviceLocation(): GeoPoint? {
    if (!hasLocationPermission()) return null
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

    val lastKnown = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
        LocationManager.GPS_PROVIDER,
    ).mapNotNull { provider ->
        try {
            locationManager.getLastKnownLocation(provider)
        } catch (_: Exception) {
            null
        }
    }.maxByOrNull { it.time }
    if (lastKnown != null) return GeoPoint(lastKnown.latitude, lastKnown.longitude)

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    val provider = when {
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        else -> return null
    }
    val location = withTimeoutOrNull(TIMEOUT_MILLIS.toLong()) {
        suspendCancellableCoroutine<Location?> { continuation ->
            val cancellationSignal = android.os.CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            try {
                locationManager.getCurrentLocation(provider, cancellationSignal, mainExecutor) {
                    if (continuation.isActive) continuation.resume(it)
                }
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }
    return location?.let { GeoPoint(it.latitude, it.longitude) }
}

/** Looks up coordinates for a city name, returns null if nothing matches. */
suspend fun geocodeCity(city: String): GeoPoint? = withContext(Dispatchers.IO) {
    try {
        val query = URLEncoder.encode(city.trim(), "UTF-8")
        val json = JSONObject(httpGet("$GEOCODING_URL?name=$query&count=1&format=json"))
        val results = json.optJSONArray("results") ?: return@withContext null
        if (results.length() == 0) return@withContext null
        val first = results.getJSONObject(0)
        GeoPoint(first.getDouble("latitude"), first.getDouble("longitude"))
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

suspend fun fetchCurrentWeather(point: GeoPoint): WeatherInfo? = withContext(Dispatchers.IO) {
    try {
        val url = String.format(
            Locale.US,
            "%s?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day",
            FORECAST_URL, point.latitude, point.longitude
        )
        val current = JSONObject(httpGet(url)).getJSONObject("current")
        WeatherInfo(
            temperatureCelsius = current.getDouble("temperature_2m").toFloat(),
            code = current.getInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun httpGet(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.requestMethod = "GET"
        if (connection.responseCode != HttpURLConnection.HTTP_OK)
            throw IllegalStateException("HTTP ${connection.responseCode}")
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

/** e.g. "18° Partly cloudy" */
fun Context.formatWeather(weather: WeatherInfo, unit: Int): String {
    val temperature = if (unit == Constants.Weather.FAHRENHEIT)
        weather.temperatureCelsius * 9 / 5 + 32
    else weather.temperatureCelsius
    return getString(R.string.weather_format, temperature.roundToInt(), getString(weatherDescription(weather.code)))
}

// WMO weather interpretation codes, see https://open-meteo.com/en/docs
@StringRes
private fun weatherDescription(code: Int): Int = when (code) {
    0 -> R.string.weather_clear
    1, 2 -> R.string.weather_partly_cloudy
    3 -> R.string.weather_cloudy
    45, 48 -> R.string.weather_fog
    51, 53, 55, 56, 57 -> R.string.weather_drizzle
    61, 63, 65, 66, 67, 80, 81, 82 -> R.string.weather_rain
    71, 73, 75, 77, 85, 86 -> R.string.weather_snow
    95, 96, 99 -> R.string.weather_thunderstorm
    else -> R.string.weather_unknown
}
