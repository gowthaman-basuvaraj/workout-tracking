package com.gowthaman.workout.cli

import com.gowthaman.workout.data.DocumentStore
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * [DocumentStore] over a plain directory, which is what the folder looks like from a
 * desktop once a sync client has put it there.
 *
 * The simplest of the implementations, and a useful measure of the port: if a platform
 * needs more than this to hold a few dozen text files, the port has grown something it
 * should not have.
 */
class DirectoryStore(private val dir: File) : DocumentStore {

    override fun list(): List<String> =
        dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()

    override fun read(name: String): String? =
        File(dir, name).takeIf { it.isFile }?.let {
            runCatching { it.readText(StandardCharsets.UTF_8) }.getOrNull()
        }

    override fun write(name: String, text: String) {
        File(dir, name).writeText(text, StandardCharsets.UTF_8)
    }

    override fun append(name: String, text: String) {
        File(dir, name).appendText(text, StandardCharsets.UTF_8)
    }

    override fun delete(name: String) {
        File(dir, name).delete()
    }
}
