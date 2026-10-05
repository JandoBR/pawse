package uk.pixxula.pawse

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager

/** An installed app that can go on the block list. */
data class App(val packageName: String, val name: String)

/** What the session logic needs to know about this phone. */
object Device {
    fun now() = Now(wall = System.currentTimeMillis(), monotonic = SystemClock.elapsedRealtime())

    /** Whether the accessibility service is switched on in Settings. */
    fun watcherSwitchedOn(context: Context): Boolean {
        val watcher = ComponentName(context, Watcher::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == watcher }
    }

    /** What the phone lacks for a session to start. */
    fun missing(context: Context): Set<Missing> =
        if (watcherSwitchedOn(context) && Watcher.running) emptySet() else setOf(Missing.WATCHER)

    /**
     * The apps that can never be blocked: Pawse, every launcher (on MIUI the
     * system launcher shows recents even when another is in use), the dialer,
     * the SMS app, the keyboard, and whatever hosts the Settings pages that
     * are the way out of a session. Looked up, as none of them is fixed.
     */
    fun protectedApps(context: Context): Set<String> {
        val packages = context.packageManager
        val protected = hashSetOf(context.packageName)

        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        for (launcher in packages.queryIntentActivities(home, 0)) {
            protected += launcher.activityInfo.packageName
        }

        val pages = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Settings.ACTION_SETTINGS),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null)),
        )
        for (page in pages) {
            packages.resolveActivity(page, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName?.let { protected += it }
        }

        context.getSystemService(TelecomManager::class.java)
            ?.defaultDialerPackage?.let { protected += it }
        Telephony.Sms.getDefaultSmsPackage(context)?.let { protected += it }
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')?.takeIf { it.isNotEmpty() }?.let { protected += it }

        return protected
    }

    /** Every app with a launcher icon that may be blocked, by name. */
    fun blockableApps(context: Context): List<App> {
        val packages = context.packageManager
        val protected = protectedApps(context)
        val launchable = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packages.queryIntentActivities(launchable, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filterNot { it in protected }
            .map { App(it, appName(context, it)) }
            .sortedBy { it.name.lowercase() }
    }

    fun appName(context: Context, packageName: String): String =
        try {
            val packages = context.packageManager
            packages.getApplicationLabel(packages.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
}
