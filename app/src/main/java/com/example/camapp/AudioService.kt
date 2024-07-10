package com.example.camapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.io.ByteArrayOutputStream
import android.Manifest
import java.util.concurrent.Executors

class AudioService : LifecycleService() {
    private lateinit var audioRecorder: AudioRecord
    private lateinit var audioThread: Thread
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

        audioThread = Thread {
            try {
                audioRecorder.startRecording()
                val buffer = ByteArray(audioBufferSize)
                while (!audioThread.isInterrupted) {
                    val bytesRead = audioRecorder.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        audioDataBuffer.write(buffer, 0, bytesRead)
                        AudioDataHolder.setAudioData(audioDataBuffer.toByteArray())
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
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }
}
