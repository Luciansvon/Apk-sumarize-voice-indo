package com.sumarize.voiceindo.ml

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "OpenRouterClient"
private const val BASE_URL = "https://openrouter.ai/api/v1"

class OpenRouterClient(
    private val apiKey: String,
    private val model: String
) {

    fun transcribeAudio(samples: FloatArray, sttModel: String, sampleRate: Int = 16000): String {
        val wavBytes = samples.toWavPcm16(sampleRate)
        val boundary = "----FormBoundary${System.currentTimeMillis()}"
        val url = URL("$BASE_URL/audio/transcriptions")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setRequestProperty("HTTP-Referer", "https://sumarize-voice-indo.app")
            conn.setRequestProperty("X-Title", "Sumarize Voice Indo")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000

            conn.outputStream.use { os ->
                fun part(name: String, value: String) {
                    os.write("--$boundary\r\n".toByteArray())
                    os.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                    os.write("$value\r\n".toByteArray())
                }
                part("model", sttModel)
                part("language", "id")
                // audio file
                os.write("--$boundary\r\n".toByteArray())
                os.write("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n".toByteArray())
                os.write("Content-Type: audio/wav\r\n\r\n".toByteArray())
                os.write(wavBytes)
                os.write("\r\n".toByteArray())
                os.write("--$boundary--\r\n".toByteArray())
            }

            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val errBody = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown"
                Log.e(TAG, "STT HTTP $code: $errBody")
                throw Exception("STT error $code: ${parseErrorMessage(errBody)}")
            }

            val body = conn.inputStream.bufferedReader().readText()
            return JSONObject(body).optString("text", "").trim()
        } finally {
            conn.disconnect()
        }
    }

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

        val url = URL("$BASE_URL/chat/completions")
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

private fun FloatArray.toWavPcm16(sampleRate: Int): ByteArray {
    val dataSize = size * 2
    val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
    buf.put("RIFF".toByteArray())
    buf.putInt(36 + dataSize)
    buf.put("WAVE".toByteArray())
    buf.put("fmt ".toByteArray())
    buf.putInt(16)
    buf.putShort(1)           // PCM
    buf.putShort(1)           // mono
    buf.putInt(sampleRate)
    buf.putInt(sampleRate * 2)
    buf.putShort(2)           // block align
    buf.putShort(16)          // bits per sample
    buf.put("data".toByteArray())
    buf.putInt(dataSize)
    for (s in this) buf.putShort((s * 32767f).toInt().coerceIn(-32768, 32767).toShort())
    return buf.array()
}
