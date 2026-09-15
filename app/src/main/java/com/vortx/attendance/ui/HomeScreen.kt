package com.vortx.attendance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.vortx.attendance.data.IdKind
import com.vortx.attendance.data.Person
import java.time.LocalDate

/**
 * The tap-to-check-in button owns this screen. The barcode fallback sits directly
 * beneath it as a quiet outlined pill: one tap to reach, but visually subordinate so
 * it never competes with the primary target.
 */
@Composable
fun HomeScreen(
    today: LocalDate,
    presentToday: List<Person>,
    rosterSize: Int,
    nfcReady: Boolean,
    onTapToCheckIn: () -> Unit,
    onScanBarcode: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(today.pretty(), style = MaterialTheme.typography.bodyMedium, color = Ink.Secondary)
                Spacer(Modifier.height(4.dp))
                Text("Check in", style = MaterialTheme.typography.displaySmall, color = Ink.Primary)
            }
        }

        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TapButton(onClick = onTapToCheckIn)
                Spacer(Modifier.height(14.dp))
                Text(
                    if (nfcReady) "Tap a tag to check in" else "NFC unavailable — use a barcode",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.Secondary
                )
                Spacer(Modifier.height(14.dp))
                BarcodeFallbackButton(onClick = onScanBarcode)
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(presentToday.size.toString(), "here today", Modifier.weight(1f))
                StatTile(rosterSize.toString(), "on the roster", Modifier.weight(1f))
            }
        }

        item {
            Text(
                "Checked in today",
                style = MaterialTheme.typography.titleMedium,
                color = Ink.Primary,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
            )
        }

        if (presentToday.isEmpty()) {
            item {
                EmptyState(
                    headline = "Nobody has checked in yet",
                    hint = "Tap a tag, or scan a barcode as a backup."
                )
            }
        } else {
            items(presentToday, key = { it.uid }) { person ->
                Panel(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(8.dp).background(Ink.Primary, CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(person.name, style = MaterialTheme.typography.bodyLarge, color = Ink.Primary)
                            val shown = person.nfcIds.firstOrNull() ?: person.barcodes.firstOrNull()
                            if (shown != null) {
                                val kind = if (person.nfcIds.isNotEmpty()) IdKind.Nfc else IdKind.Barcode
                                Text("${kind.label} · $shown", style = MonoNumber, color = Ink.Muted)
                            }
                        }
                        Text("${person.visitCount}", style = MonoNumber, color = Ink.Secondary)
                    }
                }
            }
        }
    }
}

/**
 * The round primary control. 132dp reads as unmistakably the main action on a 393dp-wide
 * screen without crowding the list beneath it.
 */
@Composable
private fun TapButton(onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }

    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(162.dp)
                .border(1.dp, if (pressed) Ink.LineBright else Ink.Line, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(CircleShape)
                .background(if (pressed) Color(0xFFD6D6D6) else Ink.Primary)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                        onTap = { onClick() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Contactless,
                contentDescription = "Tap an NFC tag to check in",
                tint = Ink.Base,
                modifier = Modifier.size(52.dp)
            )
        }
    }
}

/** Outlined, not filled — present and reachable, but clearly the secondary route. */
@Composable
private fun BarcodeFallbackButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, Ink.Line, RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.QrCodeScanner,
            contentDescription = null,
            tint = Ink.Primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(9.dp))
        Text("Scan barcode", style = MaterialTheme.typography.labelLarge, color = Ink.Primary)
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = Ink.Primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = Ink.Secondary)
        }
    }
}
