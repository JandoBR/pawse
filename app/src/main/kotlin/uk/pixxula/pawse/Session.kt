package uk.pixxula.pawse

/** A registered tag. The id is what the tag carries; the name is the owner's. */
data class Key(val id: String, val name: String)

/** Everything that is stored. */
data class SessionState(
    val on: Boolean = false,
    /** When the session started, by the wall clock, for showing. */
    val since: Long = 0,
    val blocked: Set<String> = emptySet(),
    val keys: List<Key> = emptyList(),
    /** When a key was last scanned, by the monotonic clock. */
    val lastScan: Long? = null,
)

/** The time by both clocks: one to show, one to measure with. */
data class Now(val wall: Long, val monotonic: Long)

/** What a session cannot start without. */
enum class Missing { WATCHER, NOTIFICATIONS, BATTERY, NFC, KEY, APPS }

/** What came of a scan. */
sealed interface Scan {
    data object Started : Scan
    data object Ended : Scan

    /** Too soon after the last scan: the tag is being read over and over. */
    data object TooSoon : Scan

    data class NotReady(val missing: Set<Missing>) : Scan

    /** Not a registered key, with no session on: it can be registered. */
    data object Unregistered : Scan

    /** Not a registered key, with a session on: nothing happens. */
    data object Refused : Scan
}

/**
 * The rules of a session. No Android classes, so it is tested on the JVM;
 * every function takes the state and gives back the new one.
 */
object Session {
    const val DEBOUNCE_MILLIS = 5_000L

    /** A tag was scanned. [missing] is what the phone itself lacks. */
    fun scan(
        state: SessionState,
        keyId: String,
        now: Now,
        missing: Set<Missing>,
    ): Pair<SessionState, Scan> {
        if (state.keys.none { it.id == keyId }) {
            return state to if (state.on) Scan.Refused else Scan.Unregistered
        }
        val last = state.lastScan
        val scanned = state.copy(lastScan = now.monotonic)
        // A tag left against the phone is read again every few seconds. Each
        // read that comes too soon also counts as the last one, so the
        // session switches once however long the tag stays there.
        // The monotonic clock starts again when the phone does, so a stored
        // time that is ahead of now is from before a restart: not recent.
        if (last != null && now.monotonic >= last && now.monotonic - last < DEBOUNCE_MILLIS) {
            return scanned to Scan.TooSoon
        }
        if (state.on) {
            return scanned.copy(on = false) to Scan.Ended
        }

        val lacking = missing(state, missing)
        if (lacking.isNotEmpty()) return scanned to Scan.NotReady(lacking)

        return scanned.copy(on = true, since = now.wall) to Scan.Started
    }

    /** Everything a start still lacks: what the phone lacks, and what is stored. */
    fun missing(state: SessionState, phone: Set<Missing>): Set<Missing> {
        val lacking = phone.toMutableSet()
        if (state.keys.isEmpty()) lacking += Missing.KEY
        if (state.blocked.isEmpty()) lacking += Missing.APPS
        return lacking
    }

    /**
     * The id a tag carries, from its `pawse://key/<id>` record, or null when
     * the text is anything else.
     */
    fun keyId(uri: String): String? =
        KEY_URI.matchEntire(uri)?.groupValues?.get(1)

    private val KEY_URI = Regex("pawse://key/([A-Za-z0-9_-]{1,64})")

    /** The accessibility service was switched off: that ends a session. */
    fun endBecauseWatcherIsOff(state: SessionState): SessionState =
        if (state.on) state.copy(on = false) else state

    /** The apps to cover right now. [protected] can never be blocked. */
    fun blockedNow(state: SessionState, protected: Set<String>): Set<String> =
        if (state.on) state.blocked - protected else emptySet()

    // The block list and the keys cannot be changed while a session is on:
    // each of these gives the state back untouched then.

    fun setBlocked(
        state: SessionState,
        app: String,
        blocked: Boolean,
        protected: Set<String>,
    ): SessionState = when {
        state.on -> state
        blocked && app in protected -> state
        blocked -> state.copy(blocked = state.blocked + app)
        else -> state.copy(blocked = state.blocked - app)
    }

    fun addKey(state: SessionState, key: Key): SessionState =
        if (state.on || state.keys.any { it.id == key.id }) state
        else state.copy(keys = state.keys + key)

    fun renameKey(state: SessionState, id: String, name: String): SessionState =
        if (state.on || name.isBlank()) state
        else state.copy(keys = state.keys.map { if (it.id == id) it.copy(name = name) else it })

    fun removeKey(state: SessionState, id: String): SessionState =
        if (state.on) state else state.copy(keys = state.keys.filterNot { it.id == id })
}
