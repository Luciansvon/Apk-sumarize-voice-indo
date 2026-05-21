package com.sumarize.voiceindo.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sumarize.voiceindo.audio.AudioRecorder
import com.sumarize.voiceindo.data.db.AppDatabase
import com.sumarize.voiceindo.data.db.SummaryEntity
import com.sumarize.voiceindo.data.preferences.AppPreferences
import com.sumarize.voiceindo.data.repository.SummaryRepository
import com.sumarize.voiceindo.ml.GemmaLLM
import com.sumarize.voiceindo.ml.ModelDownloader
import com.sumarize.voiceindo.ml.OpenRouterClient
import com.sumarize.voiceindo.ml.SherpaOnnxSTT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ProcessingStep {
    IDLE, RECORDING, TRANSCRIBING, SUMMARIZING, DONE, ERROR
}

data class MainUiState(
    val step: ProcessingStep = ProcessingStep.IDLE,
    val liveTranscript: String = "",
    val transcript: String = "",
    val summary: String = "",
    val streamingSummary: String = "",
    val durationSeconds: Int = 0,
    val errorMessage: String? = null,
    val isModelsReady: Boolean = false,
    val recordingSeconds: Int = 0,
    val currentAmplitude: Float = 0f,
    val isOnlineMode: Boolean = false
)

