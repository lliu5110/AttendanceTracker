package com.vortx.attendance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vortx.attendance.data.IdKind
import com.vortx.attendance.data.Person
import com.vortx.attendance.data.UnlinkedScan

@Composable
fun PeopleScreen(
    people: List<Person>,
    unlinked: List<UnlinkedScan>,
    adminMode: Boolean,
    onAddPerson: () -> Unit,
    onEdit: (Person) -> Unit,
    onDelete: (Person) -> Unit,
    onAdminToggle: () -> Unit,
    onResolveUnlinked: (UnlinkedScan) -> Unit,
    onDeleteUnlinked: (UnlinkedScan) -> Unit,
    onWipeEverything: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }

    val filtered = remember(people, query) {
        val q = query.trim().lowercase()
        val sorted = people.sortedBy { it.name.lowercase() }
        if (q.isEmpty()) sorted
        else sorted.filter { p ->
            p.name.lowercase().contains(q) ||
                p.barcodes.any { it.lowercase().contains(q) } ||
                p.nfcIds.any { it.lowercase().contains(q) }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("People", style = MaterialTheme.typography.displaySmall, color = Ink.Primary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (adminMode) "Admin unlocked — records can be deleted"
                        else "${people.size} ${if (people.size == 1) "person" else "people"} on file",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (adminMode) Ink.Danger else Ink.Secondary
                    )
                }
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, if (adminMode) Ink.Danger else Ink.Line, RoundedCornerShape(12.dp))
                        .clickable { onAdminToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (adminMode) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                        contentDescription = if (adminMode) "Lock admin" else "Unlock admin",
                        tint = if (adminMode) Ink.Danger else Ink.Secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        item {
            InkTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search name, tag or barcode"
            )
        }

        item {
            Panel(Modifier.fillMaxWidth(), onClick = onAddPerson) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Ink.Primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Add a person", style = MaterialTheme.typography.bodyLarge, color = Ink.Primary)
                }
            }
        }

        // IDs seen before anyone claimed them. Surfaced here so they don't get lost.
        if (unlinked.isNotEmpty()) {
            item {
                Text(
                    "Unmatched IDs",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink.Primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(unlinked, key = { "${it.kind}:${it.value}" }) { scan ->
                Panel(Modifier.fillMaxWidth(), onClick = { onResolveUnlinked(scan) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            scan.kind.icon(),
                            contentDescription = null,
                            tint = Ink.Muted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(scan.value, style = MonoNumber, color = Ink.Primary)
                            Text(
                                "${scan.kind.label} · seen ${scan.dates.size} ${if (scan.dates.size == 1) "day" else "days"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink.Secondary
                            )
                        }
                        Text("Match", style = MaterialTheme.typography.labelLarge, color = Ink.Primary)
                        if (adminMode) {
                            Spacer(Modifier.width(4.dp))
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .border(1.dp, Ink.Danger.copy(alpha = 0.5f), RoundedCornerShape(9.dp))
                                    .clickable { onDeleteUnlinked(scan) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Delete unmatched ID ${scan.value}",
                                    tint = Ink.Danger,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Roster",
                style = MaterialTheme.typography.titleMedium,
                color = Ink.Primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    headline = if (people.isEmpty()) "No one on the roster" else "No match for \"$query\"",
                    hint = if (people.isEmpty()) "Add someone, or tap a tag to create them."
                    else "Try a different name or ID."
                )
            }
        } else {
            items(filtered, key = { it.uid }) { person ->
                PersonRow(
                    person = person,
                    adminMode = adminMode,
                    onEdit = { onEdit(person) },
                    onDelete = { onDelete(person) }
                )
            }
        }

        // Sits last, below everything, so it can't be reached by accident.
        if (adminMode) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Ink.Danger.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .clickable { onWipeEverything() }
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Column {
                        Text(
                            "Erase everything",
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink.Danger
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Removes every person, attendance day and unmatched ID.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink.Secondary
                        )
                    }
                }
            }
        }
    }
}

