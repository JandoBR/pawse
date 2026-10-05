package uk.pixxula.pawse

import android.content.ComponentName
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
    private val applyLater = Runnable { apply() }

    // One change to the session is several stored values changing, each
    // announced by itself: they are answered once.
    private val stateChanged: () -> Unit = {
        handler.removeCallbacks(applyLater)
        handler.post(applyLater)
    }
    private val sessionsChanged =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            watch(controllers.orEmpty())
        }

    override fun onListenerConnected() {
        Log.i(TAG, "hush connected")
        connected = true
        live = this
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
        live = null
        handler.removeCallbacks(silenceLater)
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
        val showing = activeNotifications.orEmpty()
        for (notification in showing) hide(notification)
        // Asked for afresh, and not taken from what was last announced: a
        // session that Android never announced would otherwise play on.
        // Watching them pauses the ones that are playing.
        val playing = sessions.getActiveSessions(ComponentName(this, Hush::class.java))
        watch(playing)
        silenceIfNeeded()
        Log.i(
            TAG,
            "hush on: ${showing.size} notifications showing, media sessions of " +
                playing.joinToString { "${it.packageName}=${it.playbackState?.state}" },
        )
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
        handler.post(silenceLater)
    }

    // Some playback never shows as playing in a media session, so pausing it
    // through one does nothing. For that there is audio focus: asking for the
    // sound makes whoever has it stop, as an incoming call would.

    private val silenceLater = Runnable { silenceIfNeeded() }

    private fun silenceIfNeeded() {
        handler.removeCallbacks(silenceLater)
        if (!connected || blocked.isEmpty()) return
        val candidates = watched.keys.map { it.packageName }.toSet() + floating
        if (candidates.none { it in blocked }) return
        // A floating window can be set playing again by a touch, which
        // nothing announces: while one is there, keep looking.
        if (floating.any { it in blocked }) handler.postDelayed(silenceLater, LOOK_AGAIN_MILLIS)

        val audio = getSystemService(AudioManager::class.java)
        val saysPlaying = watched.keys
            .filter { it.playbackState?.state in PLAYING }
            .map { it.packageName }
            .toSet()
        val take = Quiet.takesSound(
            soundPlaying = audio.isMusicActive && audio.mode == AudioManager.MODE_NORMAL,
            saysPlaying = saysPlaying,
            candidates = candidates,
            blocked = blocked,
            inCall = inCall,
        )
        if (!take) return

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build(),
            )
            .build()
        audio.requestAudioFocus(request)
        // Given straight back: the app that lost it for good does not resume.
        handler.postDelayed({ audio.abandonAudioFocusRequest(request) }, HOLD_SOUND_MILLIS)
        Log.d(TAG, "took the sound away, for ${candidates.filter { it in blocked }}")
    }

    private fun pauseIfBlocked(controller: MediaController, state: PlaybackState?) {
        if (state?.state !in PLAYING) return
        if (!Quiet.pauses(controller.packageName, blocked, inCall)) return
        controller.transportControls.pause()
        Log.d(TAG, "paused ${controller.packageName}")
    }

    companion object {
        private const val TAG = "Pawse"
        private const val SNOOZE_MILLIS = 15 * 60 * 1000L
        private const val LOOK_AGAIN_MILLIS = 2_000L
        private const val HOLD_SOUND_MILLIS = 500L

        /** The live listener, for the watcher to tell about floating windows. */
        @Volatile
        private var live: Hush? = null

        /** The apps with a floating window on screen, as the watcher sees them. */
        @Volatile
        private var floating: Set<String> = emptySet()

        fun floatingWindows(apps: Set<String>) {
            if (apps == floating) return
            floating = apps
            live?.let { it.handler.post(it.silenceLater) }
        }

        /** Playing, or about to: an app that is still buffering gets no head start. */
        private val PLAYING = setOf(
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
        )

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

        /** Whether the listener is connected in this process, not merely allowed. */
        val running: Boolean
            get() = live != null

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
