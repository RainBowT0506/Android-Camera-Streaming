package com.example.camapp

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

class CameraHttpServer(private val context: Context, private var frameRate: Int) : NanoHTTPD(8080) {
    private val frameQueue: BlockingQueue<ByteArray> = LinkedBlockingQueue()

    init {
        startFrameProducer()
    }

    private fun startFrameProducer() {
        Executors.newSingleThreadExecutor().execute {
            while (true) {
                val frame = CameraFrameHolder.frame
                if (frame != null) {
                    frameQueue.clear()
                    frameQueue.offer(frame)
                }
                Thread.sleep((1000 / frameRate).toLong())
            }
        }
    }

    fun setFrameRate(newFrameRate: Int) {
        frameRate = newFrameRate
        startFrameProducer()
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