private fun IdKind.icon(): ImageVector =
    if (this == IdKind.Nfc) Icons.Rounded.Contactless else Icons.Rounded.QrCodeScanner

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonRow(
    person: Person,
    adminMode: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Panel(Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(person.name, style = MaterialTheme.typography.titleMedium, color = Ink.Primary)
                Spacer(Modifier.height(6.dp))

                if (person.nfcIds.isEmpty() && person.barcodes.isEmpty()) {
                    Text("No tag or barcode linked", style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        person.nfcIds.forEach { IdChip(it, IdKind.Nfc) }
                        person.barcodes.forEach { IdChip(it, IdKind.Barcode) }
                    }
                    // Flag a half-filled record so it's obvious what still needs adding.
                    val missing = when {
                        person.nfcIds.isEmpty() -> "No NFC tag yet"
                        person.barcodes.isEmpty() -> "No barcode yet"
                        else -> null
                    }
                    if (missing != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(missing, style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    "${person.visitCount} ${if (person.visitCount == 1) "day" else "days"} attended",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink.Secondary
                )
            }

            if (adminMode) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Ink.Danger.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete ${person.name}",
                        tint = Ink.Danger,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun IdChip(value: String, kind: IdKind) {
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Ink.Base)
            .border(1.dp, Ink.Line, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(kind.icon(), contentDescription = kind.label, tint = Ink.Muted, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(value, style = MonoNumber, color = Ink.Secondary)
    }
}

/** Password gate for admin mode. */
@Composable
fun AdminPasswordDialog(error: Boolean, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }

    InkDialog(
        title = "Admin access",
        onDismiss = onDismiss,
        confirmText = "Unlock",
        confirmEnabled = password.isNotBlank(),
        onConfirm = { onSubmit(password) }
    ) {
        Text(
            "Deleting people, attendance days and unmatched IDs needs the admin password.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.Secondary
        )
        Spacer(Modifier.height(14.dp))
        InkTextField(value = password, onValueChange = { password = it }, placeholder = "Password")
        if (error) {
            Spacer(Modifier.height(8.dp))
            Text(
                "That password didn't work. Try again.",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.Danger
            )
        }
    }
}

/**
 * Add or edit a person. Both identifier fields are always present, so a record created
 * from an NFC tap can have its barcode filled in later, and vice versa.
 *
 * @param prefillNfc     value to pre-fill the NFC field with (a just-tapped tag).
 * @param prefillBarcode value to pre-fill the barcode field with (a just-scanned badge).
 */
@Composable
fun PersonEditorDialog(
    existing: Person?,
    prefillNfc: String = "",
    prefillBarcode: String = "",
    onSave: (name: String, nfc: String?, barcode: String?) -> Unit,
    onRemoveId: (String, IdKind) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var nfc by remember { mutableStateOf(prefillNfc) }
    var barcode by remember { mutableStateOf(prefillBarcode) }

    InkDialog(
        title = if (existing == null) "Add a person" else "Edit person",
        onDismiss = onDismiss,
        confirmText = "Save",
        confirmEnabled = name.isNotBlank(),
        onConfirm = {
            onSave(
                name.trim(),
                nfc.trim().takeIf { it.isNotEmpty() },
                barcode.trim().takeIf { it.isNotEmpty() }
            )
        }
    ) {
        FieldLabel("Name")
        InkTextField(value = name, onValueChange = { name = it }, placeholder = "Full name")

        Spacer(Modifier.height(16.dp))
        FieldLabel(if (existing == null) "NFC tag ID (optional)" else "Add an NFC tag")
        InkTextField(
            value = nfc,
            onValueChange = { nfc = it },
            placeholder = "Tap a tag on the home screen to capture",
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
        )

        Spacer(Modifier.height(16.dp))
        FieldLabel(if (existing == null) "Barcode ID (optional)" else "Add a barcode")
        InkTextField(
            value = barcode,
            onValueChange = { barcode = it },
            placeholder = "Badge number",
            numeric = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
        )

        if (existing != null && (existing.nfcIds.isNotEmpty() || existing.barcodes.isNotEmpty())) {
            Spacer(Modifier.height(16.dp))
            FieldLabel("Linked IDs")
            existing.nfcIds.forEach { LinkedIdRow(it, IdKind.Nfc, onRemoveId) }
            existing.barcodes.forEach { LinkedIdRow(it, IdKind.Barcode, onRemoveId) }
        }
    }
}

@Composable
private fun LinkedIdRow(value: String, kind: IdKind, onRemove: (String, IdKind) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(kind.icon(), contentDescription = kind.label, tint = Ink.Muted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MonoNumber, color = Ink.Primary, modifier = Modifier.weight(1f))
        TextButton(onClick = { onRemove(value, kind) }) {
            Text("Remove", style = MaterialTheme.typography.bodySmall, color = Ink.Secondary)
        }
    }
}

/**
 * Shown when a scanned identifier has no owner. The scanned value is pre-filled into
 * its matching field; the other field stays open so both can be captured at once if
 * the person happens to have both on them.
 */
@Composable
fun MatchIdDialog(
    value: String,
    kind: IdKind,
    people: List<Person>,
    onCreate: (name: String, otherId: String?) -> Unit,
    onAttach: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var otherId by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }

    val otherKind = if (kind == IdKind.Nfc) IdKind.Barcode else IdKind.Nfc

    val matches = remember(people, search) {
        val q = search.trim().lowercase()
        people.sortedBy { it.name.lowercase() }
            .filter { q.isEmpty() || it.name.lowercase().contains(q) }
            .take(6)
    }

    InkDialog(
        title = "Who is this?",
        onDismiss = onDismiss,
        confirmText = "Create",
        confirmEnabled = newName.isNotBlank(),
        onConfirm = { onCreate(newName.trim(), otherId.trim().takeIf { it.isNotEmpty() }) },
        dismissText = "Skip"
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(kind.icon(), contentDescription = null, tint = Ink.Primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(value, style = MonoNumber, color = Ink.Primary)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "This ${kind.label.lowercase()} isn't linked to anyone yet. Name it once and future scans check them in automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.Secondary
        )

        Spacer(Modifier.height(16.dp))
        FieldLabel("New person")
        InkTextField(value = newName, onValueChange = { newName = it }, placeholder = "Full name")

        Spacer(Modifier.height(12.dp))
        FieldLabel("${otherKind.label} (optional — can be added later)")
        InkTextField(
            value = otherId,
            onValueChange = { otherId = it },
            placeholder = if (otherKind == IdKind.Barcode) "Badge number" else "Tag ID",
            numeric = otherKind == IdKind.Barcode,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
        )

        if (people.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            FieldLabel("Or attach to someone already on the roster")
            InkTextField(value = search, onValueChange = { search = it }, placeholder = "Search roster")
            Spacer(Modifier.height(8.dp))
            matches.forEach { person ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAttach(person.uid) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(person.name, style = MaterialTheme.typography.bodyLarge, color = Ink.Primary)
                        val note = when {
                            person.idsOf(kind).isNotEmpty() ->
                                "already has a ${kind.label.lowercase()} — this adds another"
                            else -> "no ${kind.label.lowercase()} yet"
                        }
                        Text(note, style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
                    }
                    Text("${person.visitCount}", style = MonoNumber, color = Ink.Muted)
                }
            }
        }
    }
}

