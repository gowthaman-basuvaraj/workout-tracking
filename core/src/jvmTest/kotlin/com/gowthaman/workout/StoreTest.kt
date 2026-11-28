package com.gowthaman.workout

import com.gowthaman.workout.data.CsvFormat
import com.gowthaman.workout.data.CsvStore
import com.gowthaman.workout.data.DocumentStore
import com.gowthaman.workout.data.FoodScale
import com.gowthaman.workout.data.KeyValueStore
import com.gowthaman.workout.data.Prefs
import com.gowthaman.workout.data.Purpose
import com.gowthaman.workout.data.FoodTag
import com.gowthaman.workout.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The data layer, tested without a device.
 *
 * None of this was reachable from a test before: reading and writing the folder lived in
 * :app on top of the Storage Access Framework, so the only way to find out whether logging
 * a meal produced the right row was to install the app and look at the file. The port
 * makes the whole path -- repository, archive, CSV -- a pure function of a map of strings.
 */

/** A folder that exists only in memory. */
private class FakeStore(
    val files: MutableMap<String, String> = LinkedHashMap(),
) : DocumentStore {
    override fun list(): List<String> = files.keys.toList()
    override fun read(name: String): String? = files[name]
    override fun write(name: String, text: String) { files[name] = text }
    override fun append(name: String, text: String) {
        files[name] = files[name].orEmpty() + text
    }
    override fun delete(name: String) { files.remove(name) }
}

private class FakeSettingsStore : KeyValueStore {
    private val values = HashMap<String, Any?>()

    override fun getString(key: String, fallback: String?) = values[key] as? String ?: fallback
    override fun getInt(key: String, fallback: Int) = values[key] as? Int ?: fallback
    override fun getBoolean(key: String, fallback: Boolean) = values[key] as? Boolean ?: fallback
    override fun getFloat(key: String, fallback: Float) = values[key] as? Float ?: fallback

    override fun write(block: (KeyValueStore.Writer) -> Unit) {
        block(object : KeyValueStore.Writer {
            override fun putString(key: String, value: String?) { values[key] = value }
            override fun putInt(key: String, value: Int) { values[key] = value }
            override fun putBoolean(key: String, value: Boolean) { values[key] = value }
            override fun putFloat(key: String, value: Float) { values[key] = value }
        })
    }
}

private val NOV_28 = LocalDate(2026, 11, 28)

private fun repoOn(store: DocumentStore): Repo {
    val repo = Repo(
        prefs = Prefs(FakeSettingsStore()),
        openStore = { store },
        bundledDoc = { "" },
        // Unconfined runs each launch inline, so a test can assert straight after the call
        // instead of waiting on a real thread.
        io = Dispatchers.Unconfined,
    )
    repo.useFolder("in-memory")
    return repo
}

class StoreTest {

    @Test
    fun `logging food writes a row into that month's file`() {
        val store = FakeStore()
        repoOn(store).addFood(NOV_28, "Dal", Purpose.DINNER, FoodScale.EVERYDAY, setOf(FoodTag.PLANTS))

        val csv = store.files.getValue("food-2026-11.csv")
        val row = csv.trim().lines().last().split(",")
        val header = csv.trim().lines().first().split(",")

        assertEquals("Dal", row[header.indexOf("item")])
        assertEquals("DINNER", row[header.indexOf("purpose")])
        assertEquals("PLANTS", row[header.indexOf("tags")])
        assertEquals("1", row[header.indexOf("qty")])
    }

    @Test
    fun `a month emptied of rows leaves no file behind`() {
        val store = FakeStore()
        val repo = repoOn(store)
        repo.addFood(NOV_28, "Dal", Purpose.DINNER, FoodScale.EVERYDAY)
        val id = repo.food.value.single().id

        repo.deleteFood(id)

        assertFalse(
            "an emptied month should be deleted, not left as a lone header",
            store.files.containsKey("food-2026-11.csv"),
        )
    }

    @Test
    fun `what was written reads back through a fresh repository`() {
        val store = FakeStore()
        repoOn(store).addFood(NOV_28, "Idli", Purpose.BREAKFAST, FoodScale.HEALTHY, qty = 2)

        // A second repository over the same folder is what the desktop CLI, or the app
        // after a restart, actually does.
        val reopened = repoOn(store)

        val entry = reopened.food.value.single()
        assertEquals("Idli", entry.item)
        assertEquals(Purpose.BREAKFAST, entry.purpose)
        assertEquals(2, entry.qty)
    }

    @Test
    fun `a conflict copy dropped in by a sync client is ignored`() {
        val store = FakeStore()
        repoOn(store).addFood(NOV_28, "Dal", Purpose.DINNER, FoodScale.EVERYDAY)

        // Syncthing's name for a file two devices both changed.
        store.files["food-2026-11.sync-conflict-20261128-120000-ABCDEFG.csv"] =
            store.files.getValue("food-2026-11.csv")

        assertEquals(
            "the conflict copy must not be loaded as a second set of rows",
            1,
            repoOn(store).food.value.size,
        )
    }
}

/**
 * The append path, which is the desktop logger's normal write and the one that broke
 * before: a row written under a header that predates one of its columns lands every value
 * after the missing column one place to the left.
 */
class AppendTest {

    private val header = CsvFormat.FOOD_HEADER
    private val row = "abc123,2026-11-28,Dal,DINNER,2,PLANTS,1,,1795839622160"

    @Test
    fun `appending to a missing file writes the header first`() {
        val store = FakeStore()
        CsvStore(store).appendRow("food-2026-11.csv", header, row)

        val lines = store.files.getValue("food-2026-11.csv").trim().lines()
        assertEquals(CsvFormat.FOOD_HEADER, lines.first().split(","))
        assertEquals(2, lines.size)
    }

    @Test
    fun `a file written before the tags column is realigned, not corrupted`() {
        val store = FakeStore()
        store.files["food-2026-11.csv"] =
            "id,date,item,purpose,scale,qty,notes,logged_at\n" +
                "old1,2026-11-05,Idli,BREAKFAST,1,2,,1790000000000\n"

        CsvStore(store).appendRow("food-2026-11.csv", header, row)

        val lines = store.files.getValue("food-2026-11.csv").trim().lines()
        val cols = lines.first().split(",")
        val old = lines[1].split(",")

        assertEquals("the header should have grown a tags column", header, cols)
        // The whole point: qty stays qty rather than sliding into tags.
        assertEquals("2", old[cols.indexOf("qty")])
        assertEquals("", old[cols.indexOf("tags")])
        assertEquals("1790000000000", old[cols.indexOf("logged_at")])
    }

    @Test
    fun `a hand-edited file with no trailing newline does not get two rows on one line`() {
        val store = FakeStore()
        store.files["food-2026-11.csv"] =
            CsvFormat.FOOD_HEADER.joinToString(",") + "\n" +
                "old1,2026-11-05,Idli,BREAKFAST,1,,2,,1790000000000"

        CsvStore(store).appendRow("food-2026-11.csv", header, row)

        val lines = store.files.getValue("food-2026-11.csv").trim().lines()
        assertEquals(3, lines.size)
        assertTrue(lines[2].startsWith("abc123,"))
    }

    @Test
    fun `an unknown file reads as absent rather than throwing`() {
        assertNull(FakeStore().read("nothing-here.csv"))
    }
}
