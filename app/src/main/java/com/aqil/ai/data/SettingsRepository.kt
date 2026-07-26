package com.aqil.ai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aqil_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val PROFILES = stringPreferencesKey("profiles")
        val SELECTED = stringPreferencesKey("selected_profile")
        val EL_KEY = stringPreferencesKey("eleven_key")
        val EL_VOICE = stringPreferencesKey("eleven_voice")
        val EL_MODEL = stringPreferencesKey("eleven_model")
        val EL_ENABLED = booleanPreferencesKey("eleven_enabled")
        val AGENT_ENABLED = booleanPreferencesKey("agent_enabled")
    }

    val settings: Flow<AqilSettings> = context.dataStore.data.map { p ->
        val profiles = ModelProfile.listFromJson(p[Keys.PROFILES])
        val selected = p[Keys.SELECTED] ?: profiles.firstOrNull()?.id ?: ""
        AqilSettings(
            profiles = profiles,
            selectedProfileId = selected,
            voice = VoiceConfig(
                apiKey = p[Keys.EL_KEY] ?: "",
                voiceId = p[Keys.EL_VOICE] ?: "",
                modelId = p[Keys.EL_MODEL] ?: "eleven_turbo_v2_5",
                enabled = p[Keys.EL_ENABLED] ?: true,
            ),
            agentEnabled = p[Keys.AGENT_ENABLED] ?: true,
        )
    }

    suspend fun saveProfiles(profiles: List<ModelProfile>) {
        context.dataStore.edit { it[Keys.PROFILES] = ModelProfile.listToJson(profiles) }
    }

    suspend fun selectProfile(id: String) {
        context.dataStore.edit { it[Keys.SELECTED] = id }
    }

    suspend fun saveVoice(v: VoiceConfig) {
        context.dataStore.edit {
            it[Keys.EL_KEY] = v.apiKey
            it[Keys.EL_VOICE] = v.voiceId
            it[Keys.EL_MODEL] = v.modelId
            it[Keys.EL_ENABLED] = v.enabled
        }
    }

    suspend fun setAgentEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AGENT_ENABLED] = enabled }
    }
}
