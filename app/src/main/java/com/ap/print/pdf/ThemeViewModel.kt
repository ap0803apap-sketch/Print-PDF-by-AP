package com.ap.print.pdf

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor = _dynamicColor.asStateFlow()

    init {
        viewModelScope.launch {
            val result = ThemePreferences.loadTheme(getApplication())
            _themeMode.value = result.first
            _dynamicColor.value = result.second
        }
    }

    fun setTheme(mode: AppThemeMode) {
        _themeMode.value = mode
        save()
    }

    fun setDynamic(enabled: Boolean) {
        _dynamicColor.value = enabled
        save()
    }

    private fun save() {
        viewModelScope.launch {
            ThemePreferences.saveTheme(
                getApplication(),
                _themeMode.value,
                _dynamicColor.value
            )
        }
    }
}
