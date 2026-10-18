package app.olauncher.helper

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Process
import app.olauncher.data.Constants

// Hosts one weather widget, picked by the user, under the date on the home screen

data class WidgetChoice(val info: AppWidgetProviderInfo, val label: String)

fun Context.weatherWidgetHost() = AppWidgetHost(applicationContext, Constants.Weather.WIDGET_HOST_ID)

/**
 * Home screen weather widgets installed for this profile, labelled "App · Widget". Apps often
 * ship several variants under one name and size, which would look identical, so those show once.
 */
fun Context.widgetChoices(): List<WidgetChoice> {
    val manager = AppWidgetManager.getInstance(this)
    val providers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        manager.getInstalledProvidersForProfile(Process.myUserHandle())
    else manager.installedProviders

    // Lock screen, cover screen and watch widgets are listed too, keep only home screen ones
    return providers.filter {
        it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0
    }.mapNotNull { info ->
        val packageName = info.provider.packageName
        val appLabel = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName
        }
        val widgetLabel = info.loadLabel(packageManager)?.toString().orEmpty()
        val label = if (widgetLabel.isBlank() || widgetLabel == appLabel) appLabel else "$appLabel · $widgetLabel"
        val isWeather = listOf(label, packageName).any { it.contains("weather", ignoreCase = true) }
        if (isWeather) WidgetChoice(info, label) else null
    }.distinctBy { it.label to widgetSizeLabel(it.info) }
        .sortedBy { it.label.lowercase() }
}

/** Whether the widget must run its configuration screen before it can be shown. */
fun AppWidgetProviderInfo.needsConfiguration(): Boolean {
    if (configure == null) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
    ) return false
    return true
}

/** Grid size like "4×2", from the widget's declared cells or its minimum size. */
fun Context.widgetSizeLabel(info: AppWidgetProviderInfo): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0 && info.targetCellHeight > 0)
        return "${info.targetCellWidth}×${info.targetCellHeight}"
    // Android's formula for cells from a widget's minimum size in dp
    val density = resources.displayMetrics.density
    fun cells(px: Int) = maxOf(1, ((px / density + 30) / 70).toInt())
    return "${cells(info.minWidth)}×${cells(info.minHeight)}"
}
