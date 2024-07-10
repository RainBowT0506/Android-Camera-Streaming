package com.example.camapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class CameraHttpServer(private val context: Context) : NanoHTTPD(8080) {
    private val frameQueue: BlockingQueue<ByteArray> = LinkedBlockingQueue()

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
