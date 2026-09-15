package com.vortx.attendance.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/** The two ways a person can be identified. */
enum class IdKind {
    Nfc,
    Barcode;

    val label: String get() = if (this == Nfc) "NFC tag" else "Barcode"
}

/**
 * One person in the roster.
 *
 * Identifiers are kept in two separate lists rather than one pooled list: a tag UID and
 * a printed badge number are different things, the editor shows them in different fields,
 * and someone can hold one without the other.
 *
 * @param uid       internal, stable key. Never shown to the user.
 * @param nfcIds    tag UIDs that resolve to this person.
 * @param barcodes  printed ID numbers that resolve to this person.
 * @param dates     ISO-8601 days (yyyy-MM-dd) this person was marked present.
 *                  A set, so scanning twice in one day cannot double-count.
 */
data class Person(
    val uid: String = UUID.randomUUID().toString(),
    val name: String,
    val nfcIds: List<String> = emptyList(),
    val barcodes: List<String> = emptyList(),
    val dates: Set<String> = emptySet()
) {
    val visitCount: Int get() = dates.size

    val sortedDates: List<LocalDate>
        get() = dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()

    fun wasPresentOn(day: LocalDate): Boolean = dates.contains(day.toKey())

    fun idsOf(kind: IdKind): List<String> = if (kind == IdKind.Nfc) nfcIds else barcodes

    fun owns(value: String, kind: IdKind): Boolean =
        idsOf(kind).any { it.equals(value.trim(), ignoreCase = true) }

    fun withId(value: String, kind: IdKind): Person {
        val v = value.trim()
        if (v.isEmpty()) return this
        return if (kind == IdKind.Nfc) copy(nfcIds = (nfcIds + v).distinct())
        else copy(barcodes = (barcodes + v).distinct())
    }

    fun withoutId(value: String, kind: IdKind): Person =
        if (kind == IdKind.Nfc) copy(nfcIds = nfcIds - value) else copy(barcodes = barcodes - value)
}

/** An identifier that was seen before anyone claimed it. */
data class UnlinkedScan(
    val value: String,
    val kind: IdKind,
    val firstSeen: String,
    val dates: Set<String> = emptySet()
)

fun LocalDate.toKey(): String = format(DateTimeFormatter.ISO_LOCAL_DATE)

fun todayKey(): String = LocalDate.now().toKey()

/**
 * Roster ranked by how many days each person attended, ties broken alphabetically.
 * Pure so the UI can derive it straight from observed state.
 */
fun List<Person>.ranked(): List<Person> =
    sortedWith(compareByDescending<Person> { it.visitCount }.thenBy { it.name.lowercase() })

/** Everyone marked present on a given day, alphabetically. */
fun List<Person>.presentOn(day: LocalDate): List<Person> =
    filter { it.wasPresentOn(day) }.sortedBy { it.name.lowercase() }

/** What happened when an identifier came back from a reader. */
sealed interface ScanOutcome {
    /** Matched a person and today was added to their record. */
    data class MarkedPresent(val person: Person) : ScanOutcome

    /** Matched a person who was already marked in for today. */
    data class AlreadyPresent(val person: Person) : ScanOutcome

    /** Nobody owns this identifier yet — the UI must ask who it belongs to. */
    data class NeedsName(val value: String, val kind: IdKind) : ScanOutcome
}
