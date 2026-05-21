package com.sumarize.voiceindo.ml

import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File

private const val TAG = "SherpaOnnxSTT"
private const val SAMPLE_RATE = 16000

data class TranscriptResult(val plainText: String, val timestampedText: String)

class SherpaOnnxSTT(private val modelDir: File) {

    private var recognizer: OfflineRecognizer? = null

    fun initialize() {
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = File(modelDir, "encoder.int8.onnx").absolutePath,
                    decoder = File(modelDir, "decoder.int8.onnx").absolutePath,
                    language = "id",
                    task = "transcribe",
                    tailPaddings = 0
                ),
                tokens = File(modelDir, "tokens.txt").absolutePath,
                numThreads = 4,
                provider = "cpu",
                debug = false,
                modelType = "whisper"
            )
        )
        recognizer = OfflineRecognizer(config = config)
        Log.i(TAG, "STT initialized with Whisper base int8")
    }

    fun transcribe(samples: FloatArray): TranscriptResult {
        val rec = recognizer ?: error("STT not initialized — call initialize() first")
        val stream = rec.createStream()
        stream.acceptWaveform(samples = samples, sampleRate = SAMPLE_RATE)
        rec.decode(stream = stream)
        val result = rec.getResult(stream = stream)
        stream.release()

        val plainText = result.text.trim()
        val tokens = result.tokens
        val timestamps = result.timestamps

        val timestampedText = if (
            tokens != null && tokens.isNotEmpty() &&
            timestamps != null && timestamps.size >= tokens.size &&
            timestamps.any { it > 0f }
        ) {
            buildTimestampedText(tokens, timestamps).ifBlank { plainText }
        } else {
            plainText
        }

        return TranscriptResult(plainText, timestampedText)
    }

    private fun buildTimestampedText(tokens: Array<String>, timestamps: FloatArray): String {
        class Segment(val startTime: Float, val text: StringBuilder = StringBuilder())

        val segments = mutableListOf<Segment>()
        var current: Segment? = null
        var lastTime = timestamps[0]

        for (i in tokens.indices) {
            val tok = tokens[i]
            if (tok.startsWith("<|") && tok.endsWith("|>")) continue

            val t = if (i < timestamps.size) timestamps[i] else lastTime
            val gap = t - lastTime
            val segDur = if (current != null) t - current.startTime else 0f

            if (current == null) {
                current = Segment(t)
            } else if ((gap > 1.0f || segDur > 8.0f) && current.text.isNotBlank()) {
                segments += current
                current = Segment(t)
            }

            // ▁ is SentencePiece word boundary; also handle raw space prefixes
            current!!.text.append(tok.replace("▁", " "))
            lastTime = t
        }

        current?.let { if (it.text.isNotBlank()) segments += it }

        return segments.joinToString("\n") { seg ->
            val mins = (seg.startTime / 60).toInt()
            val secs = (seg.startTime % 60).toInt()
            "[%d:%02d] %s".format(mins, secs, seg.text.toString().trim())
        }
    }

    fun isReady(): Boolean = recognizer != null

    fun release() {
        recognizer?.release()
        recognizer = null
        Log.i(TAG, "STT released")
    }
}
