package com.vortx.attendance.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vortx.attendance.data.Person
import java.time.LocalDate

/**
 * Every person's full history as a row of dots, ordered by how many days they've
 * attended. One dot per day; tap a dot to name the day it stands for.
 *
 * In admin mode a selected dot also offers to remove that single day, which is the
 * fine-grained fix for a mis-scan — deleting the whole person is the blunt one.
 */
@Composable
fun AttendanceScreen(
    ranked: List<Person>,
    adminMode: Boolean,
    onDeleteDate: (Person, LocalDate) -> Unit,
    onAddDate: (Person) -> Unit,
    onClearAttendance: (Person) -> Unit,
    modifier: Modifier = Modifier
) {
    if (ranked.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                headline = "No attendance recorded",
                hint = "Check-ins appear here as dots."
            )
        }
        return
    }

    val totalDays = ranked.flatMap { it.dates }.distinct().size

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Attendance", style = MaterialTheme.typography.displaySmall, color = Ink.Primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (adminMode) "Admin unlocked — tap a dot to remove that day, or add one by hand"
                    else "$totalDays ${if (totalDays == 1) "meeting" else "meetings"} on record, ranked by turnout",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (adminMode) Ink.Danger else Ink.Secondary
                )
            }
        }

        itemsIndexed(ranked, key = { _, p -> p.uid }) { index, person ->
            AttendanceRow(
                rank = index + 1,
                person = person,
                adminMode = adminMode,
                onDeleteDate = { date -> onDeleteDate(person, date) },
                onAddDate = { onAddDate(person) },
                onClearAttendance = { onClearAttendance(person) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttendanceRow(
    rank: Int,
    person: Person,
    adminMode: Boolean,
    onDeleteDate: (LocalDate) -> Unit,
    onAddDate: () -> Unit,
    onClearAttendance: () -> Unit
) {
    // Which dot is open, if any. Local to the row so only one date shows per person.
    var selected by remember(person.uid) { mutableStateOf<LocalDate?>(null) }
    val dates = person.sortedDates

    // A removed day must not leave a stale selection pointing at it.
    if (selected != null && selected !in dates) selected = null

    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rank.toString().padStart(2, '0'),
                    style = MonoNumber,
                    color = Ink.Muted,
                    modifier = Modifier.width(26.dp)
                )
                Text(
                    person.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink.Primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    person.visitCount.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink.Primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(8.dp))
                VDivider()
                Spacer(Modifier.width(8.dp))
                Text(
                    if (person.visitCount == 1) "day" else "days",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink.Secondary
                )
            }

            Spacer(Modifier.height(12.dp))

            if (dates.isEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dots.Gap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EmptyDot()
                    Spacer(Modifier.width(2.dp))
                    Text("Not yet attended", style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dots.Gap),
                    verticalArrangement = Arrangement.spacedBy(Dots.Gap),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    dates.forEach { date ->
                        AttendanceDot(
                            date = date,
                            selected = selected == date,
                            onClick = { selected = if (selected == it) null else it }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // The line is always present, so tapping dots never shifts the rows below.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = selected?.pretty() ?: "Tap a dot for the date",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected != null) Ink.Primary else Ink.Muted,
                        modifier = Modifier.weight(1f)
                    )
                    if (adminMode) {
                        selected?.let { date ->
                            AdminAction("Remove day") { onDeleteDate(date) }
                        }
                    }
                }
            }

            // Admin actions sit outside the dots branch so a person with no attendance
            // can still have a day added by hand.
            if (adminMode) {
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AdminAction("Add day", danger = false, onClick = onAddDate)
                    if (dates.isNotEmpty()) {
                        AdminAction(
                            "Clear all ${person.visitCount} ${if (person.visitCount == 1) "day" else "days"}",
                            onClick = onClearAttendance
                        )
                    }
                }
            }
        }
    }
}

/** Compact inline admin control — a text action, not a button, to stay out of the way. */
@Composable
private fun AdminAction(label: String, danger: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodySmall,
        color = if (danger) Ink.Danger else Ink.Primary,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 5.dp)
    )
}
