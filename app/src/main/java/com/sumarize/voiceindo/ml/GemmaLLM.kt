package com.sumarize.voiceindo.ml

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "GemmaLLM"

class GemmaLLM(private val context: Context, private val modelFile: File) {

    private var llm: LlmInference? = null

    fun initialize() {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(1024)
            .setMaxTopK(40)
            .setPreferredBackend(LlmInference.Backend.CPU)
            .build()
        llm = LlmInference.createFromOptions(context, options)
        Log.i(TAG, "Gemma 3 1B initialized")
    }

    // Non-streaming: generateResponse() properly decodes the output.
    // generateResponseAsync with ProgressListener returns raw tokenizer strings
    // (<unused>, <pad>, etc.) for some model variants — sync avoids this.
    fun summarizeStreaming(transcript: String): Flow<String> = flow {
        val llmInstance = llm ?: error("LLM not initialized — call initialize() first")
        val raw = withContext(Dispatchers.IO) {
            llmInstance.generateResponse(buildPrompt(transcript))
        }
        val clean = raw.filterSpecialTokens()
        Log.i(TAG, "Generated ${raw.length} chars, ${clean.length} after filter")
        if (clean.isNotBlank()) emit(clean)
    }

    fun chatStreaming(
        systemPrompt: String,
        history: List<com.sumarize.voiceindo.viewmodel.ChatMessage>
    ): Flow<String> = flow {
        val llmInstance = llm ?: error("LLM not initialized — call initialize() first")
        val raw = withContext(Dispatchers.IO) {
            llmInstance.generateResponse(buildChatPrompt(systemPrompt, history))
        }
        val clean = raw.filterSpecialTokens()
        Log.i(TAG, "Chat generated ${raw.length} chars, ${clean.length} after filter")
        if (clean.isNotBlank()) emit(clean)
    }

    private fun buildChatPrompt(
        systemPrompt: String,
        history: List<com.sumarize.voiceindo.viewmodel.ChatMessage>
    ): String {
        val filtered = history.filter { it.role == "user" || it.role == "assistant" }
        if (filtered.isEmpty()) return ""

        val sb = StringBuilder()
        // Turn pertama: system prompt + pesan user pertama
        sb.append("<start_of_turn>user\n")
        sb.append(systemPrompt)
        sb.append("\n\nPesan: ")
        sb.append(filtered.first().content)
        sb.append("\n<end_of_turn>\n")

        // Sisa pesan: alternating
        filtered.drop(1).forEach { msg ->
            sb.append("<start_of_turn>")
            sb.append(if (msg.role == "assistant") "model" else "user")
            sb.append("\n")
            sb.append(msg.content)
            sb.append("\n<end_of_turn>\n")
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun String.filterSpecialTokens(): String =
        replace(Regex("<[^>]{1,50}>"), "")
            .replace(Regex("\\[[^\\]]{1,30}\\]"), "")
            .replace(Regex("[ \t]{2,}"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

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
        llm?.close()
        llm = null
        Log.i(TAG, "Gemma LLM released")
    }
}

