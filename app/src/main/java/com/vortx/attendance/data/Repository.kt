package com.vortx.attendance.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists the roster to a single JSON file in the app's private storage.
 *
 * Deliberately not Room: the whole dataset is a few hundred rows at most, it is always
 * read in full, and keeping it as one plain file makes CSV import/export and backup
 * trivial. Writes go through a temp file so a crash mid-save cannot corrupt the roster.
 *
 * Schema v1 had barcodes only. v2 adds NFC IDs. v1 files are read transparently —
 * their "barcode" fields land in the barcode list and the NFC list starts empty.
 */
class Repository(context: Context) {

    private val file = File(context.filesDir, "roster.json")
    private val tmp = File(context.filesDir, "roster.json.tmp")

    fun load(): Pair<List<Person>, List<UnlinkedScan>> {
        if (!file.exists()) return emptyList<Person>() to emptyList()
        return try {
            val root = JSONObject(file.readText())
            val people = root.optJSONArray("people")?.let { arr ->
                (0 until arr.length()).map { readPerson(arr.getJSONObject(it)) }
            } ?: emptyList()
            val unlinked = root.optJSONArray("unlinked")?.let { arr ->
                (0 until arr.length()).map { readUnlinked(arr.getJSONObject(it)) }
            } ?: emptyList()
            people to unlinked
        } catch (e: Exception) {
            // A malformed file should not brick the app. Keep a copy for recovery and start clean.
            runCatching { file.copyTo(File(file.parentFile, "roster.corrupt.json"), overwrite = true) }
            emptyList<Person>() to emptyList()
        }
    }

    fun save(people: List<Person>, unlinked: List<UnlinkedScan>) {
        val root = JSONObject().apply {
            put("version", 2)
            put("people", JSONArray().also { arr -> people.forEach { arr.put(writePerson(it)) } })
            put("unlinked", JSONArray().also { arr -> unlinked.forEach { arr.put(writeUnlinked(it)) } })
        }
        tmp.writeText(root.toString())
        tmp.renameTo(file)
    }

    private fun readPerson(o: JSONObject) = Person(
        uid = o.optString("uid"),
        name = o.optString("name"),
        nfcIds = o.optJSONArray("nfcIds").toStringList(),
        barcodes = o.optJSONArray("barcodes").toStringList(),
        dates = o.optJSONArray("dates").toStringList().toSet()
    )

    private fun writePerson(p: Person) = JSONObject().apply {
        put("uid", p.uid)
        put("name", p.name)
        put("nfcIds", JSONArray(p.nfcIds))
        put("barcodes", JSONArray(p.barcodes))
        put("dates", JSONArray(p.dates.sorted()))
    }

    private fun readUnlinked(o: JSONObject): UnlinkedScan {
        // v1 stored the raw value under "barcode" with no kind field.
        val value = if (o.has("value")) o.optString("value") else o.optString("barcode")
        val kind = if (o.optString("kind") == IdKind.Nfc.name) IdKind.Nfc else IdKind.Barcode
        return UnlinkedScan(
            value = value,
            kind = kind,
            firstSeen = o.optString("firstSeen"),
            dates = o.optJSONArray("dates").toStringList().toSet()
        )
    }

    private fun writeUnlinked(u: UnlinkedScan) = JSONObject().apply {
        put("value", u.value)
        put("kind", u.kind.name)
        put("firstSeen", u.firstSeen)
        put("dates", JSONArray(u.dates.sorted()))
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { getString(it) }.filter { it.isNotBlank() }
    }
}
