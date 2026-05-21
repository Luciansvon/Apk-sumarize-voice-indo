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

        data class ModelOption(val id: String, val label: String)
        val AVAILABLE_MODELS = listOf(
            // Gratis — verified working text generation, May 2026
            ModelOption("deepseek/deepseek-r1:free",               "DeepSeek R1 (Gratis)"),
            ModelOption("deepseek/deepseek-chat-v3.1:free",        "DeepSeek Chat V3.1 (Gratis)"),
            ModelOption("meta-llama/llama-3.3-70b-instruct:free",  "Llama 3.3 70B (Gratis)"),
            ModelOption("meta-llama/llama-4-maverick:free",        "Llama 4 Maverick (Gratis)"),
            ModelOption("qwen/qwen-2.5-72b-instruct:free",         "Qwen 2.5 72B (Gratis)"),
            ModelOption("google/gemma-3-12b-it:free",              "Gemma 3 12B (Gratis)"),
            ModelOption("mistralai/mistral-7b-instruct:free",      "Mistral 7B (Gratis)"),
            // Berbayar
            ModelOption("anthropic/claude-3-haiku",                "Claude 3 Haiku"),
            ModelOption("openai/gpt-4o-mini",                      "GPT-4o Mini"),
        )
    }

    val isOnlineMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONLINE_MODE] ?: false }
    val apiKey: Flow<String> = context.dataStore.data.map { it[KEY_API_KEY] ?: "" }
    val selectedModel: Flow<String> = context.dataStore.data.map { it[KEY_MODEL] ?: AVAILABLE_MODELS.first().id }

    suspend fun setOnlineMode(v: Boolean) = context.dataStore.edit { it[KEY_ONLINE_MODE] = v }
    suspend fun setApiKey(v: String) = context.dataStore.edit { it[KEY_API_KEY] = v }
    suspend fun setModel(v: String) = context.dataStore.edit { it[KEY_MODEL] = v }
}
