package com.example.camapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors

class AudioService : LifecycleService() {
    private lateinit var mediaRecorder: MediaRecorder
    private lateinit var recordingFile: File
    private lateinit var audioThread: Thread
    private val audioDataBuffer = ByteArrayOutputStream()
    private val audioExecutor = Executors.newSingleThreadExecutor()
    private val channelId = "AudioServiceChannel"

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val AUDIO_ENCODING = MediaRecorder.OutputFormat.AAC_ADTS
        private const val AUDIO_CHANNEL = MediaRecorder.AudioEncoder.AAC
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
        recordingFile = File(filesDir, "audio_record.aac")

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(AUDIO_ENCODING)
            setAudioEncoder(AUDIO_CHANNEL)
            setAudioSamplingRate(SAMPLE_RATE)
            setAudioEncodingBitRate(AAC_BIT_RATE)
            setOutputFile(recordingFile.absolutePath)
            prepare()
            start()
        }

        audioThread = Thread {
            try {
                while (!audioThread.isInterrupted) {
                    val inputStream = FileInputStream(recordingFile)
                    val buffer = ByteArray(1024)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (bytesRead > 0) {
                            // Send encoded data to AudioDataHolder or other processing
                            AudioDataHolder.setAudioData(buffer.copyOf(bytesRead))
                        }
                    }
                    inputStream.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        audioThread.start()
    }

    private fun stopAudioRecording() {
        mediaRecorder.stop()
        mediaRecorder.release()
        audioThread.interrupt()
        recordingFile.delete()
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }
}
