package uk.pixxula.pawse

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log

/**
 * Brings the two services back when they are allowed in Settings but not
 * running. That is how MIUI leaves them after stopping the app (the "clear
 * all" button in recents does it) and refusing to let it start again: Android
 * marks the accessibility service as crashed and gives up on both.
 *
 * Nothing is offered for asking Android to try again, but it looks at a
 * package's services afresh whenever one of its parts is switched off or on.
 * So the stopped service is switched off and straight back on, as a part of
 * the app, which is not the same switch as the one in Settings.
 */
object Revive {
    private const val TAG = "Pawse"
    private const val WAIT_MILLIS = 10_000L

    @Volatile
    private var lastTry = 0L

    fun ifStopped(context: Context) {
        val watcher = Device.watcherSwitchedOn(context) && !Watcher.running
        val hush = Hush.switchedOn(context) && !Hush.running
        if (!watcher && !hush) return

        // Not over and over: the services take a moment to connect.
        val now = SystemClock.elapsedRealtime()
        if (now - lastTry < WAIT_MILLIS) return
        lastTry = now

        if (watcher) restart(context, Watcher::class.java)
        if (hush) restart(context, Hush::class.java)
    }

    private fun restart(context: Context, service: Class<*>) {
        Log.i(TAG, "asking for ${service.simpleName} back")
        val component = ComponentName(context, service)
        val packages = context.packageManager
        packages.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        packages.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP,
        )
    }
}
