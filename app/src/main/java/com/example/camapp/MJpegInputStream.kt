package com.example.camapp

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

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