package com.srk.wifianalyzer.settings.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val dataStore: DataStore<Preferences> = context.settingsDataStore
}