class MainViewModel(
    private val context: Context,
    private val repository: SummaryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val recorder = AudioRecorder(context)
    private val downloader = ModelDownloader(context)
    private val prefs = AppPreferences(context)

    private var stt: SherpaOnnxSTT? = null
    private var llm: GemmaLLM? = null

    @Volatile private var isTranscribingChunk = false

    init {
        initModels()
        observePreferences()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            prefs.isOnlineMode.collect { online ->
                _state.update { cur ->
                    cur.copy(
                        isOnlineMode = online,
                        // Online mode tidak perlu model lokal sama sekali
                        isModelsReady = if (online) true else (stt?.isReady() == true)
                    )
                }
            }
        }
    }

    fun setOnlineMode(v: Boolean) {
        viewModelScope.launch { prefs.setOnlineMode(v) }
    }

    fun setApiKey(k: String) {
        viewModelScope.launch { prefs.setApiKey(k) }
    }

    fun setModel(m: String) {
        viewModelScope.launch { prefs.setModel(m) }
    }

    fun setSttModel(m: String) {
        viewModelScope.launch { prefs.setSttModel(m) }
    }

    suspend fun getApiKey(): String = prefs.apiKey.first()

    suspend fun getSelectedModel(): String = prefs.selectedModel.first()

    suspend fun getSelectedSttModel(): String = prefs.sttModel.first()

    fun isGemmaReady(): Boolean = downloader.isGemmaReady()

    private fun initModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val online = prefs.isOnlineMode.first()
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
                val ready = if (online) true else (stt?.isReady() == true)
                _state.update { it.copy(isModelsReady = ready) }
            } catch (e: Exception) {
                // Online mode tetap siap walau inisialisasi local model gagal
                _state.update {
                    it.copy(
                        isModelsReady = online,
                        errorMessage = if (!online) "Gagal inisialisasi model: ${e.message}" else null
                    )
                }
            }
        }
    }

    fun hasAudioPermission(): Boolean = recorder.hasPermission()

    fun stopRecording() {
        recorder.requestStop()
    }

    fun startRecordAndProcess() {
        if (!_state.value.isModelsReady) {
            _state.update { it.copy(errorMessage = "Model masih dimuat, tunggu sebentar...") }
            return
        }
        val current = _state.value.step
        if (current == ProcessingStep.RECORDING ||
            current == ProcessingStep.TRANSCRIBING ||
            current == ProcessingStep.SUMMARIZING) return

        viewModelScope.launch {
            val onlineMode = prefs.isOnlineMode.first()
            val apiKey = prefs.apiKey.first()
            val selectedModel = prefs.selectedModel.first()
            val sttModel = prefs.sttModel.first()

            isTranscribingChunk = false
            _state.update {
                it.copy(
                    step = ProcessingStep.RECORDING,
                    liveTranscript = "",
                    transcript = "",
                    summary = "",
                    streamingSummary = "",
                    errorMessage = null,
                    recordingSeconds = 0,
                    currentAmplitude = 0f
                )
            }

            val timerJob = launch {
                while (_state.value.step == ProcessingStep.RECORDING) {
                    delay(1000L)
                    if (_state.value.step == ProcessingStep.RECORDING) {
                        _state.update { it.copy(recordingSeconds = it.recordingSeconds + 1) }
                    }
                }
            }

            try {
                // Live preview hanya tersedia di offline mode (butuh Whisper lokal)
                val chunkHandler: ((FloatArray) -> Unit)? = if (!onlineMode && stt != null) {
                    { chunkSamples ->
                        if (!isTranscribingChunk) {
                            isTranscribingChunk = true
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    val text = stt?.transcribe(chunkSamples)?.plainText?.trim() ?: ""
                                    if (text.isNotBlank()) {
                                        _state.update { s ->
                                            val joined = if (s.liveTranscript.isBlank()) text
                                                         else "${s.liveTranscript} $text"
                                            s.copy(liveTranscript = joined)
                                        }
                                    }
                                } finally {
                                    isTranscribingChunk = false
                                }
                            }
                        }
                    }
                } else null

                val result = withContext(Dispatchers.IO) {
                    recorder.recordUntilSilence(
                        onChunkAvailable = chunkHandler,
                        onAmplitudeChange = { amp ->
                            _state.update { it.copy(currentAmplitude = amp) }
                        }
                    )
                }
                timerJob.cancel()
                _state.update { it.copy(currentAmplitude = 0f) }

                val durationSec = (result.durationMs / 1000).toInt()
                _state.update { it.copy(step = ProcessingStep.TRANSCRIBING, durationSeconds = durationSec) }

                // STT: online = API, offline = Whisper lokal
                val plainText: String
                val displayText: String

                if (onlineMode) {
                    if (apiKey.isBlank()) {
                        _state.update {
                            it.copy(
                                step = ProcessingStep.ERROR,
                                errorMessage = "Mode Online aktif tapi API key OpenRouter belum di-isi. Buka Pengaturan untuk konfigurasi."
                            )
                        }
                        return@launch
                    }
                    // Coba online STT; fallback ke Whisper lokal jika gagal
                    val transcribed = runCatching {
                        withContext(Dispatchers.IO) {
                            OpenRouterClient(apiKey, selectedModel).transcribeAudio(result.samples, sttModel)
                        }
                    }.getOrElse { e ->
                        val fallback = withContext(Dispatchers.IO) {
                            stt?.transcribe(result.samples)?.plainText
                        }
                        fallback ?: throw e   // lempar error asli jika lokal juga tidak ada
                    }
                    plainText = transcribed
                    displayText = transcribed
                } else {
                    val transcriptResult = withContext(Dispatchers.IO) {
                        stt?.transcribe(result.samples) ?: error("STT lokal belum siap")
                    }
                    plainText = transcriptResult.plainText
                    displayText = transcriptResult.timestampedText
                }

                if (plainText.isBlank()) {
                    _state.update {
                        it.copy(
                            step = ProcessingStep.ERROR,
                            errorMessage = "Tidak ada suara yang terdeteksi. Coba lagi."
                        )
                    }
                    return@launch
                }

                _state.update { it.copy(transcript = displayText, step = ProcessingStep.SUMMARIZING) }

                val sb = StringBuilder()

                if (onlineMode) {
                    withContext(Dispatchers.IO) {
                        OpenRouterClient(apiKey, selectedModel).summarizeStreaming(plainText) { chunk ->
                            sb.append(chunk)
                            _state.update { it.copy(streamingSummary = sb.toString()) }
                        }
                    }
                } else {
                    llm?.summarizeStreaming(plainText)?.collect { chunk ->
                        sb.append(chunk)
                        _state.update { it.copy(streamingSummary = sb.toString()) }
                    } ?: run {
                        _state.update {
                            it.copy(
                                step = ProcessingStep.ERROR,
                                errorMessage = "Model LLM lokal belum siap. Buka Pengaturan untuk pindah ke Mode Online atau download model."
                            )
                        }
                        return@launch
                    }
                }

                val finalSummary = sb.toString().trim()

                val title = generateTitle(plainText)
                val entity = SummaryEntity(
                    title = title,
                    transcript = displayText,
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
                timerJob.cancel()
                _state.update {
                    it.copy(
                        step = ProcessingStep.ERROR,
                        errorMessage = e.message ?: "Terjadi kesalahan",
                        currentAmplitude = 0f
                    )
                }
            }
        }
    }

    fun reset() {
        _state.update {
            it.copy(
                step = ProcessingStep.IDLE,
                liveTranscript = "",
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
