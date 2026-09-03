package com.jedflix.tv.ui.settings

data class SettingsUiState(
    val apiKey: String = "",
    val savedApiKey: String = "",
    val dirty: Boolean = false,
)
