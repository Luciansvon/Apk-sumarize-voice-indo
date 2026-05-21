package com.sumarize.voiceindo.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sumarize.voiceindo.audio.AudioRecorder
import com.sumarize.voiceindo.data.db.AppDatabase
import com.sumarize.voiceindo.data.db.SummaryEntity
import com.sumarize.voiceindo.data.repository.SummaryRepository
import com.sumarize.voiceindo.ml.GemmaLLM
import com.sumarize.voiceindo.ml.ModelDownloader
import com.sumarize.voiceindo.ml.SherpaOnnxSTT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ProcessingStep {
    IDLE, RECORDING, TRANSCRIBING, SUMMARIZING, DONE, ERROR
}

data class MainUiState(
    val step: ProcessingStep = ProcessingStep.IDLE,
    val transcript: String = "",
    val summary: String = "",
    val streamingSummary: String = "",
    val durationSeconds: Int = 0,
    val errorMessage: String? = null,
    val isModelsReady: Boolean = false
)

class MainViewModel(
    private val context: Context,
    private val repository: SummaryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val recorder = AudioRecorder(context)
    private val downloader = ModelDownloader(context)

    private var stt: SherpaOnnxSTT? = null
    private var llm: GemmaLLM? = null

    init {
        initModels()
    }

    private fun initModels() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (downloader.isWhisperReady()) {
                    val sttInstance = SherpaOnnxSTT(downloader.whisperModelDir)
                    sttInstance.initialize()
                    stt = sttInstance
                }
                if (downloader.isGemmaReady()) {
                    val llmInstance = GemmaLLM(context, downloader.gemmaModelFile)
                    llmInstance.initialize()
                    llm = llmInstance
                }
                val ready = stt?.isReady() == true && llm?.isReady() == true
                _state.update { it.copy(isModelsReady = ready) }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Gagal inisialisasi model: ${e.message}") }
            }
        }
    }

    fun hasAudioPermission(): Boolean = recorder.hasPermission()

    fun stopRecording() {
        recorder.requestStop()  // signal loop to exit; coroutine continues to transcribe
    }

    fun startRecordAndProcess() {
        val current = _state.value.step
        if (current == ProcessingStep.RECORDING ||
            current == ProcessingStep.TRANSCRIBING ||
            current == ProcessingStep.SUMMARIZING) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    step = ProcessingStep.RECORDING,
                    transcript = "",
                    summary = "",
                    streamingSummary = "",
                    errorMessage = null
                )
            }
            try {
                // Record audio
                val result = withContext(Dispatchers.IO) { recorder.recordUntilSilence() }
                val durationSec = (result.durationMs / 1000).toInt()

                // Transcribe
                _state.update { it.copy(step = ProcessingStep.TRANSCRIBING, durationSeconds = durationSec) }
                val transcript = withContext(Dispatchers.IO) {
                    stt?.transcribe(result.samples)
                        ?: error("STT belum siap")
                }

                if (transcript.isBlank()) {
                    _state.update {
                        it.copy(
                            step = ProcessingStep.ERROR,
                            errorMessage = "Tidak ada suara yang terdeteksi. Coba lagi."
                        )
                    }
                    return@launch
                }

                _state.update { it.copy(transcript = transcript, step = ProcessingStep.SUMMARIZING) }

                // Summarize with streaming
                val sb = StringBuilder()
                llm?.summarizeStreaming(transcript)?.collect { chunk ->
                    sb.append(chunk)
                    _state.update { it.copy(streamingSummary = sb.toString()) }
                } ?: run {
                    _state.update { it.copy(step = ProcessingStep.ERROR, errorMessage = "LLM belum siap") }
                    return@launch
                }

                val finalSummary = sb.toString().trim()

                // Save to DB
                val title = generateTitle(transcript)
                val entity = SummaryEntity(
                    title = title,
                    transcript = transcript,
                    summary = finalSummary,
                    durationSeconds = durationSec
                )
                repository.save(entity)

                _state.update {
                    it.copy(
                        step = ProcessingStep.DONE,
                        summary = finalSummary,
                        streamingSummary = ""
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        step = ProcessingStep.ERROR,
                        errorMessage = e.message ?: "Terjadi kesalahan"
                    )
                }
            }
        }
    }

    fun reset() {
        _state.update {
            it.copy(
                step = ProcessingStep.IDLE,
                transcript = "",
                summary = "",
                streamingSummary = "",
                errorMessage = null
            )
        }
    }

    private fun generateTitle(transcript: String): String {
        val words = transcript.trim().split(" ").take(6)
        return words.joinToString(" ").let {
            if (it.length > 40) it.take(37) + "..." else it
        }
    }

    override fun onCleared() {
        super.onCleared()
        stt?.release()
        llm?.release()
    }
}

class MainViewModelFactory(
    private val context: Context,
    private val database: AppDatabase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MainViewModel(
            context.applicationContext,
            SummaryRepository(database.summaryDao())
        ) as T
    }
}
