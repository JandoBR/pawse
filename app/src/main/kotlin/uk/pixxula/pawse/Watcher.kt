package uk.pixxula.pawse

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * The accessibility service: knows which app is in front and puts the cover
 * over a blocked one. Runs with none of the app's screens open.
 *
 * It asks for no window content and no window list, so that a bank app does
 * not refuse to run while it is switched on. That leaves only the window-state
 * events, which name the app that just came to the front: the app in front is
 * remembered here, and a change to the session is checked against it. Split
 * screen and picture-in-picture are no longer seen.
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

    // The app last named by a window-state event, which is what a session
    // start is checked against: with no window list there is nothing else
    // that says what is on screen.
    private var foreground: String? = null
    private var lastSeen = ""

    override fun onServiceConnected() {
        Log.i(TAG, "connected")
        running = true
        cover = Cover(this)
        store = Store.get(this)
        store.listen(stateChanged)
        Hush.onCallsChanged = { handler.post { check() } }
        handler.postDelayed({ Revive.ifStopped(this) }, 3_000)
        stateChanged()
    }

    /** The session or the block list changed: this decides what is covered. */
    private fun stateChanged() {
        if (!running) return
        // Looked up again whenever a session may be starting, so that a new
        // default launcher or SMS app is never left blocked.
        protectedApps = Device.protectedApps(this)
        check()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName
                ?.toString()
                ?.takeIf { it != packageName }
                ?.let { foreground = it }
        }
        check()
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "unbound")
        running = false
        store.unlisten(stateChanged)
        Hush.onCallsChanged = null
        foreground = null
        cover.hide()
        // Being switched off is the way out without the tag: it ends the
        // session, and switching back on does not resume it. Android also
        // unbinds services for a moment while a test tool drives the screen,
        // with the service still switched on; that ends nothing.
        if (!Device.watcherSwitchedOn(this)) store.update(Session::endBecauseWatcherIsOff)
        return super.onUnbind(intent)
    }

    private fun check() {
        val state = store.state
        val blocked = Session.blockedNow(state, protectedApps)
        if (blocked.isEmpty()) {
            cover.hide()
            Hush.floatingWindows(emptySet())
            return
        }

        // No window list without the retrieval flags: only the app last seen
        // in front is known. Nothing in front yet means nothing to do; a
        // start is checked again on the next window-state event.
        val front = foreground ?: return
        val seen = AppWindow(front, floating = false)
        log(seen)
        Hush.floatingWindows(emptySet())

        val target = Blocking.toCover(listOf(seen), blocked, Hush.inCall)
        if (target == null) cover.hide() else cover.show(target.app, state.since)
    }

    /** Diagnostics: what is in front during a session, whenever it changes. */
    private fun log(inFront: AppWindow) {
        val seen = if (inFront.floating) "${inFront.app} (floating)" else inFront.app
        if (seen != lastSeen) {
            lastSeen = seen
            Log.d(TAG, "on screen: $seen")
        }
    }

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
