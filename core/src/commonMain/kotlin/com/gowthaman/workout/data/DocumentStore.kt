package com.gowthaman.workout.data

/**
 * A place that holds the data set's files, addressed by name.
 *
 * This is the whole of what a platform has to provide. Everything above it -- which files
 * exist, what a row looks like, when a month is rewritten -- is in :core and shared, so
 * adding a platform means writing these five methods and nothing else.
 *
 * What is deliberately *not* here is as important as what is. There is no folder picking,
 * no permission model, no URI and no MIME type, because that is exactly where the
 * platforms disagree:
 *
 * - Android hands over a user-chosen tree through the Storage Access Framework, and needs
 *   a MIME type to create a file.
 * - A desktop just takes a path on the command line.
 * - iOS does not really do "pick any folder you like". It gives an app its own Documents
 *   directory, which the Files app can expose for syncing. That is a different model, and
 *   the right response is to use it rather than to reach for a SAF-shaped equivalent that
 *   does not exist.
 *
 * All three can answer "read me the file called food-2026-11.csv". So that is the port,
 * and how the store came by its location stays on the platform's side of the line.
 *
 * Implementations are expected to be forgiving rather than strict: an unreadable or
 * missing file reads as null, not an exception. A half-synced folder is a normal state
 * here, not an error -- the whole storage model is a directory other programs also write
 * to.
 */
interface DocumentStore {

    /** Every file in the store, by name. Order is not significant. */
    fun list(): List<String>

    /** The file's text, or null when it does not exist or cannot be read. */
    fun read(name: String): String?

    /**
     * Replaces the file's contents, creating it when absent.
     *
     * Must truncate. Writing a shorter document over a longer one and leaving the old
     * tail behind would corrupt the file in a way CSV parsing does not catch.
     */
    fun write(name: String, text: String)

    /**
     * Adds to the end of the file, creating it when absent.
     *
     * Separate from [write] because appending one row is a far smaller window for a
     * sync client to trip over than rewriting a whole month, and that difference is the
     * reason the desktop logger appends at all.
     */
    fun append(name: String, text: String)

    /** Removes the file. Does nothing when it is already gone. */
    fun delete(name: String)
}
