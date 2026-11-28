package com.gowthaman.workout.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * [DocumentStore] over a folder the user picked through the Storage Access Framework.
 *
 * Everything SAF makes awkward is handled here and nowhere else: tree URIs, content
 * resolvers, the MIME type a document has to be created with, and providers that rename
 * files behind your back. :core asks for "food-2026-11.csv" and is told what is in it.
 *
 * Nothing is cached. A [DocumentFile] is a handle to a provider lookup rather than a file,
 * and the folder is expected to be written to by a sync client while the app is running,
 * so a stale handle is a real possibility and a fresh lookup costs very little.
 */
class SafStore(context: Context, private val treeUri: Uri) : DocumentStore {

    private val appContext = context.applicationContext
    private val resolver get() = appContext.contentResolver

    override fun list(): List<String> =
        root().listFiles().mapNotNull { if (it.isFile) it.name else null }

    override fun read(name: String): String? {
        val file = find(name) ?: return null
        return runCatching {
            resolver.openInputStream(file.uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
    }

    // "wt" truncates first, so a shorter file never keeps a tail from the old one.
    override fun write(name: String, text: String) = openAndWrite(name, text, mode = "wt")

    override fun append(name: String, text: String) = openAndWrite(name, text, mode = "wa")

    override fun delete(name: String) {
        find(name)?.delete()
    }

    private fun openAndWrite(name: String, text: String, mode: String) {
        val file = find(name)
            ?: root().createFile(mimeFor(name), name)
            ?: error("Could not create $name in the data folder.")
        val stream = resolver.openOutputStream(file.uri, mode)
            ?: error("Could not write $name in the data folder.")
        stream.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
        }
    }

    private fun root(): DocumentFile =
        DocumentFile.fromTreeUri(appContext, treeUri)
            ?: error("Data folder is not reachable. Re-select it in Settings.")

    /**
     * Looks a file up by its base name, tolerating providers that append an extension of
     * their own on create (ask for analyze.py, get analyze.py.txt), so the next run finds
     * that file instead of creating a second one beside it.
     */
    private fun find(name: String): DocumentFile? {
        val stem = name.substringBeforeLast('.')
        return root().listFiles().firstOrNull { f ->
            if (!f.isFile) return@firstOrNull false
            val n = f.name ?: return@firstOrNull false
            n == name || n.startsWith("$stem.")
        }
    }

    /**
     * SAF will not create a document without a MIME type, which is an Android requirement
     * rather than anything :core should know about -- so it is derived here from the name
     * :core asked for.
     */
    private fun mimeFor(name: String): String = when {
        name.endsWith(".csv") -> "text/csv"
        name.endsWith(".md") -> "text/markdown"
        name.endsWith(".py") -> "text/x-python"
        else -> "text/plain"
    }
}
