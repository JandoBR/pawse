package uk.pixxula.pawse

/** Decides what is covered. No Android classes, so it is tested on the JVM. */
object Blocking {
    /** Milestone 1: one hard-coded app, blocked whenever the watcher is on. */
    val hardCoded: Set<String> = setOf("com.miui.calculator")

    /**
     * The blocked app to name on the cover, or null when none is on screen.
     * [onScreen] is every app with a window showing, not only the focused one,
     * so half of a split screen or a picture-in-picture window counts.
     */
    fun blockedOnScreen(onScreen: Collection<String>, blocked: Set<String>): String? =
        onScreen.firstOrNull { it in blocked }
}
