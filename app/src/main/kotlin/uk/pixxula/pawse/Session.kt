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
    /** When the session was last switched, by the monotonic clock. */
    val lastToggle: Long? = null,
)

/** The time by both clocks: one to show, one to measure with. */
data class Now(val wall: Long, val monotonic: Long)

/** What a session cannot start without. */
enum class Missing { WATCHER, KEY, APPS }

/** What came of a scan, or of the debug toggle. */
sealed interface Scan {
    data object Started : Scan
    data object Ended : Scan

    /** Too soon after the last switch: the tag was read twice in a row. */
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
    const val DEBOUNCE_MILLIS = 3_000L

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
        return toggle(state, now, missing)
    }

    /**
     * Switches the session. [needsKey] is false only for the debug toggle,
     * which exists until tags do.
     */
    fun toggle(
        state: SessionState,
        now: Now,
        missing: Set<Missing>,
        needsKey: Boolean = true,
    ): Pair<SessionState, Scan> {
        val last = state.lastToggle
        // The monotonic clock starts again when the phone does, so a stored
        // time that is ahead of now is from before a restart: not recent.
        if (last != null && now.monotonic >= last && now.monotonic - last < DEBOUNCE_MILLIS) {
            return state to Scan.TooSoon
        }
        if (state.on) {
            return state.copy(on = false, lastToggle = now.monotonic) to Scan.Ended
        }

        val lacking = missing.toMutableSet()
        if (needsKey && state.keys.isEmpty()) lacking += Missing.KEY
        if (state.blocked.isEmpty()) lacking += Missing.APPS
        if (lacking.isNotEmpty()) return state to Scan.NotReady(lacking)

        return state.copy(on = true, since = now.wall, lastToggle = now.monotonic) to Scan.Started
    }

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
        if (state.on) state
        else state.copy(keys = state.keys.map { if (it.id == id) it.copy(name = name) else it })

    fun removeKey(state: SessionState, id: String): SessionState =
        if (state.on) state else state.copy(keys = state.keys.filterNot { it.id == id })
}
