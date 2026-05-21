package com.sumarize.voiceindo.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "ModelDownloader"

// Whisper base int8 — HuggingFace public repo (3 files, no archive extraction needed)
private const val HF_WHISPER_BASE = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/main"
private val WHISPER_FILES = listOf(
    "base-encoder.int8.onnx" to "encoder.int8.onnx",   // ~29 MB
    "base-decoder.int8.onnx" to "decoder.int8.onnx",   // ~131 MB
    "base-tokens.txt"        to "tokens.txt"            // ~1 MB
)
private const val WHISPER_TOTAL_BYTES = 161_000_000L   // approximate for progress

// Gemma 3 1B int4 LiteRT (.task) — public mirror (no token required)
private const val GEMMA_URL =
    "https://huggingface.co/AfiOne/gemma3-1b-it-int4.task/resolve/main/gemma3-1b-it-int4.task"

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val fraction: Float = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
)

sealed class DownloadResult {
    data object Success : DownloadResult()
    data class Failure(val message: String) : DownloadResult()
    data class Progress(val progress: DownloadProgress) : DownloadResult()
}

class ModelDownloader(private val context: Context) {

    val whisperModelDir: File
        get() = File(context.filesDir, "models/sherpa-onnx-whisper-base.int8")

    val gemmaModelFile: File
        get() = File(context.filesDir, "models/gemma3-1b-it-int4.task")

    fun isWhisperReady(): Boolean =
        File(whisperModelDir, "encoder.int8.onnx").exists() &&
        File(whisperModelDir, "decoder.int8.onnx").exists() &&
        File(whisperModelDir, "tokens.txt").exists()

    fun isGemmaReady(): Boolean = gemmaModelFile.exists() && gemmaModelFile.length() > 100_000_000L

    fun downloadWhisper(): Flow<DownloadResult> = flow {
        try {
            whisperModelDir.mkdirs()
            var totalDownloaded = 0L
            for ((srcName, destName) in WHISPER_FILES) {
                val destFile = File(whisperModelDir, destName)
                val url = "$HF_WHISPER_BASE/$srcName"
                downloadFile(url, destFile) { progress ->
                    val combined = totalDownloaded + progress.bytesDownloaded
                    emit(DownloadResult.Progress(DownloadProgress(combined, WHISPER_TOTAL_BYTES)))
                }
                totalDownloaded += destFile.length()
            }
            emit(DownloadResult.Success)
        } catch (e: Exception) {
            Log.e(TAG, "Whisper download failed", e)
            emit(DownloadResult.Failure(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    fun downloadGemma(): Flow<DownloadResult> = flow {
        try {
            gemmaModelFile.parentFile?.mkdirs()
            downloadFile(GEMMA_URL, gemmaModelFile) { progress ->
                emit(DownloadResult.Progress(progress))
            }
            emit(DownloadResult.Success)
        } catch (e: Exception) {
            Log.e(TAG, "Gemma download failed", e)
            emit(DownloadResult.Failure(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun downloadFile(
        urlString: String,
        dest: File,
        onProgress: suspend (DownloadProgress) -> Unit
    ) {
        dest.parentFile?.mkdirs()

        // Follow redirects manually — HuggingFace and GitHub redirect to CDN across domains
        var currentUrl = urlString
        var conn: HttpURLConnection? = null
        repeat(10) {
            val c = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 120_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connect()
            }
            val code = c.responseCode
            if (code in 301..308) {
                val location = c.getHeaderField("Location")
                c.disconnect()
                if (location.isNullOrBlank()) throw java.io.IOException("Redirect tanpa Location header")
                currentUrl = location
                conn = null
            } else {
                if (code != 200) {
                    c.disconnect()
                    throw java.io.IOException("HTTP $code saat download")
                }
                conn = c
                return@repeat
            }
        }

        val finalConn = conn ?: throw java.io.IOException("Terlalu banyak redirect")
        val total = finalConn.contentLengthLong
        var downloaded = 0L

        finalConn.inputStream.use { input ->
            FileOutputStream(dest).use { output ->
                val buf = ByteArray(128 * 1024)
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    output.write(buf, 0, n)
                    downloaded += n
                    onProgress(DownloadProgress(downloaded, total))
                }
            }
        }
        finalConn.disconnect()
    }
}
