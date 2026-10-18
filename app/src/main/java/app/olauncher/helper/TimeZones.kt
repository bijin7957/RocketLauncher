package app.olauncher.helper

import android.icu.text.TimeZoneNames
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

// Time zones for the second clock under the date on the home screen

data class TimeZoneChoice(val id: String, val city: String, val detail: String)

private val cityZoneRegex = Regex("^(Africa|America|Asia|Atlantic|Australia|Europe|Indian|Pacific)/.+")

private val zoneNames by lazy { TimeZoneNames.getInstance(Locale.getDefault()) }

/**
 * The zone's city in the phone's language, current spelling: "Asia/Calcutta" -> "Kolkata".
 * Falls back to the id itself: "America/Argentina/Buenos_Aires" -> "Buenos Aires".
 */
fun timeZoneCity(id: String): String =
    runCatching { zoneNames.getExemplarLocationName(id) }.getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: id.substringAfterLast('/').replace('_', ' ')

/**
 * City time zones, by city name, with their country and current UTC offset, e.g. "India · GMT+5:30".
 * A zone is named after one city, so the country lets "India" find Kolkata.
 */
fun timeZoneChoices(): List<TimeZoneChoice> {
    val now = System.currentTimeMillis()
    return TimeZone.getAvailableIDs()
        // One id per zone: skips aliases like "Australia/ACT" or "Asia/Kolkata" for "Asia/Calcutta",
        // whose city still shows with its current name
        .filter { cityZoneRegex.matches(it) && android.icu.util.TimeZone.getCanonicalID(it) == it }
        .map { id ->
            TimeZoneChoice(id, timeZoneCity(id), "${timeZoneCountry(id)} · ${gmtOffset(TimeZone.getTimeZone(id).getOffset(now))}")
        }
        .sortedBy { it.city.lowercase() }
}

// Country of the zone in the phone's language, or its region like "Asia" when it has none
private fun timeZoneCountry(id: String): String {
    val code = runCatching { android.icu.util.TimeZone.getRegion(id) }.getOrNull()
    val country = if (code != null && code != "001") Locale.Builder().setRegion(code).build().displayCountry else ""
    return country.ifBlank { id.substringBeforeLast('/').replace('_', ' ').replace("/", " · ") }
}

private fun gmtOffset(offsetMillis: Int): String {
    val minutes = abs(offsetMillis) / 60_000
    val sign = if (offsetMillis < 0) "-" else "+"
    return if (minutes % 60 == 0) "GMT$sign${minutes / 60}"
    else "GMT$sign${minutes / 60}:%02d".format(minutes % 60)
}

/**
 * Label under the second clock, a TextClock pattern like "'Kochi' · EEE a" for "Kochi · Sun a.m.",
 * with [label] or else the zone's city. The weekday shows when it is already tomorrow or still
 * yesterday there, a.m./p.m. only for 12-hour time. Quotes are doubled to stay literal.
 */
fun secondClockLabelFormat(id: String, label: String, is24Hour: Boolean): String {
    val name = label.ifBlank { timeZoneCity(id) }.replace("'", "''")
    return "'$name' · EEE" + if (is24Hour) "" else " a"
}
