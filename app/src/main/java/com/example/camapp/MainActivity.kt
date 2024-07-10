package com.example.camapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.camapp.ui.theme.CamAppTheme

class MainActivity : ComponentActivity() {
    private var server: CameraHttpServer? = null
    private var serverStatus by mutableStateOf("Server not started")
    private var serverIp by mutableStateOf("")
    private var cameraEnabled by mutableStateOf(true)
    private var serverEnabled by mutableStateOf(false)
    private var audioEnabled by mutableStateOf(false) // Flag for AudioService
    private var frameRate by mutableStateOf(30) // Default frame rate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CamAppTheme {
                Scaffold(
                    content = { padding ->
                        Box(modifier = Modifier.padding(padding)) {
                            CameraView(
                                serverStatus = serverStatus,
                                serverIp = serverIp,
                                cameraEnabled = cameraEnabled,
                                serverEnabled = serverEnabled,
                                audioEnabled = audioEnabled,
                                frameRate = frameRate,
                                onCameraToggle = { enabled ->
                                    cameraEnabled = enabled
                                    updateCameraState(enabled)
                                },
                                onServerToggle = { enabled ->
                                    serverEnabled = enabled
                                    updateServerState(enabled)
                                },
                                onAudioToggle = { enabled ->
                                    audioEnabled = enabled
                                    updateAudioState(enabled)
                                },
                                onFrameRateChange = { newFrameRate ->
                                    frameRate = newFrameRate
                                    updateFrameRate(newFrameRate)
                                }
                            )
                        }
                    }
                )
            }
        }

        requestPermissions()
    }

    private fun requestPermissions() {
        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (isGranted) {
                    if (serverEnabled) startServer()
                    if (cameraEnabled) startCameraService()
                    if (audioEnabled) startAudioService()
                } else {
                    // Handle permission denied case
                }
            }

        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                if (serverEnabled) startServer()
                if (cameraEnabled) startCameraService()
                if (audioEnabled) startAudioService()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startServer() {
        server = CameraHttpServer(this)
        try {
            server?.start()
            serverStatus = "Server started"
            serverIp = "http://${getLocalIpAddress()}:8080"
        } catch (e: Exception) {
            serverStatus = "Server failed to start: ${e.message}"
        }
    }

    private fun stopServer() {
        server?.stop()
        serverStatus = "Server stopped"
        serverIp = ""
        server = null
    }

    private fun startCameraService() {
        val serviceIntent = Intent(this, CameraService::class.java)
        serviceIntent.putExtra("frameRate", frameRate)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopCameraService() {
        val serviceIntent = Intent(this, CameraService::class.java)
        stopService(serviceIntent)
    }

    private fun startAudioService() {
        val serviceIntent = Intent(this, AudioService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopAudioService() {
        val serviceIntent = Intent(this, AudioService::class.java)
        stopService(serviceIntent)
    }

    private fun updateCameraState(enabled: Boolean) {
        if (enabled) {
            if (serverEnabled) startCameraService()
        } else {
            stopCameraService()
        }
    }

    private fun updateServerState(enabled: Boolean) {
        serverEnabled = enabled
        if (enabled && cameraEnabled) {
            startServer()
            startCameraService()
        } else {
            stopServer()
            stopCameraService()
        }
    }

    private fun updateAudioState(enabled: Boolean) {
        audioEnabled = enabled
        if (enabled) {
            startAudioService()
        } else {
            stopAudioService()
        }
    }

    private fun updateFrameRate(newFrameRate: Int) {
        val serviceIntent = Intent(this, CameraService::class.java)
        serviceIntent.putExtra("frameRate", newFrameRate)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        stopCameraService()
        stopAudioService()
    }
}

@Composable
fun CameraView(
    serverStatus: String,
    serverIp: String,
    cameraEnabled: Boolean,
    serverEnabled: Boolean,
    audioEnabled: Boolean, // Added audioEnabled state
    frameRate: Int,
    onCameraToggle: (Boolean) -> Unit,
    onServerToggle: (Boolean) -> Unit,
    onAudioToggle: (Boolean) -> Unit, // Added onAudioToggle callback
    onFrameRateChange: (Int) -> Unit
) {
    var sliderPosition by remember { mutableStateOf(frameRate.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = serverStatus)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = serverIp)
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = cameraEnabled,
                onCheckedChange = { onCameraToggle(it) },
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(text = "Camera Enabled")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = serverEnabled,
                onCheckedChange = { onServerToggle(it) },
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(text = "Server Enabled")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = audioEnabled,
                onCheckedChange = { onAudioToggle(it) },
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(text = "Audio Enabled")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Frame Rate: ${sliderPosition.toInt()} FPS")
        Slider(
            value = sliderPosition,
            onValueChange = {
                sliderPosition = it
                onFrameRateChange(it.toInt())
            },
            valueRange = 1f..60f,
            steps = 59 // Number of steps between min and max values
        )
    }
}

fun getLocalIpAddress(): String? {
    try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val iface = interfaces.nextElement()
            val addresses = iface.inetAddresses
            while (addresses.hasMoreElements()) {
                val addr = addresses.nextElement()
                if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                    return addr.hostAddress
                }
            }
        }
    } catch (ex: Exception) {
        ex.printStackTrace()
    }
    return null
}
