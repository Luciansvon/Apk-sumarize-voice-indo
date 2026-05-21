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
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

private const val TAG = "ModelDownloader"

// sherpa-onnx Whisper base int8: encoder ~72MB + decoder ~3MB + tokens
private const val WHISPER_URL =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.int8.tar.bz2"

// Gemma 3 1B int4 LiteRT (.task) — public mirror via HuggingFace
private const val GEMMA_URL =
    "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task"

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
            val tmpFile = File(context.cacheDir, "whisper-base-int8.tar.bz2")
            downloadFile(WHISPER_URL, tmpFile) { progress ->
                emit(DownloadResult.Progress(progress))
            }
            emit(DownloadResult.Progress(DownloadProgress(1, 1, 1f)))
            extractTarBz2(tmpFile, File(context.filesDir, "models"))
            tmpFile.delete()
            emit(DownloadResult.Success)
        } catch (e: Exception) {
            Log.e(TAG, "Whisper download failed", e)
            emit(DownloadResult.Failure(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    fun downloadGemma(hfToken: String = ""): Flow<DownloadResult> = flow {
        try {
            gemmaModelFile.parentFile?.mkdirs()
            downloadFile(GEMMA_URL, gemmaModelFile, hfToken) { progress ->
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
        hfToken: String = "",
        onProgress: suspend (DownloadProgress) -> Unit
    ) {
        dest.parentFile?.mkdirs()
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            if (hfToken.isNotBlank()) setRequestProperty("Authorization", "Bearer $hfToken")
            connect()
        }

        val total = conn.contentLengthLong
        var downloaded = 0L

        conn.inputStream.use { input ->
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
        conn.disconnect()
    }

    private fun extractTarBz2(archive: File, destDir: File) {
        destDir.mkdirs()
        // Use Apache Commons Compress via bundled bzip2 support
        // Falls back to manual extraction if library not available
        try {
            val bzInput = org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(
                archive.inputStream().buffered()
            )
            TarArchiveInputStream(bzInput).use { tar ->
                var entry = tar.nextTarEntry
                while (entry != null) {
                    val outFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { tar.copyTo(it) }
                    }
                    entry = tar.nextTarEntry
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Extraction via Commons Compress failed, trying fallback", e)
            extractViaNativeTar(archive, destDir)
        }
    }

    private fun extractViaNativeTar(archive: File, destDir: File) {
        val process = ProcessBuilder("tar", "-xjf", archive.absolutePath, "-C", destDir.absolutePath)
            .redirectErrorStream(true)
            .start()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("tar extraction failed with code $exitCode")
        }
    }
}
