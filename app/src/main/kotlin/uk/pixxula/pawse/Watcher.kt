package uk.pixxula.pawse

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * The accessibility service: knows which apps are on screen and puts the cover
 * over a blocked one. Runs with none of the app's screens open.
 */
class Watcher : AccessibilityService() {
    private lateinit var cover: Cover

    override fun onServiceConnected() {
        Log.i(TAG, "connected")
        running = true
        cover = Cover(this)
        check(null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        check(event)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "unbound")
        running = false
        cover.hide()
        return super.onUnbind(intent)
    }

    private fun check(event: AccessibilityEvent?) {
        var onScreen = appsOnScreen()
        if (onScreen.isEmpty()) {
            // The window list can come back empty in the middle of a
            // transition. The event still names the app that is arriving; if
            // there is none either, leave the cover as it is.
            val arriving = event
                ?.takeIf { it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED }
                ?.packageName?.toString()
                ?: return
            if (arriving == packageName) return
            onScreen = listOf(arriving)
        }

        val blocked = Blocking.blockedOnScreen(onScreen, Blocking.hardCoded)
        if (blocked != null) cover.show(blocked) else cover.hide()
    }

    /** The app behind every application window showing, whatever has focus. */
    private fun appsOnScreen(): List<String> =
        windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root?.packageName?.toString() }

    companion object {
        private const val TAG = "Pawse"

        /**
         * Whether the service is connected in this process. Being switched on
         * in Settings is not the same: after a crash Android leaves the
         * service switched on but does not start it again.
         */
        @Volatile
        var running = false
            private set
    }
}
