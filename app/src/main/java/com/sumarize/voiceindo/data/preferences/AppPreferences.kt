package com.sumarize.voiceindo.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("app_settings")

class AppPreferences(private val context: Context) {
    companion object {
        private val KEY_ONLINE_MODE = booleanPreferencesKey("online_mode")
        private val KEY_API_KEY = stringPreferencesKey("openrouter_api_key")
        private val KEY_MODEL = stringPreferencesKey("openrouter_model")
        private val KEY_MODE_CHOSEN = booleanPreferencesKey("mode_chosen")
        private val KEY_STT_MODEL = stringPreferencesKey("stt_model")

        data class ModelOption(val id: String, val label: String)
        val AVAILABLE_MODELS = listOf(
            // Gratis (free tier OpenRouter — bisa kena rate limit)
            ModelOption("deepseek/deepseek-r1:free",               "DeepSeek R1 (Gratis)"),
            ModelOption("meta-llama/llama-3.3-70b-instruct:free",  "Llama 3.3 70B (Gratis)"),
            ModelOption("mistralai/mistral-7b-instruct:free",      "Mistral 7B (Gratis)"),
            // Berbayar — murah & stabil (butuh kredit OpenRouter)
            ModelOption("microsoft/phi-4",                         "Phi 4 — Microsoft"),
            ModelOption("openai/gpt-oss-120b",                     "GPT-OSS 120B — OpenAI"),
            ModelOption("z-ai/glm-4.5-air",                        "GLM 4.5 Air — Z.AI"),
            ModelOption("alibaba/tongyi-deepresearch-30b-a3b",     "Tongyi DeepResearch 30B"),
            ModelOption("qwen/qwen-plus-0728",                     "Qwen Plus 0728"),
            ModelOption("openai/gpt-3.5-turbo",                    "GPT-3.5 Turbo"),
            ModelOption("mistralai/mistral-large-2411",            "Mistral Large 2411"),
            ModelOption("anthropic/claude-opus-4",                 "Claude Opus 4 (Premium)"),
        )
        val AVAILABLE_STT_MODELS = listOf(
            ModelOption("openai/whisper-large-v3-turbo", "Whisper Large V3 Turbo (Cepat)"),
            ModelOption("openai/whisper-large-v3",       "Whisper Large V3 (Akurat)"),
        )
    }

    val isOnlineMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONLINE_MODE] ?: false }
    val apiKey: Flow<String> = context.dataStore.data.map { it[KEY_API_KEY] ?: "" }
    val selectedModel: Flow<String> = context.dataStore.data.map { it[KEY_MODEL] ?: AVAILABLE_MODELS.first().id }
    val modeChosen: Flow<Boolean> = context.dataStore.data.map { it[KEY_MODE_CHOSEN] ?: false }
    val sttModel: Flow<String> = context.dataStore.data.map { it[KEY_STT_MODEL] ?: AVAILABLE_STT_MODELS.first().id }

    suspend fun setOnlineMode(v: Boolean) = context.dataStore.edit { it[KEY_ONLINE_MODE] = v }
    suspend fun setApiKey(v: String) = context.dataStore.edit { it[KEY_API_KEY] = v }
    suspend fun setModel(v: String) = context.dataStore.edit { it[KEY_MODEL] = v }
    suspend fun setModeChosen(v: Boolean) = context.dataStore.edit { it[KEY_MODE_CHOSEN] = v }
    suspend fun setSttModel(v: String) = context.dataStore.edit { it[KEY_STT_MODEL] = v }
}
