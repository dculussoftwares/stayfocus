package com.dculus.stayfocused.feature.devices

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Feeds camera frames to ML Kit's QR detector and reports the decoded strings of each frame to [onCodes] on the
 * main thread. Results that arrive after [close] are dropped.
 */
private class QrAnalyzer(
    private val scanner: BarcodeScanner,
    private val onCodes: (List<String>) -> Unit,
) : ImageAnalysis.Analyzer {
    @Volatile private var closed = false

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (closed || mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner
            .process(image)
            .addOnSuccessListener { codes ->
                val values = codes.mapNotNull { it.rawValue }
                if (!closed && values.isNotEmpty()) onCodes(values)
            }.addOnFailureListener { Timber.w(it, "QR detection failed") }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() {
        closed = true
    }
}

/**
 * The live back-camera preview with QR analysis. Binds to the lifecycle while composed; [onCodes] receives the
 * decoded QR strings of each frame (callers de-duplicate) and [onUnavailable] is called once when the camera can't
 * be started. Needs the CAMERA permission already granted.
 */
@Composable
internal fun QrScannerPreview(
    onCodes: (List<String>) -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCodes by rememberUpdatedState(onCodes)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    val previewView =
        remember(context) {
            PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        }
    DisposableEffect(lifecycleOwner, previewView) {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val scanner =
            BarcodeScanning.getClient(
                BarcodeScannerOptions
                    .Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build(),
            )
        requestModule(context, scanner)
        val analyzer = QrAnalyzer(scanner) { currentOnCodes(it) }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        providerFuture.addListener(
            {
                if (disposed) return@addListener
                try {
                    val cameraProvider = providerFuture.get()
                    provider = cameraProvider
                    check(cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) { "No back camera" }
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val analysis =
                        ImageAnalysis
                            .Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { it.setAnalyzer(executor, analyzer) }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    // No back camera, camera in use, or the provider failed: tell the screen.
                    Timber.w(e, "Could not start the camera")
                    currentOnUnavailable()
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            disposed = true
            analyzer.close()
            provider?.unbindAll()
            executor.shutdown()
            scanner.close()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Asks Play services to fetch the unbundled QR model now, so a first scan does not silently find nothing. */
private fun requestModule(
    context: Context,
    scanner: BarcodeScanner,
) {
    ModuleInstall
        .getClient(context)
        .installModules(ModuleInstallRequest.newBuilder().addApi(scanner).build())
        .addOnFailureListener { Timber.w(it, "Could not request the barcode model") }
}

internal fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
