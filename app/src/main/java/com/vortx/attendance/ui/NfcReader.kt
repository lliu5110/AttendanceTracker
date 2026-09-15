package com.vortx.attendance.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Whether this device can actually read a tag right now. */
enum class NfcState { Unsupported, Disabled, Ready }

fun nfcState(context: Context): NfcState {
    val adapter = NfcAdapter.getDefaultAdapter(context) ?: return NfcState.Unsupported
    return if (adapter.isEnabled) NfcState.Ready else NfcState.Disabled
}

fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Tag UID as uppercase hex — stable across taps and present on every tag type. */
private fun Tag.uidHex(): String =
    // Mask to 0..255 first: Byte is signed, so 0xFF would otherwise format as "-1".
    id.joinToString("") { byte -> "%02X".format(byte.toInt() and 0xFF) }

/**
 * Turns on NFC reader mode for as long as this composable is in the tree.
 *
 * Reader mode (rather than the foreground intent dispatch) keeps tag delivery inside
 * this activity instance, so tapping a tag never relaunches the app or interrupts
 * whatever dialog is open.
 *
 * The platform delivers tags on a binder thread, so the result is posted to the main
 * looper before it touches Compose state.
 */
@Composable
fun NfcReaderEffect(onTag: (String) -> Unit) {
    val context = LocalContext.current
    val currentOnTag by rememberUpdatedState(onTag)

    DisposableEffect(context) {
        val activity = context.findActivity()
        val adapter = NfcAdapter.getDefaultAdapter(context)
        if (activity == null || adapter == null) return@DisposableEffect onDispose { }

        val main = Handler(Looper.getMainLooper())
        val callback = NfcAdapter.ReaderCallback { tag ->
            val uid = tag.uidHex()
            if (uid.isNotBlank()) main.post { currentOnTag(uid) }
        }

        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE or
            // We only want the UID, so skip the NDEF read and the platform's tag sound.
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

        runCatching { adapter.enableReaderMode(activity, callback, flags, null) }

        onDispose { runCatching { adapter.disableReaderMode(activity) } }
    }
}

/**
 * The primary check-in surface: waiting for a tag. It stays open after a successful
 * read so a queue of people can tap through without reopening it. Both fallbacks live
 * at the bottom, one tap away, without competing with the target for attention.
 */
@Composable
fun NfcTapDialog(
    state: NfcState,
    feedback: String?,
    onTag: (String) -> Unit,
    onUseBarcode: () -> Unit,
    onEnterManually: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Ink.Base)) {

            if (state == NfcState.Ready) {
                NfcReaderEffect(onTag = onTag)
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.systemBarsPadding().padding(8.dp).align(Alignment.TopStart)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Ink.Primary)
            }

            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (state) {
                    NfcState.Ready -> {
                        PulsingTarget()
                        Spacer(Modifier.height(34.dp))
                        Text(
                            "Hold the tag to the back of the phone",
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink.Primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        // The dialog stays open between taps so a queue can be checked in
                        // without reopening it. A Snackbar can't draw above a Dialog window,
                        // so the result is reported here instead.
                        Text(
                            feedback ?: "Check-in happens as soon as it reads.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (feedback != null) Ink.Primary else Ink.Secondary,
                            textAlign = TextAlign.Center
                        )
                    }

                    NfcState.Disabled -> {
                        Text(
                            "NFC is switched off",
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink.Primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Turn it on in settings to read tags, or use a barcode instead.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink.Secondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
                        }) {
                            Text("Open NFC settings", color = Ink.Primary)
                        }
                    }

                    NfcState.Unsupported -> {
                        Text(
                            "This phone has no NFC reader",
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink.Primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Use the barcode scanner or type an ID in by hand.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink.Secondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TextButton(
                    onClick = onUseBarcode,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Ink.Raised)
                ) {
                    Text("Scan barcode instead", style = MaterialTheme.typography.labelLarge, color = Ink.Primary)
                }
                TextButton(onClick = onEnterManually) {
                    Text("Enter ID manually", style = MaterialTheme.typography.labelLarge, color = Ink.Secondary)
                }
            }
        }
    }
}

/**
 * One slow ring expanding outward. The single piece of ambient motion in the app —
 * it earns its place by signalling that the reader is live and waiting.
 */
@Composable
private fun PulsingTarget() {
    val transition = rememberInfiniteTransition(label = "nfc")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(120.dp)
                .scale(1f + pulse * 0.9f)
                .alpha((1f - pulse).coerceIn(0f, 1f) * 0.55f)
                .border(1.dp, Ink.Primary, CircleShape)
        )
        Box(Modifier.size(120.dp).border(1.dp, Ink.Line, CircleShape))
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(Ink.Primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Contactless,
                contentDescription = null,
                tint = Ink.Base,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}
