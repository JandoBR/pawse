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
     * the cover offers can make it go away.
     */
    fun toCover(onScreen: List<AppWindow>, blocked: Set<String>): AppWindow? =
        onScreen.filter { it.app in blocked }.minByOrNull { it.floating }
}
