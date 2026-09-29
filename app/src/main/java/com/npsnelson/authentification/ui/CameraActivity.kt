package com.npsnelson.authentification.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.npsnelson.authentification.R
import com.npsnelson.authentification.data.AuthRepository
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {
    private lateinit var preview: androidx.camera.view.PreviewView
    private lateinit var permissionPanel: android.view.View
    private lateinit var capture: MaterialButton
    private lateinit var photo: MaterialButton
    private lateinit var gallery: MaterialButton
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var scanning = true

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else showPermissionPanel()
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let(::scanGalleryImage)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AuthRepository(this).isAuthenticated()) return goToLogin()
        setContentView(R.layout.activity_camera)
        preview = findViewById(R.id.preview)
        permissionPanel = findViewById(R.id.permissionPanel)
        capture = findViewById(R.id.capture)
        photo = findViewById(R.id.photo)
        gallery = findViewById(R.id.gallery)
        cameraExecutor = Executors.newSingleThreadExecutor()
        capture.setOnClickListener { scanning = true; Toast.makeText(this, "Pointez vers un QR code", Toast.LENGTH_SHORT).show() }
        photo.setOnClickListener { takePhoto() }
        gallery.setOnClickListener { galleryLauncher.launch("image/*") }
        findViewById<MaterialButton>(R.id.requestPermission).setOnClickListener { permissionLauncher.launch(Manifest.permission.CAMERA) }
        findViewById<MaterialButton>(R.id.logout).setOnClickListener { AuthRepository(this).logout(); goToLogin() }
        if (hasCameraPermission()) startCamera() else showPermissionPanel()
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun showPermissionPanel() {
        permissionPanel.visibility = android.view.View.VISIBLE
        capture.isEnabled = false
        photo.isEnabled = false
    }

    private fun startCamera() {
        permissionPanel.visibility = android.view.View.GONE
        capture.isEnabled = true
        photo.isEnabled = true
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val previewUseCase = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(cameraExecutor) { proxy -> analyzeFrame(proxy) }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, previewUseCase, imageCapture, analysis)
            } catch (error: Exception) {
                Toast.makeText(this, "Impossible d’ouvrir la caméra", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(proxy: ImageProxy) {
        if (!scanning) { proxy.close(); return }
        val mediaImage = proxy.image ?: run { proxy.close(); return }
        val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
        BarcodeScanning.getClient().process(input)
            .addOnSuccessListener { codes ->
                codes.firstOrNull { it.rawValue != null }?.rawValue?.let { value ->
                    scanning = false
                    runOnUiThread { Toast.makeText(this, "QR détecté : ${value.take(48)}", Toast.LENGTH_LONG).show() }
                }
            }
            .addOnCompleteListener { proxy.close() }
    }

    private fun scanGalleryImage(uri: Uri) {
        try {
            val input = InputImage.fromFilePath(this, uri)
            BarcodeScanning.getClient().process(input)
                .addOnSuccessListener { codes ->
                    val value = codes.firstOrNull { it.rawValue != null }?.rawValue
                    Toast.makeText(this, value?.let { "QR détecté : ${it.take(48)}" } ?: "Aucun QR code trouvé dans l’image", Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener { Toast.makeText(this, "Lecture de l’image impossible", Toast.LENGTH_LONG).show() }
        } catch (_: Exception) {
            Toast.makeText(this, "Image inaccessible", Toast.LENGTH_LONG).show()
        }
    }

    private fun takePhoto() {
        val captureUseCase = imageCapture ?: return
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val output = ImageCapture.OutputFileOptions.Builder(filesDir.resolve("photo_$name.jpg")).build()
        captureUseCase.takePicture(output, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) = runOnUiThread { Toast.makeText(this@CameraActivity, "Photo enregistrée dans l’espace privé de l’application", Toast.LENGTH_SHORT).show() }
            override fun onError(exception: ImageCaptureException) = runOnUiThread { Toast.makeText(this@CameraActivity, "Échec de la capture", Toast.LENGTH_SHORT).show() }
        })
    }

    private fun goToLogin() { startActivity(Intent(this, LoginActivity::class.java)); finish() }
    override fun onDestroy() { super.onDestroy(); if (::cameraExecutor.isInitialized) cameraExecutor.shutdown() }
}
