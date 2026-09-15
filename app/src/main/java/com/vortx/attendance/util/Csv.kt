package com.vortx.attendance.util

import com.vortx.attendance.data.Person

/**
 * CSV shape — one row per person:
 *
 *     name,barcode_ids,nfc_ids,days_attended,dates
 *     Ada Lovelace,10039 | 10040,04A2B1C3,3,2026-09-01 | 2026-09-04 | 2026-09-11
 *
 * Multi-values are pipe-separated inside their cell so the file stays one row per
 * person and opens cleanly in Sheets/Excel. days_attended is derived on export and
 * ignored on import — the dates column is the source of truth.
 *
 * Columns are located by header name, not position, so a file exported by an older
 * build (which had no nfc_ids column) still imports, and reordered columns are fine.
 */
object Csv {

    private const val HEADER = "name,barcode_ids,nfc_ids,days_attended,dates"
    private const val MULTI = " | "

    /** Header aliases → canonical field. Lets hand-made files use looser names. */
    private val ALIASES = mapOf(
        "name" to "name",
        "barcode_ids" to "barcodes",
        "barcodes" to "barcodes",
        "ids" to "barcodes",
        "id" to "barcodes",
        "nfc_ids" to "nfc",
        "nfc" to "nfc",
        "tag" to "nfc",
        "tags" to "nfc",
        "dates" to "dates",
        "days" to "dates"
    )

    fun export(people: List<Person>): String = buildString {
        appendLine(HEADER)
        people.sortedBy { it.name.lowercase() }.forEach { p ->
            appendLine(
                listOf(
                    p.name,
                    p.barcodes.joinToString(MULTI),
                    p.nfcIds.joinToString(MULTI),
                    p.visitCount.toString(),
                    p.dates.sorted().joinToString(MULTI)
                ).joinToString(",") { escape(it) }
            )
        }
    }

    fun import(text: String): List<Person> {
        val rows = splitRows(text).filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) return emptyList()

        val header = rows.first().map { it.trim().lowercase() }
        val hasHeader = header.any { ALIASES[it] == "name" }
        val map = if (hasHeader) columnMap(header) else legacyColumnMap(rows.first().size)
        val body = if (hasHeader) rows.drop(1) else rows

        return body.mapNotNull { cols ->
            val name = cols.getOrNull(map["name"] ?: -1)?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            Person(
                name = name,
                nfcIds = cols.cell(map["nfc"]),
                barcodes = cols.cell(map["barcodes"]),
                dates = cols.cell(map["dates"]).toSet()
            )
        }
    }

    private fun columnMap(header: List<String>): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        header.forEachIndexed { i, raw ->
            ALIASES[raw]?.let { field -> map.putIfAbsent(field, i) }
        }
        return map
    }

    /**
     * No header row. Fall back to position, distinguishing the current 5-column layout
     * from the original 4-column one (which had no nfc_ids).
     */
    private fun legacyColumnMap(width: Int): Map<String, Int> = when {
        width >= 5 -> mapOf("name" to 0, "barcodes" to 1, "nfc" to 2, "dates" to 4)
        width == 4 -> mapOf("name" to 0, "barcodes" to 1, "dates" to 3)
        width == 3 -> mapOf("name" to 0, "barcodes" to 1, "dates" to 2)
        else -> mapOf("name" to 0, "barcodes" to 1)
    }

    private fun List<String>.cell(index: Int?): List<String> {
        val raw = index?.let { getOrNull(it) } ?: return emptyList()
        return raw.split("|").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    private fun escape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value

    /** Minimal RFC-4180 reader: handles quoted fields, escaped quotes and embedded newlines. */
    private fun splitRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0

        fun endCell() { row.add(cell.toString()); cell.setLength(0) }
        fun endRow() { endCell(); rows.add(row); row = mutableListOf() }

        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> endCell()
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    endRow()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
