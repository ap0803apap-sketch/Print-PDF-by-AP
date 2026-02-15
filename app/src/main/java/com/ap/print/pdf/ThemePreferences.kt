package com.ap.print.pdf

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore("theme_prefs")

object ThemePreferences {

    private val THEME_MODE = stringPreferencesKey("theme_mode")
    private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")

    suspend fun saveTheme(
        context: Context,
        themeMode: AppThemeMode,
        dynamicColor: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE] = themeMode.name
            prefs[DYNAMIC_COLOR] = dynamicColor
        }
    }

    suspend fun loadTheme(context: Context): Pair<AppThemeMode, Boolean> {
        val prefs = context.dataStore.data.first()

        val theme =
            prefs[THEME_MODE]?.let { AppThemeMode.valueOf(it) } ?: AppThemeMode.SYSTEM

        val dynamic = prefs[DYNAMIC_COLOR] ?: true

        return theme to dynamic
    }
}
