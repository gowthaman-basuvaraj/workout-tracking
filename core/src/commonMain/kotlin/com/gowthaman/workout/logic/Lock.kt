package com.gowthaman.workout.logic

/**
 * When the app should ask for a fingerprint again.
 *
 * This is the whole of the lock's thinking, kept pure so the awkward cases can be pinned
 * down without a device. The awkward cases are the point: locking on every return to the
 * foreground sounds right and is unusable, because flicking to another app for two seconds
 * would mean authenticating again, and the folder picker returns the same way.
 *
 * What the lock is for, and what it is not: the log is plain CSV in a folder the user
 * chose, usually synced to a laptop. Anyone holding the unlocked phone can read those
 * files without going near this app. The lock keeps a borrowed phone from idly browsing
 * the app, which is a real thing to want -- but it is privacy, not security, and nothing
 * here should pretend otherwise.
 */
object LockPolicy {

    /**
     * How long the app may sit in the background before it locks.
     *
     * Long enough to answer a message, take a photo of the barbell, or pick a folder,
     * and short enough that a phone left on a table locks itself.
     */
    const val DEFAULT_GRACE_SECONDS = 60L

    /**
     * Whether to lock when the app comes back to the foreground.
     *
     * @param leftAt when the app last went to the background, or null if it has not been
     *   there this process -- a cold start, which always locks.
     */
    fun shouldLock(
        enabled: Boolean,
        leftAt: Long?,
        now: Long,
        graceSeconds: Long = DEFAULT_GRACE_SECONDS,
    ): Boolean {
        if (!enabled) return false
        if (leftAt == null) return true
        // A clock that has gone backwards cannot be reasoned about, so it locks. Erring
        // the other way would let a wrong clock hold the app open indefinitely.
        if (now < leftAt) return true
        return now - leftAt >= graceSeconds * 1000
    }
}
