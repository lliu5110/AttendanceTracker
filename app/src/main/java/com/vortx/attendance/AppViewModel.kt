package com.vortx.attendance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vortx.attendance.data.IdKind
import com.vortx.attendance.data.Person
import com.vortx.attendance.data.Repository
import com.vortx.attendance.data.ScanOutcome
import com.vortx.attendance.data.UnlinkedScan
import com.vortx.attendance.data.ranked
import com.vortx.attendance.data.todayKey
import com.vortx.attendance.util.Csv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val ADMIN_PASSWORD = "vortx"

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(app)

    private val _people = MutableStateFlow<List<Person>>(emptyList())
    val people: StateFlow<List<Person>> = _people.asStateFlow()

    private val _unlinked = MutableStateFlow<List<UnlinkedScan>>(emptyList())
    val unlinked: StateFlow<List<UnlinkedScan>> = _unlinked.asStateFlow()

    /** Admin unlock is per-session on purpose — it resets when the app is killed. */
    private val _adminMode = MutableStateFlow(false)
    val adminMode: StateFlow<Boolean> = _adminMode.asStateFlow()

    init {
        val (people, unlinked) = repo.load()
        _people.value = people
        _unlinked.value = unlinked
    }

    /** Roster ranked by attendance count — used for CSV export ordering. */
    val rankedPeople: List<Person> get() = _people.value.ranked()

    fun findByValue(value: String, kind: IdKind): Person? =
        _people.value.firstOrNull { it.owns(value, kind) }

    // ---------------------------------------------------------------- scanning

    /**
     * Core check-in handler, shared by the NFC reader, the barcode scanner and manual
     * entry. Resolves the identifier to a person and stamps today's date, or reports
     * back that nobody owns it yet.
     */
    fun submitScan(rawValue: String, kind: IdKind): ScanOutcome {
        val value = rawValue.trim()
        val person = findByValue(value, kind)
            ?: run {
                rememberUnlinked(value, kind)
                return ScanOutcome.NeedsName(value, kind)
            }

        val today = todayKey()
        if (person.dates.contains(today)) return ScanOutcome.AlreadyPresent(person)

        val updated = person.copy(dates = person.dates + today)
        replace(updated)
        return ScanOutcome.MarkedPresent(updated)
    }

    private fun rememberUnlinked(value: String, kind: IdKind) {
        val today = todayKey()
        val existing = _unlinked.value.firstOrNull { it.value == value && it.kind == kind }
        _unlinked.value = if (existing == null) {
            _unlinked.value + UnlinkedScan(value, kind, today, setOf(today))
        } else {
            _unlinked.value.map {
                if (it.value == value && it.kind == kind) it.copy(dates = it.dates + today) else it
            }
        }
        persist()
    }

    /**
     * Creates a person around an unclaimed identifier, carrying over any days already
     * banked against it. The scanned value lands in the field matching its kind, so a
     * person created from an NFC tap has their NFC ID pre-filled and can have a barcode
     * added later (and vice versa).
     */
    fun linkToNewPerson(value: String, kind: IdKind, name: String, otherId: String? = null): Person {
        val carried = unlinkedDates(value, kind)
        var person = Person(name = name.trim(), dates = carried + todayKey())
            .withId(value, kind)
        otherId?.takeIf { it.isNotBlank() }?.let { person = person.withId(it, kind.opposite()) }

        _people.value = _people.value + person
        dropUnlinked(value, kind)
        persist()
        return person
    }

    /** Attaches an unclaimed identifier to someone already on the roster. */
    fun linkToExistingPerson(value: String, kind: IdKind, uid: String): Person? {
        val target = _people.value.firstOrNull { it.uid == uid } ?: return null
        val carried = unlinkedDates(value, kind)
        val updated = target.withId(value, kind).let {
            it.copy(dates = it.dates + carried + todayKey())
        }
        replace(updated)
        dropUnlinked(value, kind)
        persist()
        return updated
    }

    private fun unlinkedDates(value: String, kind: IdKind): Set<String> =
        _unlinked.value.firstOrNull { it.value == value && it.kind == kind }?.dates ?: emptySet()

    private fun dropUnlinked(value: String, kind: IdKind) {
        _unlinked.value = _unlinked.value.filterNot { it.value == value && it.kind == kind }
    }

    private fun IdKind.opposite(): IdKind = if (this == IdKind.Nfc) IdKind.Barcode else IdKind.Nfc

    // ---------------------------------------------------------------- editing

    fun addPerson(name: String, nfc: String?, barcode: String?): Person {
        var person = Person(name = name.trim())
        nfc?.takeIf { it.isNotBlank() }?.let { person = person.withId(it, IdKind.Nfc) }
        barcode?.takeIf { it.isNotBlank() }?.let { person = person.withId(it, IdKind.Barcode) }
        _people.value = _people.value + person
        persist()
        return person
    }

    fun rename(uid: String, newName: String) {
        _people.value.firstOrNull { it.uid == uid }?.let { replace(it.copy(name = newName.trim())) }
    }

    fun addId(uid: String, value: String, kind: IdKind) {
        _people.value.firstOrNull { it.uid == uid }?.let { replace(it.withId(value, kind)) }
    }

    fun removeId(uid: String, value: String, kind: IdKind) {
        _people.value.firstOrNull { it.uid == uid }?.let { replace(it.withoutId(value, kind)) }
    }

    /** Toggles a single day — used to correct a mis-scan without deleting the person. */
    fun toggleDate(uid: String, dayKey: String) {
        _people.value.firstOrNull { it.uid == uid }?.let {
            val dates = if (it.dates.contains(dayKey)) it.dates - dayKey else it.dates + dayKey
            replace(it.copy(dates = dates))
        }
    }

    // ---------------------------------------------------------------- admin

    /** Admin-only. Removes one attendance day from one person. */
    fun deleteDate(uid: String, dayKey: String) {
        if (!_adminMode.value) return
        _people.value.firstOrNull { it.uid == uid }?.let { replace(it.copy(dates = it.dates - dayKey)) }
    }

    /**
     * Admin-only. Records a day someone attended but was never scanned for.
     * Returns false if that day was already on their record.
     */
    fun addDate(uid: String, dayKey: String): Boolean {
        if (!_adminMode.value) return false
        val person = _people.value.firstOrNull { it.uid == uid } ?: return false
        if (person.dates.contains(dayKey)) return false
        replace(person.copy(dates = person.dates + dayKey))
        return true
    }

    /**
     * Admin-only. Wipes the roster, every attendance day and every unmatched ID,
     * returning the app to a fresh install. There is no undo — the UI gates this
     * behind a typed confirmation.
     */
    fun wipeEverything() {
        if (!_adminMode.value) return
        _people.value = emptyList()
        _unlinked.value = emptyList()
        persist()
    }

    /** Admin-only. Clears a person's whole attendance history but keeps their record. */
    fun clearAttendance(uid: String) {
        if (!_adminMode.value) return
        _people.value.firstOrNull { it.uid == uid }?.let { replace(it.copy(dates = emptySet())) }
    }

    /** Admin-only. Removes the person and every attendance day attached to them. */
    fun deletePerson(uid: String) {
        if (!_adminMode.value) return
        _people.value = _people.value.filterNot { it.uid == uid }
        persist()
    }

    /** Admin-only. Discards an unmatched identifier and the days banked against it. */
    fun deleteUnlinked(value: String, kind: IdKind) {
        if (!_adminMode.value) return
        dropUnlinked(value, kind)
        persist()
    }

    fun tryUnlockAdmin(password: String): Boolean {
        val ok = password == ADMIN_PASSWORD
        if (ok) _adminMode.value = true
        return ok
    }

    fun lockAdmin() { _adminMode.value = false }

    // ---------------------------------------------------------------- csv

    fun exportCsv(): String = Csv.export(rankedPeople)

    /**
     * Merges an imported file into the roster, matching on name (case-insensitive).
     * Existing people gain the imported IDs and dates; unknown names are added.
     * Nothing is ever deleted by an import.
     */
    fun importCsv(text: String): ImportResult {
        val incoming = Csv.import(text)
        if (incoming.isEmpty()) return ImportResult(0, 0)

        var added = 0
        var merged = 0
        val current = _people.value.toMutableList()

        incoming.forEach { row ->
            val idx = current.indexOfFirst { it.name.equals(row.name, ignoreCase = true) }
            if (idx >= 0) {
                val existing = current[idx]
                current[idx] = existing.copy(
                    nfcIds = (existing.nfcIds + row.nfcIds).distinct(),
                    barcodes = (existing.barcodes + row.barcodes).distinct(),
                    dates = existing.dates + row.dates
                )
                merged++
            } else {
                current.add(row)
                added++
            }
        }

        _people.value = current
        persist()
        return ImportResult(added, merged)
    }

    data class ImportResult(val added: Int, val merged: Int)

    // ---------------------------------------------------------------- plumbing

    private fun replace(updated: Person) {
        _people.value = _people.value.map { if (it.uid == updated.uid) updated else it }
        persist()
    }

    private fun persist() {
        val snapshotPeople = _people.value
        val snapshotUnlinked = _unlinked.value
        viewModelScope.launch(Dispatchers.IO) { repo.save(snapshotPeople, snapshotUnlinked) }
    }
}
