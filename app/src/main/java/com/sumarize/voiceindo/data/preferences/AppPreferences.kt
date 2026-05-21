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
            ModelOption("google/gemma-3-4b-it:free", "Gemma 3 4B (Gratis)"),
            ModelOption("meta-llama/llama-3.1-8b-instruct:free", "Llama 3.1 8B (Gratis)"),
            ModelOption("mistralai/mistral-7b-instruct:free", "Mistral 7B (Gratis)"),
            ModelOption("anthropic/claude-3-haiku", "Claude 3 Haiku"),
            ModelOption("openai/gpt-4o-mini", "GPT-4o Mini"),
        )
    }

    val isOnlineMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONLINE_MODE] ?: false }
    val apiKey: Flow<String> = context.dataStore.data.map { it[KEY_API_KEY] ?: "" }
    val selectedModel: Flow<String> = context.dataStore.data.map { it[KEY_MODEL] ?: AVAILABLE_MODELS.first().id }

    suspend fun setOnlineMode(v: Boolean) = context.dataStore.edit { it[KEY_ONLINE_MODE] = v }
    suspend fun setApiKey(v: String) = context.dataStore.edit { it[KEY_API_KEY] = v }
    suspend fun setModel(v: String) = context.dataStore.edit { it[KEY_MODEL] = v }
}
