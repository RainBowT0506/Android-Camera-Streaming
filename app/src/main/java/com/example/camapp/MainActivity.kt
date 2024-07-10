package com.example.camapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.camapp.ui.theme.CamAppTheme
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private var server: CameraHttpServer? = null
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
                    startCamera()
                } else {
                    Log.e("MainActivity", "Camera permission not granted")
                }
            }
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        server = CameraHttpServer(this)
        try {
            server?.start()
            serverStatus = "Server started"
            serverIp = "http://${getLocalIpAddress()}:8080"
        } catch (e: Exception) {
            serverStatus = "Server failed to start: ${e.message}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        serverStatus = "Server stopped"
    }
}

@Composable
fun CameraView(serverStatus: String, serverIp: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { mutableStateOf<ImageCapture?>(null) }

    LaunchedEffect(cameraProviderFuture) {
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        val imageAnalysis = ImageAnalysis.Builder().build().also {
            it.setAnalyzer(Executors.newSingleThreadExecutor(), ImageAnalyzer())
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (exc: Exception) {
            Log.e("CameraView", "Use case binding failed", exc)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView }
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Text(text = serverStatus)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = serverIp)
        }
    }
}

class CameraHttpServer(private val context: Context) : NanoHTTPD(8080) {
    private val frameQueue: BlockingQueue<ByteArray> = LinkedBlockingQueue<ByteArray>()

    init {
        Executors.newSingleThreadExecutor().execute {
            while (true) {
                val frame = CameraFrameHolder.frame
                if (frame != null) {
                    frameQueue.clear()
                    frameQueue.offer(frame)
                }
                Thread.sleep(33) // Approx. 30 frames per second
            }
        }
    }

    override fun serve(session: IHTTPSession?): Response {
        val boundary = "frame"
        val headers = mutableMapOf<String, String>()
        headers["Content-Type"] = "multipart/x-mixed-replace; boundary=$boundary"
        return newChunkedResponse(Response.Status.OK, "multipart/x-mixed-replace; boundary=$boundary", MJpegInputStream(frameQueue, boundary)).apply {
            headers.forEach { (key, value) ->
                addHeader(key, value)
            }
        }
    }
}

class MJpegInputStream(private val frameQueue: BlockingQueue<ByteArray>, private val boundary: String) : InputStream() {
    private var currentStream: InputStream? = null

    override fun read(): Int {
        if (currentStream == null || currentStream!!.available() <= 0) {
            val frame = frameQueue.poll(100, TimeUnit.MILLISECONDS) ?: return -1
            val header = "--$boundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n"
            val outputStream = ByteArrayOutputStream()
            outputStream.write(header.toByteArray())
            outputStream.write(frame)
            outputStream.write("\r\n".toByteArray())
            currentStream = ByteArrayInputStream(outputStream.toByteArray())
        }
        return currentStream!!.read()
    }
}

object CameraFrameHolder {
    var frame: ByteArray? = null
}

class ImageAnalyzer : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        val bitmap = image.toBitmap()
        val jpegData = bitmap.toByteArray()
        CameraFrameHolder.frame = jpegData
        image.close()
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun Bitmap.toByteArray(): ByteArray {
        val stream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        return stream.toByteArray()
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
