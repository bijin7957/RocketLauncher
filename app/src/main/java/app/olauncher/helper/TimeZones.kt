package app.olauncher.helper

import java.util.TimeZone
import kotlin.math.abs

// Time zones for the second clock under the date on the home screen

data class TimeZoneChoice(val id: String, val city: String, val detail: String)

private val cityZoneRegex = Regex("^(Africa|America|Asia|Atlantic|Australia|Europe|Indian|Pacific)/.+")

/** "Asia/Kolkata" -> "Kolkata", "America/Argentina/Buenos_Aires" -> "Buenos Aires" */
fun timeZoneCity(id: String): String = id.substringAfterLast('/').replace('_', ' ')

/** City time zones, by city name, with their region and current UTC offset, e.g. "Asia · GMT+5:30". */
fun timeZoneChoices(): List<TimeZoneChoice> {
    val now = System.currentTimeMillis()
    return TimeZone.getAvailableIDs()
        // Skips old aliases like "Australia/ACT" that duplicate a city zone
        .filter { cityZoneRegex.matches(it) && android.icu.util.TimeZone.getCanonicalID(it) == it }
        .map { id ->
            val region = id.substringBeforeLast('/').replace('_', ' ').replace("/", " · ")
            TimeZoneChoice(id, timeZoneCity(id), "$region · ${gmtOffset(TimeZone.getTimeZone(id).getOffset(now))}")
        }
        .sortedBy { it.city.lowercase() }
}

private fun gmtOffset(offsetMillis: Int): String {
    val minutes = abs(offsetMillis) / 60_000
    val sign = if (offsetMillis < 0) "-" else "+"
    return if (minutes % 60 == 0) "GMT$sign${minutes / 60}"
    else "GMT$sign${minutes / 60}:%02d".format(minutes % 60)
}

/**
 * TextClock pattern like "'Kolkata' · EEE h:mm a". The weekday shows when it is already
 * tomorrow or still yesterday there; quotes in the city are doubled to stay literal.
 */
fun secondClockFormat(id: String, is24Hour: Boolean): String {
    val city = timeZoneCity(id).replace("'", "''")
    return "'$city' · EEE " + if (is24Hour) "HH:mm" else "h:mm a"
}
