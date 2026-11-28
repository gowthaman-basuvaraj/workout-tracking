package com.gowthaman.workout.data

/** Minimal RFC-4180 style CSV reader/writer. One record per physical line. */
object Csv {

    fun field(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    fun row(vararg values: String): String = values.joinToString(",") { field(it) }

    fun row(values: List<String>): String = values.joinToString(",") { field(it) }

    fun parse(line: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    cur.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    out.add(cur.toString()); cur.setLength(0)
                }
                else -> cur.append(c)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }

    /** Records are line-based, so free text must not carry newlines into the file. */
    fun clean(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim()

    fun lines(text: String): List<String> =
        text.split('\n').map { it.removeSuffix("\r") }.filter { it.isNotBlank() }
}

/** Reads a parsed row by header name, so added columns never shift existing ones. */
class CsvRow(private val header: List<String>, private val values: List<String>) {
    fun str(name: String, default: String = ""): String {
        val i = header.indexOf(name)
        return if (i >= 0 && i < values.size) values[i] else default
    }

    fun int(name: String, default: Int = 0): Int = str(name).trim().toIntOrNull() ?: default

    fun double(name: String, default: Double = 0.0): Double =
        str(name).trim().replace(',', '.').toDoubleOrNull() ?: default

    fun bool(name: String, default: Boolean = false): Boolean = when (str(name).trim().lowercase()) {
        "true", "1", "yes", "y" -> true
        "false", "0", "no", "n" -> false
        else -> default
    }

    fun long(name: String, default: Long = 0L): Long = str(name).trim().toLongOrNull() ?: default
}

fun parseCsv(text: String): List<CsvRow> {
    val lines = Csv.lines(text)
    if (lines.isEmpty()) return emptyList()
    val header = Csv.parse(lines.first()).map { it.trim() }
    return lines.drop(1).map { CsvRow(header, Csv.parse(it)) }
}
