package com.sumarize.voiceindo.ml

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "OpenRouterClient"
private const val API_URL = "https://openrouter.ai/api/v1/chat/completions"

class OpenRouterClient(
    private val apiKey: String,
    private val model: String
) {

    fun summarizeStreaming(transcript: String, onChunk: (String) -> Unit) {
        val prompt = buildPrompt(transcript)

        val requestBody = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }.toString()

        val url = URL(API_URL)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("HTTP-Referer", "https://sumarize-voice-indo.app")
            conn.setRequestProperty("X-Title", "Sumarize Voice Indo")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000

            conn.outputStream.use { os ->
                os.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = try {
                    conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                } catch (e: Exception) {
                    "Unknown error"
                }
                Log.e(TAG, "HTTP $responseCode: $errorBody")
                throw Exception("OpenRouter error $responseCode: ${parseErrorMessage(errorBody)}")
            }

            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line!!.trim()
                    if (trimmed.startsWith("data: ")) {
                        val data = trimmed.removePrefix("data: ").trim()
                        if (data == "[DONE]") break
                        try {
                            val json = JSONObject(data)
                            val choices = json.optJSONArray("choices") ?: continue
                            if (choices.length() == 0) continue
                            val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue
                            val content = delta.optString("content", "")
                            if (content.isNotEmpty()) {
                                onChunk(content)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse SSE line: $data", e)
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseErrorMessage(body: String): String {
        return try {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message") ?: body.take(200)
        } catch (e: Exception) {
            body.take(200)
        }
    }

    private fun buildPrompt(transcript: String): String {
        val trimmed = transcript.take(3000)
        return """Kamu adalah asisten ringkasan dalam bahasa Indonesia. Buat ringkasan singkat dan padat dari transkripsi suara berikut.

Format output:
**Ringkasan:** (1-2 kalimat inti)
**Poin Penting:**
- (poin 1)
- (poin 2)
- (poin 3, jika ada)

Transkripsi:
$trimmed"""
    }
}
