package com.example.camapp

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.InputStream
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
            justify-content: center;
            align-items: center;
            height: 100vh;
            margin: 0;
        }
        img {
            width: auto;
            height: 100%;
        }
    </style>
</head>
<body>
    <img src="/stream" />
    <audio id="audioPlayer" controls autoplay></audio>
    <script>
        var audioPlayer = document.getElementById('audioPlayer');
        var audioSource = new EventSource('/audio');

        audioSource.onmessage = function(event) {
            var blob = new Blob([event.data], { type: 'audio/ogg; codecs=opus' });
            var url = URL.createObjectURL(blob);
            audioPlayer.src = url;
        };
    </script>
</body>
</html>
""".trimIndent()

class CameraHttpServer(private val context: Context) : NanoHTTPD(8080) {
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

    override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
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
                headers["Content-Type"] = "audio/ogg; codecs=opus"
                val audioData = AudioDataHolder.getAudioData()
                if (audioData != null) {
                    newChunkedResponse(Response.Status.OK, "audio/ogg; codecs=opus", ByteArrayInputStream(audioData)).apply {
                        headers.forEach { (key, value) ->
                            addHeader(key, value)
                        }
                    }
                } else {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "No audio data available")
                }
            }
            else -> {
                newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found")
            }
        }
    }
}
