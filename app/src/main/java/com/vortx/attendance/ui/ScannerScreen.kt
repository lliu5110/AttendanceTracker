package com.vortx.attendance.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen scanner. Fires [onCode] exactly once — the analyzer latches after the
 * first successful read so a barcode held in frame cannot fire dozens of times.
 */
@Composable
fun ScannerDialog(
    onCode: (String) -> Unit,
    onManualEntry: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Ink.Base)) {

            if (hasPermission) {
                CameraFeed(onCode = onCode)
                ScanReticle()
            } else {
                PermissionPrompt(
                    onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Close control, top-left, clear of the status bar.
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .systemBarsPadding()
                    .padding(8.dp)
                    .align(Alignment.TopStart)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close scanner", tint = Ink.Primary)
            }

            if (hasPermission) {
                Text(
                    "Point at the barcode on the badge",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.Secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .systemBarsPadding()
                        .padding(top = 72.dp, start = 32.dp, end = 32.dp)
                )
            }

            // Manual fallback. Small, bottom-anchored, always reachable.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextButton(
                    onClick = onManualEntry,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Ink.Raised.copy(alpha = 0.92f))
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        "Enter manually",
                        style = MaterialTheme.typography.labelLarge,
                        color = Ink.Primary
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Camera access is off", style = MaterialTheme.typography.titleLarge, color = Ink.Primary)
        Text(
            "Attendance needs the camera to read badge barcodes. You can still type IDs in by hand.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.Secondary,
            textAlign = TextAlign.Center
        )
        TextButton(onClick = onGrant) { Text("Turn on camera", color = Ink.Primary) }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraFeed(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient()  // all formats: Code128, Code39, EAN, QR, etc.
    }
    // Latch so a code in frame across many frames only ever reports once.
    val latched = remember { AtomicBoolean(false) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(executor) { proxy: ImageProxy ->
                val media = proxy.image
                if (media == null || latched.get()) {
                    proxy.close()
                    return@setAnalyzer
                }
                val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { codes ->
                        val value = codes.firstNotNullOfOrNull(Barcode::getRawValue)
                        if (!value.isNullOrBlank() && latched.compareAndSet(false, true)) {
                            onCode(value.trim())
                        }
                    }
                    .addOnCompleteListener { proxy.close() }
            }

            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            runCatching { scanner.close() }
            executor.shutdown()
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Corner-bracket viewfinder. Brackets only, no full rectangle — it frames the target
 * without putting lines across the barcode itself.
 */
@Composable
private fun ScanReticle() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 280.dp, height = 190.dp)) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val arm = size.minDimension * 0.22f
                val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                val w = size.width
                val h = size.height

                fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                    drawLine(Color.White, Offset(x, y), Offset(x + dx, y), stroke.width, stroke.cap)
                    drawLine(Color.White, Offset(x, y), Offset(x, y + dy), stroke.width, stroke.cap)
                }
                corner(0f, 0f, arm, arm)
                corner(w, 0f, -arm, arm)
                corner(0f, h, arm, -arm)
                corner(w, h, -arm, -arm)
            }
        }
    }
}

fun Context.hasCameraPermission(): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

/** Manual ID entry — the fallback from the scanner's bottom button. */
@Composable
fun ManualEntryDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }

    InkDialog(
        title = "Enter barcode",
        onDismiss = onDismiss,
        confirmText = "Check in",
        confirmEnabled = value.isNotBlank(),
        onConfirm = { onSubmit(value.trim()) }
    ) {
        Text(
            "For someone who forgot their tag — type the number printed on their badge.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.Secondary
        )
        Spacer(Modifier.height(14.dp))
        InkTextField(
            value = value,
            onValueChange = { value = it },
            placeholder = "e.g. 10039",
            numeric = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
        )
    }
}
