package com.srk.wifianalyzer.settings.domain

import com.srk.wifianalyzer.settings.domain.models.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val userSettings: Flow<UserSettings>

    suspend fun updateUserSettings(reducer: (UserSettings) -> UserSettings)

    suspend fun resetToDefaults()
}
