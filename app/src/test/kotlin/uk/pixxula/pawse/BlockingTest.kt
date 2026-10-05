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
    fun aFloatingFreeAppIsLeftAlone() {
        assertNull(Blocking.toCover(listOf(full("app.launcher"), floating("app.free")), blocked))
    }
}
