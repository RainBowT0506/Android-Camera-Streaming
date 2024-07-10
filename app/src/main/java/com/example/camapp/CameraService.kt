package com.example.camapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.util.concurrent.Executors

class CameraService : LifecycleService() {
    private var server: CameraHttpServer? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var frameRate = 30 // Default frame rate

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        startCamera()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        frameRate = intent?.getIntExtra("frameRate", 30) ?: 30
        server?.setFrameRate(frameRate)
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

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            val imageAnalysis = ImageAnalysis.Builder().build().also {
                it.setAnalyzer(cameraExecutor, ImageAnalyzer())
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this@CameraService,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (exc: Exception) {
                // Handle the exception
            }
        }, ContextCompat.getMainExecutor(this))

        server = CameraHttpServer(this, frameRate)
        server?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        cameraExecutor.shutdown()
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }
}
