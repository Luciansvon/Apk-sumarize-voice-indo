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

    fun transcribe(samples: FloatArray): String {
        val rec = recognizer ?: error("STT not initialized — call initialize() first")
        val stream = rec.createStream()
        stream.acceptWaveform(samples = samples, sampleRate = SAMPLE_RATE)
        rec.decode(stream = stream)
        val result = rec.getResult(stream = stream)
        stream.release()
        return result.text.trim()
    }

    fun isReady(): Boolean = recognizer != null

    fun release() {
        recognizer?.release()
        recognizer = null
        Log.i(TAG, "STT released")
    }
}
