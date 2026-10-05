package uk.pixxula.pawse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    private val key = Key("abc", "Plushie")
    private val ready = SessionState(blocked = setOf("app.blocked"), keys = listOf(key))
    private val nothingMissing = emptySet<Missing>()

    private fun at(seconds: Long) = Now(wall = 1_000_000 + seconds * 1000, monotonic = seconds * 1000)

    private fun started(): SessionState = Session.scan(ready, "abc", at(10), nothingMissing).first

    @Test
    fun aRegisteredKeyStartsAndEndsTheSession() {
        val (on, first) = Session.scan(ready, "abc", at(10), nothingMissing)
        assertEquals(Scan.Started, first)
        assertTrue(on.on)
        assertEquals(at(10).wall, on.since)

        val (off, second) = Session.scan(on, "abc", at(60), nothingMissing)
        assertEquals(Scan.Ended, second)
        assertFalse(off.on)
    }

    @Test
    fun anUnknownKeyCanBeRegisteredOnlyOutsideASession() {
        val (same, off) = Session.scan(ready, "other", at(10), nothingMissing)
        assertEquals(Scan.Unregistered, off)
        assertSame(ready, same)

        val on = started()
        val (still, refused) = Session.scan(on, "other", at(60), nothingMissing)
        assertEquals(Scan.Refused, refused)
        assertTrue(still.on)
    }

    @Test
    fun aStartIsRefusedWithoutAppsOrKeysOrSetup() {
        val noApps = ready.copy(blocked = emptySet())
        assertEquals(
            Scan.NotReady(setOf(Missing.APPS)),
            Session.scan(noApps, "abc", at(10), nothingMissing).second,
        )
        assertEquals(
            Scan.NotReady(setOf(Missing.WATCHER)),
            Session.scan(ready, "abc", at(10), setOf(Missing.WATCHER)).second,
        )
    }

    @Test
    fun whatIsMissingCountsThePhoneAndWhatIsStored() {
        assertEquals(emptySet<Missing>(), Session.missing(ready, nothingMissing))
        assertEquals(
            setOf(Missing.WATCHER, Missing.KEY, Missing.APPS),
            Session.missing(SessionState(), setOf(Missing.WATCHER)),
        )
    }

    @Test
    fun aTagCarriesItsIdInAPawseUri() {
        assertEquals("0123abcd", Session.keyId("pawse://key/0123abcd"))
        assertEquals(null, Session.keyId("pawse://key/"))
        assertEquals(null, Session.keyId("pawse://key/a/b"))
        assertEquals(null, Session.keyId("habitd://habit/0123abcd"))
        assertEquals(null, Session.keyId("https://example.org/pawse://key/abc"))
    }

    @Test
    fun aKeyCannotBeRenamedToNothing() {
        assertSame(ready, Session.renameKey(ready, "abc", "  "))
    }

    @Test
    fun endingNeedsNoSetup() {
        val (off, result) = Session.scan(started(), "abc", at(60), setOf(Missing.WATCHER))
        assertEquals(Scan.Ended, result)
        assertFalse(off.on)
    }

    @Test
    fun aSecondScanWithinThreeSecondsOfASwitchIsIgnored() {
        val on = started()
        val (still, soon) = Session.scan(on, "abc", at(12), nothingMissing)
        assertEquals(Scan.TooSoon, soon)
        assertSame(on, still)
        assertEquals(Scan.Ended, Session.scan(on, "abc", at(13), nothingMissing).second)
    }

    @Test
    fun retryingEarlyDoesNotPushTheWaitBack() {
        var state = started()
        for (second in listOf(11L, 12L)) {
            state = Session.scan(state, "abc", at(second), nothingMissing).first
        }
        assertEquals(Scan.Ended, Session.scan(state, "abc", at(13), nothingMissing).second)
    }

    @Test
    fun aRestartOfThePhoneDoesNotCountAsRecent() {
        // The monotonic clock started again and reads less than what is stored.
        val on = started()
        assertEquals(Scan.Ended, Session.scan(on, "abc", at(2), nothingMissing).second)
    }

    @Test
    fun onlyASessionThatIsOnBlocks() {
        assertEquals(emptySet<String>(), Session.blockedNow(ready, emptySet()))
        assertEquals(setOf("app.blocked"), Session.blockedNow(started(), emptySet()))
    }

    @Test
    fun protectedAppsAreNeverBlocked() {
        val state = ready.copy(blocked = setOf("app.blocked", "app.settings"), on = true)
        assertEquals(setOf("app.blocked"), Session.blockedNow(state, setOf("app.settings")))
        assertSame(ready, Session.setBlocked(ready, "app.settings", true, setOf("app.settings")))
    }

    @Test
    fun theBlockListIsLockedDuringASession() {
        assertEquals(
            setOf("app.blocked", "app.new"),
            Session.setBlocked(ready, "app.new", true, emptySet()).blocked,
        )
        assertEquals(
            emptySet<String>(),
            Session.setBlocked(ready, "app.blocked", false, emptySet()).blocked,
        )
        val on = started()
        assertSame(on, Session.setBlocked(on, "app.new", true, emptySet()))
        assertSame(on, Session.setBlocked(on, "app.blocked", false, emptySet()))
    }

    @Test
    fun keysAreLockedDuringASession() {
        val second = Key("def", "Spare")
        assertEquals(listOf(key, second), Session.addKey(ready, second).keys)
        assertEquals(listOf(Key("abc", "Bear")), Session.renameKey(ready, "abc", "Bear").keys)
        assertEquals(emptyList<Key>(), Session.removeKey(ready, "abc").keys)

        val on = started()
        assertSame(on, Session.addKey(on, second))
        assertSame(on, Session.renameKey(on, "abc", "Bear"))
        assertSame(on, Session.removeKey(on, "abc"))
    }

    @Test
    fun aKeyIsRegisteredOnce() {
        assertSame(ready, Session.addKey(ready, Key("abc", "Again")))
    }

    @Test
    fun switchingTheWatcherOffEndsTheSession() {
        assertFalse(Session.endBecauseWatcherIsOff(started()).on)
        assertSame(ready, Session.endBecauseWatcherIsOff(ready))
    }
}
