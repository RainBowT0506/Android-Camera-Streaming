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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.camapp.ui.theme.CamAppTheme

class MainActivity : ComponentActivity() {
    private var serverStatus by mutableStateOf("Server not started")
    private var serverIp by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CamAppTheme {
                Scaffold(
                    content = { padding ->
                        Box(modifier = Modifier.padding(padding)) {
                            CameraView(serverStatus, serverIp)
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
                    startCameraService()
                } else {
                    // Handle permission denied case
                }
            }

        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCameraService()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCameraService() {
        val serviceIntent = Intent(this, CameraService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        serverStatus = "Server started"
        serverIp = "http://${getLocalIpAddress()}:8080"
    }

    override fun onDestroy() {
        super.onDestroy()
        val serviceIntent = Intent(this, CameraService::class.java)
        stopService(serviceIntent)
        serverStatus = "Server stopped"
    }
}

@Composable
fun CameraView(serverStatus: String, serverIp: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = serverStatus)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = serverIp)
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
