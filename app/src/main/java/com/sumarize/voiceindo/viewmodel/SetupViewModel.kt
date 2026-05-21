package com.sumarize.voiceindo.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sumarize.voiceindo.ml.DownloadResult
import com.sumarize.voiceindo.ml.ModelDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupState(
    val whisperReady: Boolean = false,
    val gemmaReady: Boolean = false,
    val whisperProgress: Float = 0f,
    val gemmaProgress: Float = 0f,
    val isDownloadingWhisper: Boolean = false,
    val isDownloadingGemma: Boolean = false,
    val error: String? = null
) {
    val isReady: Boolean get() = whisperReady && gemmaReady
    val isDownloading: Boolean get() = isDownloadingWhisper || isDownloadingGemma
}

class SetupViewModel(context: Context) : ViewModel() {

    private val downloader = ModelDownloader(context)
    private val _state = MutableStateFlow(
        SetupState(
            whisperReady = downloader.isWhisperReady(),
            gemmaReady = downloader.isGemmaReady()
        )
    )
    val state: StateFlow<SetupState> = _state.asStateFlow()

    fun downloadAll() {
        if (!_state.value.whisperReady) downloadWhisper()
        if (!_state.value.gemmaReady) downloadGemma()
    }

    private fun downloadWhisper() {
        viewModelScope.launch {
            _state.update { it.copy(isDownloadingWhisper = true, error = null) }
            downloader.downloadWhisper().collect { result ->
                when (result) {
                    is DownloadResult.Progress ->
                        _state.update { it.copy(whisperProgress = result.progress.fraction) }
                    is DownloadResult.Success ->
                        _state.update { it.copy(whisperReady = true, isDownloadingWhisper = false, whisperProgress = 1f) }
                    is DownloadResult.Failure ->
                        _state.update { it.copy(isDownloadingWhisper = false, error = "Whisper: ${result.message}") }
                }
            }
        }
    }

    private fun downloadGemma() {
        viewModelScope.launch {
            _state.update { it.copy(isDownloadingGemma = true, error = null) }
            downloader.downloadGemma().collect { result ->
                when (result) {
                    is DownloadResult.Progress ->
                        _state.update { it.copy(gemmaProgress = result.progress.fraction) }
                    is DownloadResult.Success ->
                        _state.update { it.copy(gemmaReady = true, isDownloadingGemma = false, gemmaProgress = 1f) }
                    is DownloadResult.Failure ->
                        _state.update { it.copy(isDownloadingGemma = false, error = "Gemma: ${result.message}") }
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

class SetupViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SetupViewModel(context.applicationContext) as T
    }
}
