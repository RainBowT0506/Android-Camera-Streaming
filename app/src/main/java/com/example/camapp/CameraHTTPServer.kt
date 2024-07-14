package com.example.camapp

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

private val INDEX_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Camera Stream</title>
    <style>
        body {
            display: flex;
            flex-direction: column;
            justify-content: center;
            align-items: center;
            height: 100vh;
            margin: 0;
        }
        img {
            width: auto;
            height: 70%;
        }
        input[type="range"] {
            width: 80%;
        }
    </style>
</head>
<body>
    <img src="/stream" />
    <input type="range" min="0" max="100" value="0" id="zoomSlider">
    <script>
        var zoomSlider = document.getElementById('zoomSlider');
        zoomSlider.oninput = function() {
            fetch(`/setZoom?zoomLevel=${"$"}{this.value / 100}`).then(response => {
                console.log(response.text());
            }).catch(error => {
                console.error('Error:', error);
            });
        }
    </script>
</body>
</html>
""".trimIndent()

class CameraHttpServer(private val context: Context) : NanoHTTPD(8080) {
    var cameraService: CameraService? = null

    @Volatile
    private var frameRate: Int = 30 // Default frame rate
    private val frameQueue: BlockingQueue<ByteArray> = LinkedBlockingQueue()

    private var frameProducerThread: Thread? = null

    init {
        startFrameProducer()
    }

    private fun startFrameProducer() {
        frameProducerThread = Thread {
            while (!Thread.currentThread().isInterrupted) {
                val frame = CameraFrameHolder.frame
                if (frame != null) {
                    frameQueue.clear()
                    frameQueue.offer(frame)
                }
                try {
                    Thread.sleep((1000 / frameRate).toLong())
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        frameProducerThread?.start()
    }

    fun setFrameRate(newFrameRate: Int) {
        frameRate = newFrameRate
        // Restart frame producer thread with updated frame rate
        stopFrameProducer()
        startFrameProducer()
    }

    private fun stopFrameProducer() {
        frameProducerThread?.interrupt()
        frameProducerThread = null
    }

    private fun setZoom(zoomLevelStr: String?): NanoHTTPD.Response{
        if (zoomLevelStr != null) {
            try {
                val zoomLevel = zoomLevelStr.toFloat()
                if (zoomLevel in 0.0f..1.0f) {
                    // Set zoom level in CameraService
                    cameraService?.setZoom(zoomLevel)
                    return newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "Zoom level set to $zoomLevel")
                } else {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Invalid zoom level")
                }
            } catch (e: NumberFormatException) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Invalid zoom level format")
            }
        } else {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Missing zoom level parameter")
        }
    }

    private fun setFps(fpsStr: String?): NanoHTTPD.Response {
        if (fpsStr != null) {
            try {
                val fps = fpsStr.toInt()
                if (fps in 5..30) {
                    setFrameRate(fps)
                    return newFixedLengthResponse(
                        Response.Status.OK,
                        NanoHTTPD.MIME_PLAINTEXT,
                        "Frame rate set to $fps FPS"
                    )
                } else {
                    return newFixedLengthResponse(
                        Response.Status.BAD_REQUEST,
                        NanoHTTPD.MIME_PLAINTEXT,
                        "Invalid frame rate: must be between 1 and 30"
                    )
                }
            } catch (e: NumberFormatException) {
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    NanoHTTPD.MIME_PLAINTEXT,
                    "Invalid frame rate format"
                )
            }
        } else {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                NanoHTTPD.MIME_PLAINTEXT,
                "Missing frame rate parameter"
            )
        }
    }


    override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        Log.d("CameraHttpServer", "Request received: ${session.uri}")
        return when (session.uri) {
            "/" -> {
                newFixedLengthResponse(Response.Status.OK, "text/html", INDEX_HTML)
            }
            "/stream" -> {
                val boundary = "frame"
                val headers = mutableMapOf<String, String>()
                headers["Content-Type"] = "multipart/x-mixed-replace; boundary=$boundary"
                newChunkedResponse(Response.Status.OK, "multipart/x-mixed-replace; boundary=$boundary", MJpegInputStream(frameQueue, boundary)).apply {
                    headers.forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }
            }
            "/audio" -> {
                val headers = mutableMapOf<String, String>()
                headers["Content-Type"] = "audio/aac"
                val audioData = AudioDataHolder.getAudioData()
                if (audioData != null) {
                    val inputStream = ByteArrayInputStream(audioData)
                    newChunkedResponse(Response.Status.OK, "audio/aac", inputStream).apply {
                        headers.forEach { (key, value) ->
                            addHeader(key, value)
                        }
                    }
                } else {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "No audio data available")
                }
            }
            "/setZoom" -> {
                val zoomLevelStr = session.parameters["zoomLevel"]?.firstOrNull()
                setZoom(zoomLevelStr)
            }
            "/setFPS" -> {
                val fpsStr = session.parameters["fps"]?.firstOrNull()
                setFps(fpsStr)
            }
            else -> {
                newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found")
            }
        }
    }
}
