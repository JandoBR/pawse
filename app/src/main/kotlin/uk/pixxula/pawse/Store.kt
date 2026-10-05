package uk.pixxula.pawse

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * The stored state, in app-private preferences. The service and the screens
 * run in one process and share this one object.
 */
class Store private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    // Preferences keep their listeners weakly, so they are held here.
    private val listeners = HashMap<() -> Unit, SharedPreferences.OnSharedPreferenceChangeListener>()

    val state: SessionState
        get() = SessionState(
            on = prefs.getBoolean(ON, false),
            since = prefs.getLong(SINCE, 0),
            blocked = prefs.getStringSet(BLOCKED, emptySet())!!.toSet(),
            keys = readKeys(prefs.getString(KEYS, null)),
            lastScan = prefs.getLong(LAST_SCAN, -1).takeIf { it >= 0 },
        )

    /** Applies [change] to the stored state and gives back the result. */
    @Synchronized
    fun update(change: (SessionState) -> SessionState): SessionState {
        val before = state
        val after = change(before)
        if (after != before) write(after)
        return after
    }

    /** Like [update], for the changes that also have an outcome to report. */
    @Synchronized
    fun <T> updateWith(change: (SessionState) -> Pair<SessionState, T>): T {
        val before = state
        val (after, outcome) = change(before)
        if (after != before) write(after)
        return outcome
    }

    /** Calls [listener] after every change, until it is removed with [unlisten]. */
    @Synchronized
    fun listen(listener: () -> Unit) {
        val wrapped = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> listener() }
        listeners[listener] = wrapped
        prefs.registerOnSharedPreferenceChangeListener(wrapped)
    }

    @Synchronized
    fun unlisten(listener: () -> Unit) {
        listeners.remove(listener)?.let(prefs::unregisterOnSharedPreferenceChangeListener)
    }

    private fun write(state: SessionState) {
        val keys = JSONArray()
        for (key in state.keys) keys.put(JSONObject().put("id", key.id).put("name", key.name))
        prefs.edit()
            .putBoolean(ON, state.on)
            .putLong(SINCE, state.since)
            .putStringSet(BLOCKED, state.blocked)
            .putString(KEYS, keys.toString())
            .putLong(LAST_SCAN, state.lastScan ?: -1)
            // On disk before returning: a session must survive whatever
            // happens to the process next.
            .commit()
    }

    private fun readKeys(json: String?): List<Key> {
        if (json == null) return emptyList()
        val array = JSONArray(json)
        return List(array.length()) { index ->
            val key = array.getJSONObject(index)
            Key(key.getString("id"), key.getString("name"))
        }
    }

    companion object {
        private const val ON = "on"
        private const val SINCE = "since"
        private const val BLOCKED = "blocked"
        private const val KEYS = "keys"
        private const val LAST_SCAN = "last_scan"

        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(context.applicationContext).also { instance = it }
            }
    }
}
