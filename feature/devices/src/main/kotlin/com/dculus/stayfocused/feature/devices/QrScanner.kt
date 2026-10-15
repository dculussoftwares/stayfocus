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
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Feeds camera frames to ML Kit's QR detector and reports each decoded string to [onCode] on the main thread. */
private class QrAnalyzer(
    private val onCode: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val scanner =
        BarcodeScanning.getClient(
            BarcodeScannerOptions
                .Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner
            .process(image)
            .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let(onCode) }
            .addOnFailureListener { Timber.w(it, "QR detection failed") }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() = scanner.close()
}

/**
 * The live back-camera preview with QR analysis. Binds to the lifecycle while composed; [onCode] receives every
 * decoded QR string (callers de-duplicate). Needs the CAMERA permission already granted.
 */
@Composable
internal fun QrScannerPreview(
    onCode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val previewView =
        remember(context) {
            PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        }
    DisposableEffect(lifecycleOwner, previewView) {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val analyzer = QrAnalyzer { currentOnCode(it) }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        providerFuture.addListener(
            {
                if (disposed) return@addListener
                try {
                    val cameraProvider = providerFuture.get()
                    provider = cameraProvider
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
                    // No back camera, camera in use, or the provider failed: the screen just shows no preview.
                    Timber.w(e, "Could not start the camera")
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            disposed = true
            provider?.unbindAll()
            executor.shutdown()
            analyzer.close()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

internal fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
