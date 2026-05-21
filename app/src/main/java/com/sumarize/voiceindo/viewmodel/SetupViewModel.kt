package com.sumarize.voiceindo.viewmodel

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sumarize.voiceindo.ml.DownloadResult
import com.sumarize.voiceindo.ml.ModelDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "settings")
private val HF_TOKEN_KEY = stringPreferencesKey("hf_token")

data class SetupState(
    val whisperReady: Boolean = false,
    val gemmaReady: Boolean = false,
    val whisperProgress: Float = 0f,
    val gemmaProgress: Float = 0f,
    val isDownloadingWhisper: Boolean = false,
    val isDownloadingGemma: Boolean = false,
    val error: String? = null,
    val hfToken: String = ""
) {
    val isReady: Boolean get() = whisperReady && gemmaReady
    val isDownloading: Boolean get() = isDownloadingWhisper || isDownloadingGemma
}

class SetupViewModel(private val context: Context) : ViewModel() {

    private val downloader = ModelDownloader(context)
    private val _state = MutableStateFlow(
        SetupState(
            whisperReady = downloader.isWhisperReady(),
            gemmaReady = downloader.isGemmaReady()
        )
    )
    val state: StateFlow<SetupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = context.dataStore.data.first()
            val saved = prefs[HF_TOKEN_KEY] ?: ""
            if (saved.isNotBlank()) _state.update { it.copy(hfToken = saved) }
        }
    }

    fun updateHfToken(token: String) {
        _state.update { it.copy(hfToken = token) }
        viewModelScope.launch {
            context.dataStore.edit { it[HF_TOKEN_KEY] = token }
        }
    }

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
            downloader.downloadGemma(_state.value.hfToken).collect { result ->
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
