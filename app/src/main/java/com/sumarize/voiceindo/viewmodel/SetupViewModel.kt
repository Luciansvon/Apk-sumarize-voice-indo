package com.sumarize.voiceindo.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sumarize.voiceindo.data.preferences.AppPreferences
import com.sumarize.voiceindo.ml.DownloadResult
import com.sumarize.voiceindo.ml.ModelDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupState(
    val whisperReady: Boolean = false,
    val gemmaReady: Boolean = false,
    val whisperProgress: Float = 0f,
    val gemmaProgress: Float = 0f,
    val isDownloadingWhisper: Boolean = false,
    val isDownloadingGemma: Boolean = false,
    val onlineMode: Boolean = false,
    val modeChosen: Boolean = false,
    val error: String? = null
) {
    // Online: langsung siap (tidak perlu download). Offline: butuh Whisper + Gemma.
    val isReady: Boolean get() = onlineMode || (whisperReady && gemmaReady)
    val isDownloading: Boolean get() = isDownloadingWhisper || isDownloadingGemma
}

class SetupViewModel(context: Context) : ViewModel() {

    private val downloader = ModelDownloader(context)
    private val prefs = AppPreferences(context)

    private val _state = MutableStateFlow(
        SetupState(
            whisperReady = downloader.isWhisperReady(),
            gemmaReady = downloader.isGemmaReady()
        )
    )
    val state: StateFlow<SetupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.isOnlineMode.collect { online ->
                _state.update { it.copy(onlineMode = online) }
            }
        }
        viewModelScope.launch {
            prefs.modeChosen.collect { chosen ->
                _state.update { it.copy(modeChosen = chosen) }
            }
        }
    }

    fun chooseOnlineMode() {
        // Update sinkron agar navigasi langsung melihat mode baru
        _state.update { it.copy(onlineMode = true, modeChosen = true) }
        viewModelScope.launch {
            prefs.setOnlineMode(true)
            prefs.setModeChosen(true)
        }
    }

    fun chooseOfflineMode() {
        _state.update { it.copy(onlineMode = false, modeChosen = true) }
        viewModelScope.launch {
            prefs.setOnlineMode(false)
            prefs.setModeChosen(true)
        }
    }

    fun downloadRequired() {
        if (!_state.value.whisperReady) downloadWhisper()
        // Gemma hanya didownload jika mode offline
        if (!_state.value.onlineMode && !_state.value.gemmaReady) downloadGemma()
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
