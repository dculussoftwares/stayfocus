package com.dculus.stayfocused.feature.devices

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
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
    var ready by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, previewView) {
        var disposed = false
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val scanner =
            BarcodeScanning.getClient(
                BarcodeScannerOptions
                    .Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build(),
            )
        val stopWatchingModule =
            ensureModule(
                context,
                scanner,
                onReady = { if (!disposed) ready = true },
                onFailed = { if (!disposed) currentOnUnavailable() },
            )
        val analyzer = QrAnalyzer(scanner) { currentOnCodes(it) }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
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
            stopWatchingModule()
            analyzer.close()
            provider?.unbindAll()
            executor.shutdown()
            scanner.close()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (!ready) {
            Text(
                stringResource(R.string.link_scan_preparing),
                style = StayFocusedTheme.type.body.copy(fontSize = 13.sp),
                color = StayFocusedTheme.colors.onInkMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Makes sure Play services has the unbundled QR model: [onReady] when it is (already, or after the download),
 * [onFailed] when it cannot be had. The install request does not wait, so progress is tracked with a listener.
 * Returns the function that stops watching (unregisters the listener); also called on a final update.
 */
private fun ensureModule(
    context: Context,
    scanner: BarcodeScanner,
    onReady: () -> Unit,
    onFailed: () -> Unit,
): () -> Unit {
    val client = ModuleInstall.getClient(context)
    val mainExecutor = ContextCompat.getMainExecutor(context)
    var listener: InstallStatusListener? = null
    val stop = {
        listener?.let { client.unregisterListener(it) }
        listener = null
    }
    client
        .areModulesAvailable(scanner)
        .addOnSuccessListener { response ->
            if (response.areModulesAvailable()) {
                onReady()
            } else {
                val installListener =
                    InstallStatusListener { update ->
                        when (update.installState) {
                            ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> {
                                stop()
                                onReady()
                            }

                            ModuleInstallStatusUpdate.InstallState.STATE_FAILED,
                            ModuleInstallStatusUpdate.InstallState.STATE_CANCELED,
                            -> {
                                stop()
                                onFailed()
                            }

                            else -> {
                                Unit
                            }
                        }
                    }
                listener = installListener
                client
                    .installModules(
                        ModuleInstallRequest
                            .newBuilder()
                            .addApi(scanner)
                            .setListener(installListener, mainExecutor)
                            .build(),
                    ).addOnSuccessListener {
                        if (it.areModulesAlreadyInstalled()) {
                            stop()
                            onReady()
                        }
                    }.addOnFailureListener {
                        Timber.w(it, "Could not request the barcode model")
                        stop()
                        onFailed()
                    }
            }
        }.addOnFailureListener {
            Timber.w(it, "Could not check the barcode model")
            onFailed()
        }
    return stop
}

internal fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
