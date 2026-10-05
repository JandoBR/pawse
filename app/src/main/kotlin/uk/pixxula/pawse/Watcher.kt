package uk.pixxula.pawse

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * The accessibility service: knows which apps are on screen and puts the cover
 * over a blocked one. Runs with none of the app's screens open.
 */
class Watcher : AccessibilityService() {
    private lateinit var cover: Cover
    private lateinit var store: Store
    private var protectedApps: Set<String> = emptySet()
    private val stateChangedLater = Runnable { stateChanged() }

    // One change to the session is several stored values changing, each
    // announced by itself: they are answered once.
    private val stateChanged: () -> Unit = {
        handler.removeCallbacks(stateChangedLater)
        handler.post(stateChangedLater)
    }
    private val handler = Handler(Looper.getMainLooper())
    private val recheck = Runnable { check(null) }
    private var rechecks = 0
    private var lastSeen = ""

    override fun onServiceConnected() {
        Log.i(TAG, "connected")
        running = true
        cover = Cover(this)
        store = Store.get(this)
        store.listen(stateChanged)
        Hush.onCallsChanged = { handler.post { check(null) } }
        stateChanged()
    }

    /** The session or the block list changed: this decides what is covered. */
    private fun stateChanged() {
        if (!running) return
        // Looked up again whenever a session may be starting, so that a new
        // default launcher or SMS app is never left blocked.
        protectedApps = Device.protectedApps(this)
        check(null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        rechecks = 0
        check(event)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "unbound")
        running = false
        handler.removeCallbacks(recheck)
        store.unlisten(stateChanged)
        Hush.onCallsChanged = null
        cover.hide()
        // Being switched off is the way out without the tag: it ends the
        // session, and switching back on does not resume it. Android also
        // unbinds services for a moment while a test tool drives the screen,
        // with the service still switched on; that ends nothing.
        if (!Device.watcherSwitchedOn(this)) store.update(Session::endBecauseWatcherIsOff)
        return super.onUnbind(intent)
    }

    private fun check(event: AccessibilityEvent?) {
        val state = store.state
        val blocked = Session.blockedNow(state, protectedApps)
        if (blocked.isEmpty()) {
            handler.removeCallbacks(recheck)
            cover.hide()
            return
        }

        val arriving = event
            ?.takeIf { it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED }
            ?.packageName?.toString()
            ?.takeIf { it != packageName }

        val bounds = HashMap<AppWindow, Rect>()
        var unnamed = false
        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            // A window that has not drawn yet has no content to ask for its
            // app. The event that announced it names the app; failing that,
            // look again shortly.
            val app = window.root?.packageName?.toString() ?: arriving
            if (app == null) {
                unnamed = true
                continue
            }
            val seen = AppWindow(app, floating = window.isInPictureInPictureMode)
            bounds[seen] = Rect().also { window.getBoundsInScreen(it) }
        }
        log(bounds.keys, unnamed)

        handler.removeCallbacks(recheck)
        if (unnamed && rechecks < MAX_RECHECKS) {
            rechecks++
            handler.postDelayed(recheck, RECHECK_MILLIS)
        }
        // The window list can come back empty in the middle of a transition:
        // leave the cover as it is until there is something to go by.
        if (bounds.isEmpty()) return

        val target = Blocking.toCover(bounds.keys.toList(), blocked, Hush.inCall)
        when {
            target == null -> if (!unnamed) cover.hide()
            target.floating -> cover.showOver(target.app, bounds.getValue(target))
            else -> cover.show(target.app, state.since)
        }
    }

    /** Diagnostics: what is on screen during a session, whenever it changes. */
    private fun log(onScreen: Collection<AppWindow>, unnamed: Boolean) {
        val seen = onScreen.joinToString { if (it.floating) "${it.app} (floating)" else it.app } +
            if (unnamed) ", and one not yet named" else ""
        if (seen != lastSeen) {
            lastSeen = seen
            Log.d(TAG, "on screen: $seen")
        }
    }

    companion object {
        private const val TAG = "Pawse"
        private const val RECHECK_MILLIS = 250L
        private const val MAX_RECHECKS = 8

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
