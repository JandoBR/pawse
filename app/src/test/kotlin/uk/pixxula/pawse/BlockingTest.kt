package uk.pixxula.pawse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockingTest {
    private val blocked = setOf("app.blocked", "app.other")

    @Test
    fun aBlockedAppOnScreenIsNamed() {
        assertEquals("app.blocked", Blocking.blockedOnScreen(listOf("app.blocked"), blocked))
    }

    @Test
    fun nothingIsCoveredWhenNoBlockedAppIsOnScreen() {
        assertNull(Blocking.blockedOnScreen(listOf("app.free", "app.launcher"), blocked))
        assertNull(Blocking.blockedOnScreen(emptyList(), blocked))
    }

    @Test
    fun aBlockedAppBesideAFreeOneCounts() {
        assertEquals("app.other", Blocking.blockedOnScreen(listOf("app.free", "app.other"), blocked))
    }
}
