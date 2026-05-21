package com.sumarize.voiceindo.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

private const val TAG = "AudioRecorder"
const val SAMPLE_RATE = 16000
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
private const val FRAMES_PER_BUFFER = 1024

// VAD: stop after 2.5 seconds of silence
private const val SILENCE_TIMEOUT_MS = 2500L
private const val SILENCE_THRESHOLD = 0.008f
private const val MIN_RECORDING_MS = 1000L
private const val MAX_RECORDING_MS = 120_000L

data class AudioChunk(val samples: FloatArray, val rms: Float)
data class RecordingResult(val samples: FloatArray, val durationMs: Long)

class AudioRecorder(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun recordWithVad(): Flow<AudioChunk> = flow {
        val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufSize = maxOf(minBufSize, FRAMES_PER_BUFFER * 4)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufSize
        )

        recorder.startRecording()
        Log.i(TAG, "Recording started")

        try {
            val buffer = FloatArray(FRAMES_PER_BUFFER)
            while (coroutineContext.isActive) {
                val read = recorder.read(buffer, 0, FRAMES_PER_BUFFER, AudioRecord.READ_BLOCKING)
                if (read > 0) {
                    val chunk = buffer.copyOf(read)
                    val rms = calculateRms(chunk)
                    emit(AudioChunk(chunk, rms))
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
            Log.i(TAG, "Recording stopped")
        }
    }.flowOn(Dispatchers.IO)

    suspend fun recordUntilSilence(): RecordingResult {
        val allSamples = mutableListOf<Float>()
        val startMs = System.currentTimeMillis()
        var lastSpeechMs = startMs
        var silenceStart: Long? = null

        val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT,
            maxOf(minBufSize, FRAMES_PER_BUFFER * 4)
        )
        recorder.startRecording()

        try {
            val buffer = FloatArray(FRAMES_PER_BUFFER)
            while (true) {
                val read = recorder.read(buffer, 0, FRAMES_PER_BUFFER, AudioRecord.READ_BLOCKING)
                if (read <= 0) continue

                val chunk = buffer.copyOf(read)
                allSamples.addAll(chunk.toList())

                val elapsed = System.currentTimeMillis() - startMs
                if (elapsed > MAX_RECORDING_MS) break

                val rms = calculateRms(chunk)
                val now = System.currentTimeMillis()

                if (rms > SILENCE_THRESHOLD) {
                    lastSpeechMs = now
                    silenceStart = null
                } else {
                    if (silenceStart == null) silenceStart = now
                    val silenceDuration = now - (silenceStart ?: now)
                    if (silenceDuration > SILENCE_TIMEOUT_MS && elapsed > MIN_RECORDING_MS) {
                        break
                    }
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
        }

        val durationMs = System.currentTimeMillis() - startMs
        return RecordingResult(allSamples.toFloatArray(), durationMs)
    }

    private fun calculateRms(samples: FloatArray): Float {
        var sum = 0.0
        for (s in samples) sum += s * s
        return sqrt(sum / samples.size).toFloat()
    }
}
