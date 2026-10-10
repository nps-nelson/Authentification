package com.nps.authenticator.ui

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

private fun newScanner(): BarcodeScanner = BarcodeScanning.getClient(
    BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
)

private fun List<Barcode>.firstValue(): String? =
    mapNotNull { it.rawValue }.firstOrNull { it.isNotBlank() }

private class QrAnalyzer(
    private val scanner: BarcodeScanner,
    private val onCode: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        try {
            val media = proxy.image
            if (media == null) {
                proxy.close()
                return
            }
            val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { list -> list.firstValue()?.let(onCode) }
                .addOnCompleteListener { proxy.close() }
        } catch (e: Exception) {
            proxy.close()
        }
    }
}

/** Aperçu caméra + détection des QR. La torche suit le paramètre [torch]. */
@Composable
fun QrScannerView(torch: Boolean, onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val latestOnCode by rememberUpdatedState(onCode)

    DisposableEffect(owner) {
        val executor = Executors.newSingleThreadExecutor()
        val scanner = newScanner()
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, QrAnalyzer(scanner) { latestOnCode(it) })
                p.unbindAll()
                camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                // Caméra indisponible : l'écran reste noir, la saisie manuelle reste utilisable.
            }
        }, ContextCompat.getMainExecutor(ctx))

        onDispose {
            provider?.unbindAll()
            executor.shutdown()
            scanner.close()
        }
    }

    LaunchedEffect(torch, camera) {
        camera?.cameraControl?.enableTorch(torch)
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Lit un QR depuis une image de la galerie. Rappelle onResult(null) si rien de valide. */
fun scanImage(ctx: Context, uri: Uri, onResult: (String?) -> Unit) {
    val scanner = newScanner()
    try {
        val image = InputImage.fromFilePath(ctx, uri)
        scanner.process(image)
            .addOnSuccessListener { list -> onResult(list.firstValue()) }
            .addOnFailureListener { onResult(null) }
            .addOnCompleteListener { scanner.close() }
    } catch (e: Exception) {
        scanner.close()
        onResult(null)
    }
}
