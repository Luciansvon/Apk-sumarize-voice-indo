package com.sumarize.voiceindo.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

private const val TAG = "AudioRecorder"
const val SAMPLE_RATE = 16000
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
private const val FRAMES_PER_BUFFER = 1024

private const val SILENCE_TIMEOUT_MS = 4000L
private const val MIN_SILENCE_THRESHOLD = 0.015f  // absolute minimum
private const val MIN_RECORDING_MS = 1500L
private const val MAX_RECORDING_MS = 120_000L
private const val NOISE_CALIBRATION_MS = 400L
private const val PREVIEW_CHUNK_MS = 5000L  // emit preview chunk every 5 s

data class RecordingResult(val samples: FloatArray, val durationMs: Long)

class AudioRecorder(private val context: Context) {

    @Volatile var stopRequested = false
        private set

    fun requestStop() { stopRequested = true }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun recordUntilSilence(
        onChunkAvailable: ((FloatArray) -> Unit)? = null,
        onAmplitudeChange: ((Float) -> Unit)? = null
    ): RecordingResult {
        stopRequested = false
        // Kumpulkan chunk FloatArray, bukan Float satu-satu — hindari boxing ~30MB untuk 120 detik
        val chunks = ArrayList<FloatArray>(2048)
        var calibCount = 0
        val startMs = System.currentTimeMillis()

        val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT,
            maxOf(minBufSize, FRAMES_PER_BUFFER * 4)
        )
        recorder.startRecording()

        try {
            val buffer = FloatArray(FRAMES_PER_BUFFER)

            // Phase 1: calibrate ambient noise for 400ms before user speaks
            val calibEnd = startMs + NOISE_CALIBRATION_MS
            while (System.currentTimeMillis() < calibEnd) {
                val n = recorder.read(buffer, 0, FRAMES_PER_BUFFER, AudioRecord.READ_BLOCKING)
                if (n > 0) {
                    chunks.add(buffer.copyOf(n))
                    calibCount++
                }
            }
            val noiseFloor = if (calibCount > 0) {
                calculateRms(flattenChunks(chunks.subList(0, calibCount)))
            } else 0f
            val threshold = maxOf(noiseFloor * 5f, MIN_SILENCE_THRESHOLD)
            Log.i(TAG, "Noise floor: $noiseFloor  threshold: $threshold")

            // Phase 2: main recording with VAD
            var silenceStart: Long? = null
            var previewStartIdx = chunks.size  // start after calibration data
            var lastPreviewMs = System.currentTimeMillis()

            while (coroutineContext.isActive && !stopRequested) {
                val read = recorder.read(buffer, 0, FRAMES_PER_BUFFER, AudioRecord.READ_BLOCKING)
                if (read <= 0) continue

                val chunk = buffer.copyOf(read)
                chunks.add(chunk)

                val elapsed = System.currentTimeMillis() - startMs
                if (elapsed > MAX_RECORDING_MS) break

                val rms = calculateRms(chunk)
                onAmplitudeChange?.invoke(rms)
                val now = System.currentTimeMillis()

                if (rms > threshold) {
                    silenceStart = null
                } else {
                    if (silenceStart == null) silenceStart = now
                    val silenceDuration = now - silenceStart!!
                    if (silenceDuration > SILENCE_TIMEOUT_MS && elapsed > MIN_RECORDING_MS) {
                        Log.i(TAG, "Silence detected after ${elapsed}ms, stopping")
                        break
                    }
                }

                // Emit audio chunk for live preview every PREVIEW_CHUNK_MS
                if (onChunkAvailable != null && (now - lastPreviewMs) >= PREVIEW_CHUNK_MS) {
                    val newChunks = chunks.subList(previewStartIdx, chunks.size).toList()
                    if (newChunks.isNotEmpty()) {
                        onChunkAvailable(flattenChunks(newChunks))
                        previewStartIdx = chunks.size
                    }
                    lastPreviewMs = now
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
        }

        val durationMs = System.currentTimeMillis() - startMs
        return RecordingResult(flattenChunks(chunks), durationMs)
    }

    private fun flattenChunks(chunks: List<FloatArray>): FloatArray {
        val total = chunks.sumOf { it.size }
        val out = FloatArray(total)
        var pos = 0
        for (c in chunks) { c.copyInto(out, pos); pos += c.size }
        return out
    }

    private fun calculateRms(samples: FloatArray): Float {
        var sum = 0.0
        for (s in samples) sum += s * s
        return sqrt(sum / samples.size).toFloat()
    }
}