/** Confirmation before an admin wipes a person and their history. */
@Composable
fun DeleteConfirmDialog(person: Person, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    InkDialog(
        title = "Delete ${person.name}?",
        onDismiss = onDismiss,
        confirmText = "Delete",
        onConfirm = onConfirm,
        destructive = true
    ) {
        Text(
            "This removes their record, their linked IDs and all ${person.visitCount} attendance " +
                "${if (person.visitCount == 1) "day" else "days"}. It can't be undone — export a CSV first if you need a copy.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.Secondary
        )
    }
}

/** Confirmation before wiping just a person's attendance, keeping the person. */
@Composable
fun ClearAttendanceDialog(person: Person, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    InkDialog(
        title = "Clear ${person.name}'s attendance?",
        onDismiss = onDismiss,
        confirmText = "Clear",
        onConfirm = onConfirm,
        destructive = true
    ) {
        Text(
            "Removes all ${person.visitCount} ${if (person.visitCount == 1) "day" else "days"}. " +
                "${person.name} stays on the roster with their IDs intact.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.Secondary
        )
    }
}

/** Confirmation before discarding an unmatched ID and the days banked against it. */
@Composable
fun DeleteUnlinkedDialog(scan: UnlinkedScan, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    InkDialog(
        title = "Delete this ${scan.kind.label.lowercase()}?",
        onDismiss = onDismiss,
        confirmText = "Delete",
        onConfirm = onConfirm,
        destructive = true
    ) {
        Text(
            "Discards ${scan.value} and the ${scan.dates.size} " +
                "${if (scan.dates.size == 1) "day" else "days"} recorded against it. " +
                "If you match it to a person instead, those days carry across.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.Secondary
        )
    }
}
