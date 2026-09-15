package com.vortx.attendance.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vortx.attendance.data.Person
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Admin: record a day someone attended but was never scanned for.
 *
 * Future dates are blocked — attendance is a record of what happened, and allowing
 * them would silently corrupt the ranking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDayDialog(
    person: Person,
    today: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val todayMillis = remember(today) { today.toEpochMillisUtc() }

    val state = rememberDatePickerState(
        initialSelectedDateMillis = todayMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int) = year <= today.year
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(containerColor = Ink.Raised),
        shape = RoundedCornerShape(18.dp),
        confirmButton = {
            val millis = state.selectedDateMillis
            TextButton(
                onClick = { millis?.let { onConfirm(it.toLocalDateUtc()) } },
                enabled = millis != null
            ) {
                Text("Add day", color = if (millis != null) Ink.Primary else Ink.Muted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Ink.Secondary) }
        }
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    "Day attended",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.Secondary,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                )
            },
            headline = {
                Text(
                    person.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink.Primary,
                    modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
                )
            },
            colors = DatePickerDefaults.colors(
                containerColor = Ink.Raised,
                titleContentColor = Ink.Secondary,
                headlineContentColor = Ink.Primary,
                weekdayContentColor = Ink.Secondary,
                dayContentColor = Ink.Primary,
                disabledDayContentColor = Ink.Muted,
                selectedDayContentColor = Ink.Base,
                selectedDayContainerColor = Ink.Primary,
                todayContentColor = Ink.Primary,
                todayDateBorderColor = Ink.LineBright,
                navigationContentColor = Ink.Primary,
                yearContentColor = Ink.Primary,
                currentYearContentColor = Ink.Primary,
                selectedYearContentColor = Ink.Base,
                selectedYearContainerColor = Ink.Primary
            )
        )
    }
}

/** The picker works in UTC midnight, so convert on both edges rather than using the default zone. */
private fun LocalDate.toEpochMillisUtc(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * Admin: erase everything.
 *
 * Gated behind typing the word, not just a second tap — this is the one action in the
 * app that can't be partially recovered, and a mis-tap here costs the whole dataset.
 */
@Composable
fun WipeEverythingDialog(
    peopleCount: Int,
    dayCount: Int,
    unmatchedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var typed by remember { mutableStateOf("") }
    val armed = typed.trim().equals("ERASE", ignoreCase = false)

    InkDialog(
        title = "Erase everything?",
        onDismiss = onDismiss,
        confirmText = "Erase",
        confirmEnabled = armed,
        onConfirm = onConfirm,
        destructive = true
    ) {
        Text(
            "This deletes all $peopleCount ${if (peopleCount == 1) "person" else "people"}, " +
                "$dayCount attendance ${if (dayCount == 1) "day" else "days"} and " +
                "$unmatchedCount unmatched ${if (unmatchedCount == 1) "ID" else "IDs"}, " +
                "leaving the app as it was on install. Export a CSV first if you want a copy.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.Secondary
        )
        Spacer(Modifier.height(16.dp))
        FieldLabel("Type ERASE to confirm")
        InkTextField(value = typed, onValueChange = { typed = it }, placeholder = "ERASE")
    }
}
