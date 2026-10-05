package uk.pixxula.pawse

/** An app with a window on screen. [floating] means picture-in-picture. */
data class AppWindow(val app: String, val floating: Boolean)

/** Decides what is covered. No Android classes, so it is tested on the JVM. */
object Blocking {
    /**
     * The window to cover, or null when no blocked app is on screen.
     * [onScreen] is every app window showing, not only the focused one. A
     * blocked app in an ordinary window comes first, as it takes the whole
     * screen; a floating one is covered only where it is, because nothing
     * the cover offers can make it go away. An app that is [inCall] is not
     * covered at all: its call screen cannot be told from the rest of it.
     */
    fun toCover(
        onScreen: List<AppWindow>,
        blocked: Set<String>,
        inCall: Set<String> = emptySet(),
    ): AppWindow? =
        onScreen.filter { it.app in blocked && it.app !in inCall }.minByOrNull { it.floating }
}

/**
 * What is hushed during a session: notifications hidden and playback paused,
 * except for an app that is in a call.
 */
object Quiet {
    /** How apps mark the notification of an incoming or ongoing call. */
    private const val CALL = "call"

    /** Whether a notification from [app] is hidden. A call's never is. */
    fun hides(app: String, category: String?, blocked: Set<String>): Boolean =
        app in blocked && category != CALL

    /**
     * The blocked apps in a call, from the notifications showing, each given
     * as its app and its category.
     */
    fun inCall(showing: List<Pair<String, String?>>, blocked: Set<String>): Set<String> =
        showing.filter { (app, category) -> app in blocked && category == CALL }
            .map { it.first }
            .toSet()

    /** Whether [app] is paused when it plays. Not while it is in a call. */
    fun pauses(app: String, blocked: Set<String>, inCall: Set<String>): Boolean =
        app in blocked && app !in inCall
}
