package com.example.camapp

object AudioDataHolder {
    @Volatile
    private var audioData: ByteArray? = null

    fun getAudioData(): ByteArray? {
        return audioData
    }

    fun setAudioData(data: ByteArray) {
        audioData = data
    }
}
