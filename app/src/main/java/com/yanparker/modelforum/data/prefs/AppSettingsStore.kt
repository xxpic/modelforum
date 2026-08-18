package com.yanparker.modelforum.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val minRequestIntervalMs: Long = 3000,
    val maxMessagesPerModel: Int = 15,
    val maxTokens: Int = 800,
    val temperature: Double = 0.7,
    val contextTrimChars: Int = 120_000,
    val resumeInterrupted: Boolean = true,
    val darkTheme: Boolean = false,
    val dynamicColor: Boolean = true,
)

class AppSettingsStore(private val context: Context) {

    private object Keys {
        val MIN_INTERVAL = longPreferencesKey("min_interval_ms")
        val MAX_MESSAGES = intPreferencesKey("max_messages_per_model")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val TEMPERATURE = doublePreferencesKey("temperature")
        val TRIM_CHARS = intPreferencesKey("trim_chars")
        val RESUME = booleanPreferencesKey("resume_interrupted")
        val DARK = booleanPreferencesKey("dark")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            minRequestIntervalMs = p[Keys.MIN_INTERVAL] ?: 3000,
            maxMessagesPerModel = p[Keys.MAX_MESSAGES] ?: 15,
            maxTokens = p[Keys.MAX_TOKENS] ?: 800,
            temperature = p[Keys.TEMPERATURE] ?: 0.7,
            contextTrimChars = p[Keys.TRIM_CHARS] ?: 120_000,
            resumeInterrupted = p[Keys.RESUME] ?: true,
            darkTheme = p[Keys.DARK] ?: false,
            dynamicColor = p[Keys.DYNAMIC] ?: true,
        )
    }

    suspend fun update(minRequestIntervalMs: Long? = null, maxMessagesPerModel: Int? = null,
                       maxTokens: Int? = null, temperature: Double? = null,
                       contextTrimChars: Int? = null, resumeInterrupted: Boolean? = null,
                       darkTheme: Boolean? = null, dynamicColor: Boolean? = null) {
        context.dataStore.edit { p ->
            minRequestIntervalMs?.let { p[Keys.MIN_INTERVAL] = it }
            maxMessagesPerModel?.let { p[Keys.MAX_MESSAGES] = it }
            maxTokens?.let { p[Keys.MAX_TOKENS] = it }
            temperature?.let { p[Keys.TEMPERATURE] = it }
            contextTrimChars?.let { p[Keys.TRIM_CHARS] = it }
            resumeInterrupted?.let { p[Keys.RESUME] = it }
            darkTheme?.let { p[Keys.DARK] = it }
            dynamicColor?.let { p[Keys.DYNAMIC] = it }
        }
    }
}