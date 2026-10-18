package app.olauncher.helper

import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.olauncher.R

/**
 * Started by the system when an app asks to pin a shortcut. Any installed app can ask, with any
 * label, so the user confirms first: an unasked-for "Bank" shortcut could open a look-alike app.
 */
class PinItemActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setBackgroundDrawable(null)

        val launcherApps = getSystemService(LauncherApps::class.java)
        val pinItemRequest = launcherApps.getPinItemRequest(intent)

        when (pinItemRequest != null) {
            true -> handleRequestType(pinItemRequest)
            false -> {
                showToast(R.string.invalid_pin_request)
                finish()
            }
        }
    }

    private fun handleRequestType(pinItemRequest: LauncherApps.PinItemRequest) {
        when (pinItemRequest.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> {
                confirmShortcutRequest(pinItemRequest)
                return
            }

            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET ->
                showToast(R.string.widgets_not_supported)

            else -> showToast(R.string.pin_action_not_supported)
        }
        finish()
    }

    private fun confirmShortcutRequest(pinItemRequest: LauncherApps.PinItemRequest) {
        val shortcutInfo = pinItemRequest.shortcutInfo
        if (shortcutInfo == null) {
            showToast(R.string.invalid_shortcut)
            finish()
            return
        }
        val label = (shortcutInfo.shortLabel ?: shortcutInfo.longLabel)?.toString().orEmpty()
        val dialog = createDialog(
            title = R.string.add_shortcut,
            action = R.string.add,
            messageText = getString(R.string.add_shortcut_message, appName(shortcutInfo.`package`), label),
            onAction = { acceptShortcut(pinItemRequest) },
        )
        dialog.setOnDismissListener { finish() }
        dialog.showRespectingStatusBar()
    }

    private fun acceptShortcut(pinItemRequest: LauncherApps.PinItemRequest) {
        val success = runCatching { pinItemRequest.accept() }.getOrDefault(false)
        showToast(if (success) R.string.shortcut_pinned else R.string.shortcut_pin_failed)
    }

    private fun appName(packageName: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }
}
