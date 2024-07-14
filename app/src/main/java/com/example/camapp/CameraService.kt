package com.example.camapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.util.concurrent.Executors

class CameraService : LifecycleService() {
    private var cameraExecutor = Executors.newSingleThreadExecutor()
    private var frameRate = 30 // Default frame rate
    private lateinit var cameraControl: CameraControl // CameraControl reference
    private lateinit var cameraInfo: CameraInfo // CameraInfo reference

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        startCamera()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        frameRate = intent?.getIntExtra("frameRate", 30) ?: 30
        val zoomLevel = intent?.getFloatExtra("zoomLevel", 1.0f) ?: 1.0f
        setZoom(zoomLevel)
        setFrameRate(frameRate)
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startForegroundService() {
        val channelId = "CameraServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Camera Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Camera Service")
            .setContentText("Running...")
            .setSmallIcon(R.drawable.ic_camera)
            .build()

        startForeground(1, notification)
    }

    inner class LocalBinder : Binder() {
        fun getService(): CameraService = this@CameraService
    }

    private val binder = LocalBinder()

    fun setZoom(zoomLevel: Float) {
        Log.d("CameraService", "Setting zoom level to $zoomLevel")
        if (::cameraControl.isInitialized) {
            cameraControl.setLinearZoom(zoomLevel.coerceIn(0.0f, 1.0f))
        } else {
            Log.e("CameraService", "Camera control is not initialized.")
        }
    }

    fun setFrameRate(newFrameRate: Int) {
        frameRate = newFrameRate
        // Reconfigure the imageAnalysis or any relevant components with new frame rate
        // For simplicity, let's just print the frame rate change for now
        Log.d("CameraService", "Frame rate set to $frameRate FPS")
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                val imageAnalysis = ImageAnalysis.Builder().build().also {
                    it.setAnalyzer(cameraExecutor, ImageAnalyzer())
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this@CameraService,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                // Get the CameraControl and CameraInfo
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo

                Log.d("CameraService", "Camera initialized successfully.")
            } catch (exc: Exception) {
                Log.e("CameraService", "Error starting camera: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return binder
    }
}
