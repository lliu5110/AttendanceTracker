package com.vortx.attendance.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val PrettyDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val ShortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

fun LocalDate.pretty(): String = format(PrettyDate)
fun LocalDate.short(): String = format(ShortDate)

/** Standard surface card used for every row and panel in the app. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = if (onClick != null) modifier.clickable { onClick() } else modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Ink.Raised),
        border = BorderStroke(1.dp, Ink.Line)
    ) { content() }
}

/**
 * A single attendance day. Filled = present. Tapping reports the date up so the
 * caller can surface it; the dot itself never opens its own popup, which keeps
 * only one date visible at a time per row.
 */
@Composable
fun AttendanceDot(
    date: LocalDate,
    selected: Boolean,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(Dots.Size)
            .background(if (selected) Ink.Primary else Ink.Primary.copy(alpha = 0.82f), CircleShape)
            .then(
                if (selected) Modifier.border(3.dp, Ink.LineBright, CircleShape) else Modifier
            )
            .clickable { onClick(date) }
    )
}

/** Placeholder dot for someone with no attendance yet. */
@Composable
fun EmptyDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Dots.Size)
            .border(1.dp, Ink.Muted, CircleShape)
    )
}

/** Thin vertical rule used to separate a count from its label. */
@Composable
fun VDivider(height: Int = 18) {
    Box(
        Modifier
            .width(1.dp)
            .height(height.dp)
            .background(Ink.Line)
    )
}

@Composable
fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Ink.Secondary,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
fun InkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    textStyle: TextStyle? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Ink.Muted) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        textStyle = textStyle ?: MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ink.Primary,
            unfocusedBorderColor = Ink.Line,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedTextColor = Ink.Primary,
            unfocusedTextColor = Ink.Primary,
            cursorColor = Ink.Primary
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** Consistent dialog shell so every prompt in the app looks identical. */
@Composable
fun InkDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    confirmEnabled: Boolean = true,
    onConfirm: (() -> Unit)? = null,
    dismissText: String = "Cancel",
    destructive: Boolean = false,
    body: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Raised,
        titleContentColor = Ink.Primary,
        textContentColor = Ink.Secondary,
        shape = RoundedCornerShape(18.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Column { body() } },
        confirmButton = {
            if (confirmText != null && onConfirm != null) {
                TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                    Text(
                        confirmText,
                        color = when {
                            !confirmEnabled -> Ink.Muted
                            destructive -> Ink.Danger
                            else -> Ink.Primary
                        }
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText, color = Ink.Secondary) }
        }
    )
}

/** Centred message for a screen with nothing on it yet. */
@Composable
fun EmptyState(headline: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dots.Gap)) {
            repeat(3) { EmptyDot() }
        }
        Text(
            headline,
            style = MaterialTheme.typography.titleMedium,
            color = Ink.Primary,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = Ink.Muted)
    }
}
