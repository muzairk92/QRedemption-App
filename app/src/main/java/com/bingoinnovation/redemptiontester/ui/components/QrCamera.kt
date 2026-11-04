package com.bingoinnovation.redemptiontester.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bingoinnovation.redemptiontester.R
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

/** Problems the scanner can report while the camera is running. */
enum class ScannerProblem {
    /** Google Play services is still downloading the barcode model. */
    MODEL_NOT_READY,
}

/**
 * Shows [content] once the camera permission is granted. Otherwise explains why the
 * camera is needed and offers to ask again, or to open the app's system settings when
 * the user has chosen "Don't allow" for good.
 */
@Composable
fun CameraPermissionGate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        askedOnce = true
    }

    // The user may change the permission in system settings and come back.
    LifecycleResumeEffect(Unit) {
        granted = context.hasCameraPermission()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        if (!granted && !askedOnce) launcher.launch(Manifest.permission.CAMERA)
    }

    if (granted) {
        content()
    } else {
        val blocked = askedOnce && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        Column(
            modifier = modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(
                R.drawable.ic_qr_code_scanner,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.camera_permission_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    if (blocked) R.string.camera_permission_blocked_body else R.string.camera_permission_body,
                ),
                style = MaterialTheme.typography.bodyMedium,
                // Follows the container, so it reads on the black scanner and on light cards.
                color = LocalContentColor.current.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            if (blocked) {
                Button(onClick = { context.openAppSettings() }) {
                    Text(stringResource(R.string.camera_permission_open_settings))
                }
            } else {
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.camera_permission_allow))
                }
            }
        }
    }
}

/**
 * Live camera preview that reports the text of every QR code it sees.
 *
 * Uses CameraX's [LifecycleCameraController], so the camera follows the screen's
 * lifecycle, and ML Kit barcode scanning from Google Play services. Detection runs only
 * while [scanningEnabled] is true.
 */
@Composable
fun QrCameraPreview(
    onCodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
    scanningEnabled: Boolean = true,
    torchEnabled: Boolean = false,
    onProblem: (ScannerProblem) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCodeScanned by rememberUpdatedState(onCodeScanned)
    val currentOnProblem by rememberUpdatedState(onProblem)
    val currentScanningEnabled by rememberUpdatedState(scanningEnabled)

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
        )
        val executor = ContextCompat.getMainExecutor(context)
        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, executor) { result ->
                if (!currentScanningEnabled) return@MlKitAnalyzer
                if (result.getThrowable(scanner) != null) {
                    currentOnProblem(ScannerProblem.MODEL_NOT_READY)
                    return@MlKitAnalyzer
                }
                result.getValue(scanner)
                    ?.firstNotNullOfOrNull { it.rawValue }
                    ?.let { currentOnCodeScanned(it) }
            },
        )
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            scanner.close()
        }
    }

    LaunchedEffect(torchEnabled) {
        controller.enableTorch(torchEnabled)
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                // TextureView-based: clips to rounded shapes and animates with Compose.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        },
    )
}

/**
 * Dims everything except a rounded square viewfinder. [centerYFraction] places the
 * viewfinder's centre vertically, as a fraction of the height.
 */
@Composable
fun ScannerOverlay(
    modifier: Modifier = Modifier,
    frameFraction: Float = 0.68f,
    centerYFraction: Float = 0.5f,
    scrimColor: Color = Color.Black.copy(alpha = 0.55f),
    frameColor: Color = Color.White,
) {
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        ) {
            val side = size.minDimension * frameFraction
            val topLeft = Offset((size.width - side) / 2f, size.height * centerYFraction - side / 2f)
            val frameSize = Size(side, side)
            val radius = CornerRadius(28.dp.toPx())
            drawRect(scrimColor)
            drawRoundRect(Color.Black, topLeft, frameSize, radius, blendMode = BlendMode.Clear)
            drawRoundRect(frameColor, topLeft, frameSize, radius, style = Stroke(width = 3.dp.toPx()))
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
