package com.example.camapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.io.ByteArrayOutputStream
import android.Manifest
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class AudioService : LifecycleService() {
    private lateinit var audioRecorder: AudioRecord
    private lateinit var audioThread: Thread
    private lateinit var aacEncoder: MediaCodec
    private val audioBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private val audioDataBuffer = ByteArrayOutputStream()
    private val audioExecutor = Executors.newSingleThreadExecutor()
    private val channelId = "AudioServiceChannel"

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val AAC_MIME_TYPE = "audio/mp4a-latm"
        private const val AAC_BIT_RATE = 64000 // Adjust as needed
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        startAudioRecording()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudioRecording()
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Audio Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Audio Service")
            .setContentText("Running...")
            .setSmallIcon(R.drawable.ic_microphone)
            .build()

        startForeground(1, notification)
    }

    private fun startAudioRecording() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Handle permission denied case, throw an exception or log an error
            return
        }

        audioRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AUDIO_CHANNEL,
            AUDIO_ENCODING,
            audioBufferSize
        )

        // Initialize AAC encoder
        initAACEncoder()

        audioThread = Thread {
            try {
                audioRecorder.startRecording()
                val buffer = ByteArray(audioBufferSize)
                while (!audioThread.isInterrupted) {
                    val bytesRead = audioRecorder.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        // Encode audio using AAC
                        encodeAAC(buffer, bytesRead)
                    }
                }
            } catch (e: IllegalStateException) {
                // Handle IllegalStateException (e.g., if audioRecorder is not initialized properly)
                e.printStackTrace()
            }
        }

        audioThread.start()
    }

    private fun stopAudioRecording() {
        audioThread.interrupt()
        audioRecorder.stop()
        audioRecorder.release()
        audioDataBuffer.close()

        // Release AAC encoder
        releaseAACEncoder()
    }

    private fun initAACEncoder() {
        try {
            aacEncoder = MediaCodec.createEncoderByType(AAC_MIME_TYPE)
            val format = MediaFormat.createAudioFormat(AAC_MIME_TYPE, SAMPLE_RATE, 1)
            format.setInteger(MediaFormat.KEY_BIT_RATE, AAC_BIT_RATE)
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, audioBufferSize)
            aacEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            aacEncoder.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun encodeAAC(buffer: ByteArray, bytesRead: Int) {
        try {
            val inputBufferIndex = aacEncoder.dequeueInputBuffer(-1)
            if (inputBufferIndex >= 0) {
                val inputBuffer = aacEncoder.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                inputBuffer?.put(buffer, 0, bytesRead)
                aacEncoder.queueInputBuffer(inputBufferIndex, 0, bytesRead, 0, 0)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var outputBufferIndex = aacEncoder.dequeueOutputBuffer(bufferInfo, 0)
            while (outputBufferIndex >= 0) {
                val outputBuffer = aacEncoder.getOutputBuffer(outputBufferIndex)
                outputBuffer?.position(bufferInfo.offset)
                outputBuffer?.limit(bufferInfo.offset + bufferInfo.size)
                val encodedData = ByteArray(bufferInfo.size)
                outputBuffer?.get(encodedData)

                // Send encoded data to AudioDataHolder or other processing
                AudioDataHolder.setAudioData(encodedData)

                aacEncoder.releaseOutputBuffer(outputBufferIndex, false)
                outputBufferIndex = aacEncoder.dequeueOutputBuffer(bufferInfo, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseAACEncoder() {
        try {
            aacEncoder.stop()
            aacEncoder.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }
}
