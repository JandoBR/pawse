package uk.pixxula.pawse

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * The notification listener. During a session it hides blocked apps'
 * notifications, pauses their playback, and knows when one of them is in a
 * call, which the watcher then leaves uncovered. Does nothing otherwise.
 */
class Hush : NotificationListenerService() {
    private lateinit var store: Store
    private lateinit var sessions: MediaSessionManager
    private var connected = false
    private var blocked: Set<String> = emptySet()
    private val watched = HashMap<MediaController, MediaController.Callback>()

    private val handler = Handler(Looper.getMainLooper())
    private val stateChanged: () -> Unit = { handler.post { apply() } }
    private val sessionsChanged =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            watch(controllers.orEmpty())
        }

    override fun onListenerConnected() {
        Log.i(TAG, "hush connected")
        connected = true
        store = Store.get(this)
        sessions = getSystemService(MediaSessionManager::class.java)
        store.listen(stateChanged)
        val self = ComponentName(this, Hush::class.java)
        sessions.addOnActiveSessionsChangedListener(sessionsChanged, self)
        watch(sessions.getActiveSessions(self))
        apply()
    }

    override fun onListenerDisconnected() {
        Log.i(TAG, "hush disconnected")
        connected = false
        store.unlisten(stateChanged)
        sessions.removeOnActiveSessionsChangedListener(sessionsChanged)
        watch(emptyList())
        setInCall(emptySet())
    }

    override fun onNotificationPosted(notification: StatusBarNotification) {
        if (!connected) return
        refreshCalls()
        hide(notification)
    }

    override fun onNotificationRemoved(notification: StatusBarNotification) {
        if (connected) refreshCalls()
    }

    /** The session or the block list changed. */
    private fun apply() {
        if (!connected) return
        blocked = Session.blockedNow(store.state, Device.protectedApps(this))
        refreshCalls()
        if (blocked.isEmpty()) return
        for (notification in activeNotifications.orEmpty()) hide(notification)
        for (controller in watched.keys) pauseIfBlocked(controller, controller.playbackState)
    }

    private fun hide(notification: StatusBarNotification) {
        val category = notification.notification.category
        if (!Quiet.hides(notification.packageName, category, blocked)) return
        // There is no way to bring a snoozed notification back early, so it
        // is put away for a short while at a time. When it returns during a
        // session it is posted again, and lands here again.
        snoozeNotification(notification.key, SNOOZE_MILLIS)
        Log.d(TAG, "hid a notification from ${notification.packageName}")
    }

    private fun refreshCalls() {
        val showing = activeNotifications.orEmpty().map { it.packageName to it.notification.category }
        setInCall(Quiet.inCall(showing, blocked))
    }

    // Playback. Every media session is watched, so that a blocked app is
    // paused the moment it starts playing, not only when the session starts.

    private fun watch(controllers: List<MediaController>) {
        for ((controller, callback) in watched) controller.unregisterCallback(callback)
        watched.clear()
        for (controller in controllers) {
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    pauseIfBlocked(controller, state)
                }
            }
            controller.registerCallback(callback)
            watched[controller] = callback
            pauseIfBlocked(controller, controller.playbackState)
        }
    }

    private fun pauseIfBlocked(controller: MediaController, state: PlaybackState?) {
        if (state?.state != PlaybackState.STATE_PLAYING) return
        if (!Quiet.pauses(controller.packageName, blocked, inCall)) return
        controller.transportControls.pause()
        Log.d(TAG, "paused ${controller.packageName}")
    }

    companion object {
        private const val TAG = "Pawse"
        private const val SNOOZE_MILLIS = 15 * 60 * 1000L

        /** The blocked apps that are in a call right now. */
        @Volatile
        var inCall: Set<String> = emptySet()
            private set

        /** Told when [inCall] changes; the watcher sets it. */
        @Volatile
        var onCallsChanged: (() -> Unit)? = null

        private fun setInCall(apps: Set<String>) {
            if (apps == inCall) return
            inCall = apps
            Log.i(TAG, "in a call: $apps")
            onCallsChanged?.invoke()
        }

        /** Whether notification access is granted in Settings. */
        fun switchedOn(context: Context): Boolean {
            val hush = ComponentName(context, Hush::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == hush }
        }
    }
}
