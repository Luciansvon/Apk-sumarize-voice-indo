package com.sumarize.voiceindo.ml

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

private const val TAG = "GemmaLLM"

class GemmaLLM(private val context: Context, private val modelFile: File) {

    private var llm: LlmInference? = null

    // Routes inference results to the active callbackFlow
    @Volatile private var resultSink: ((String?, Boolean) -> Unit)? = null

    fun initialize() {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(1024)
            .setMaxTopK(40)
            .setResultListener { partialResult, done ->
                resultSink?.invoke(partialResult, done)
            }
            .build()
        llm = LlmInference.createFromOptions(context, options)
        Log.i(TAG, "Gemma 3 1B initialized")
    }

    fun summarizeStreaming(transcript: String): Flow<String> = callbackFlow {
        val llmInstance = llm ?: error("LLM not initialized — call initialize() first")

        resultSink = { partial, done ->
            if (!partial.isNullOrEmpty()) trySend(partial)
            if (done) close()
        }

        llmInstance.generateResponseAsync(buildPrompt(transcript))
        awaitClose { resultSink = null }
    }

    suspend fun summarize(transcript: String): String {
        val llmInstance = llm ?: error("LLM not initialized — call initialize() first")
        return llmInstance.generateResponse(buildPrompt(transcript))
    }

    private fun buildPrompt(transcript: String): String {
        val trimmed = transcript.take(3000)
        return """<start_of_turn>user
Kamu adalah asisten ringkasan dalam bahasa Indonesia. Buat ringkasan singkat dan padat dari transkripsi suara berikut.

Format output:
**Ringkasan:** (1-2 kalimat inti)
**Poin Penting:**
- (poin 1)
- (poin 2)
- (poin 3, jika ada)

Transkripsi:
$trimmed
<end_of_turn>
<start_of_turn>model
"""
    }

    fun isReady(): Boolean = llm != null

    fun release() {
        resultSink = null
        llm?.close()
        llm = null
        Log.i(TAG, "Gemma LLM released")
    }
}
