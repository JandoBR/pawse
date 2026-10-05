package uk.pixxula.pawse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockingTest {
    private val blocked = setOf("app.blocked", "app.other")

    private fun full(app: String) = AppWindow(app, floating = false)
    private fun floating(app: String) = AppWindow(app, floating = true)

    @Test
    fun aBlockedAppOnScreenIsCovered() {
        assertEquals(full("app.blocked"), Blocking.toCover(listOf(full("app.blocked")), blocked))
    }

    @Test
    fun nothingIsCoveredWhenNoBlockedAppIsOnScreen() {
        assertNull(Blocking.toCover(listOf(full("app.free"), full("app.launcher")), blocked))
        assertNull(Blocking.toCover(emptyList(), blocked))
    }

    @Test
    fun aBlockedAppBesideAFreeOneCounts() {
        assertEquals(
            full("app.other"),
            Blocking.toCover(listOf(full("app.free"), full("app.other")), blocked),
        )
    }

    @Test
    fun aFloatingBlockedAppIsCoveredAsFloating() {
        assertEquals(
            floating("app.blocked"),
            Blocking.toCover(listOf(full("app.launcher"), floating("app.blocked")), blocked),
        )
    }

    @Test
    fun anOrdinaryBlockedWindowComesBeforeAFloatingOne() {
        assertEquals(
            full("app.other"),
            Blocking.toCover(listOf(floating("app.blocked"), full("app.other")), blocked),
        )
    }

    @Test
    fun anAppInACallIsNotCovered() {
        val onScreen = listOf(full("app.blocked"))
        assertNull(Blocking.toCover(onScreen, blocked, inCall = setOf("app.blocked")))
        assertEquals(
            full("app.blocked"),
            Blocking.toCover(onScreen, blocked, inCall = setOf("app.other")),
        )
    }

    @Test
    fun notificationsFromBlockedAppsAreHiddenExceptCalls() {
        assertEquals(true, Quiet.hides("app.blocked", "msg", blocked))
        assertEquals(true, Quiet.hides("app.blocked", null, blocked))
        assertEquals(false, Quiet.hides("app.blocked", "call", blocked))
        assertEquals(false, Quiet.hides("app.free", "msg", blocked))
        assertEquals(false, Quiet.hides("app.blocked", "msg", emptySet()))
    }

    @Test
    fun aCallNotificationPutsABlockedAppInACall() {
        val showing = listOf(
            "app.blocked" to "call",
            "app.other" to "msg",
            "app.free" to "call",
        )
        assertEquals(setOf("app.blocked"), Quiet.inCall(showing, blocked))
        assertEquals(emptySet<String>(), Quiet.inCall(showing, emptySet()))
    }

    @Test
    fun playbackIsPausedExceptDuringACall() {
        assertEquals(true, Quiet.pauses("app.blocked", blocked, emptySet()))
        assertEquals(false, Quiet.pauses("app.blocked", blocked, setOf("app.blocked")))
        assertEquals(false, Quiet.pauses("app.free", blocked, emptySet()))
    }

    @Test
    fun soundIsTakenFromABlockedAppThatDoesNotSayItIsPlaying() {
        fun takes(
            sound: Boolean = true,
            says: Set<String> = emptySet(),
            candidates: Set<String> = setOf("app.blocked"),
            inCall: Set<String> = emptySet(),
        ) = Quiet.takesSound(sound, says, candidates, blocked, inCall)

        assertEquals(true, takes())
        // Silence, or nothing blocked that could be playing: nothing to do.
        assertEquals(false, takes(sound = false))
        assertEquals(false, takes(candidates = emptySet()))
        assertEquals(false, takes(candidates = setOf("app.free")))
        // An app that is allowed to play says the sound is its own.
        assertEquals(false, takes(says = setOf("app.free")))
        // A call would be cut off.
        assertEquals(false, takes(inCall = setOf("app.other")))
    }

    @Test
    fun aFloatingFreeAppIsLeftAlone() {
        assertNull(Blocking.toCover(listOf(full("app.launcher"), floating("app.free")), blocked))
    }
}
