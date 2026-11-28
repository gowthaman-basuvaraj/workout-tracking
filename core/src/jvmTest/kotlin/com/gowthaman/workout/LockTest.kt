package com.gowthaman.workout

import com.gowthaman.workout.logic.LockPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockTest {

    private val t0 = 1_795_000_000_000L
    private fun seconds(n: Long) = n * 1000

    @Test
    fun `an unlocked app never locks`() {
        assertFalse(LockPolicy.shouldLock(enabled = false, leftAt = null, now = t0))
        assertFalse(
            LockPolicy.shouldLock(enabled = false, leftAt = t0 - seconds(600), now = t0)
        )
    }

    @Test
    fun `a cold start locks`() {
        // No record of leaving means this process has just started, so whatever was on
        // screen before was a different process and must not be shown again unasked.
        assertTrue(LockPolicy.shouldLock(enabled = true, leftAt = null, now = t0))
    }

    @Test
    fun `flicking to another app and straight back does not lock`() {
        // The case that makes a lock unusable if it is got wrong.
        assertFalse(LockPolicy.shouldLock(enabled = true, leftAt = t0 - seconds(2), now = t0))
    }

    @Test
    fun `a minute away locks`() {
        assertTrue(LockPolicy.shouldLock(enabled = true, leftAt = t0 - seconds(60), now = t0))
        assertTrue(LockPolicy.shouldLock(enabled = true, leftAt = t0 - seconds(3600), now = t0))
    }

    @Test
    fun `the grace period boundary locks rather than stays open`() {
        val grace = LockPolicy.DEFAULT_GRACE_SECONDS
        assertFalse(
            LockPolicy.shouldLock(enabled = true, leftAt = t0 - seconds(grace) + 1, now = t0)
        )
        assertTrue(
            LockPolicy.shouldLock(enabled = true, leftAt = t0 - seconds(grace), now = t0)
        )
    }

    @Test
    fun `a longer folder-picking trip still locks, and that is survivable`() {
        // Picking a data folder can take longer than the grace period. The result is still
        // delivered to the app; the user simply authenticates afterwards, so nothing is
        // lost -- which is why this does not need special-casing.
        assertTrue(LockPolicy.shouldLock(enabled = true, leftAt = t0 - seconds(180), now = t0))
    }

    @Test
    fun `a clock that has gone backwards locks`() {
        // A wrong clock must not be able to hold the app open. Erring towards asking is
        // the only safe direction when the arithmetic cannot be trusted.
        assertTrue(LockPolicy.shouldLock(enabled = true, leftAt = t0 + seconds(5), now = t0))
    }

    @Test
    fun `a custom grace period is respected`() {
        assertFalse(
            LockPolicy.shouldLock(
                enabled = true, leftAt = t0 - seconds(20), now = t0, graceSeconds = 30,
            )
        )
        assertTrue(
            LockPolicy.shouldLock(
                enabled = true, leftAt = t0 - seconds(40), now = t0, graceSeconds = 30,
            )
        )
    }

    @Test
    fun `a zero grace period locks on any return`() {
        assertTrue(
            LockPolicy.shouldLock(enabled = true, leftAt = t0, now = t0, graceSeconds = 0)
        )
    }
}
