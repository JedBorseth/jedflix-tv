package com.jedflix.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.jedflix.tv.data.settings.SettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val store: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private var persistJob: Job? = null

    init {
        viewModelScope.launch {
            store.realDebridApiKey.collect { saved ->
                _state.update { current ->
                    current.copy(
                        apiKey = if (current.dirty) current.apiKey else saved,
                        savedApiKey = saved,
                    )
                }
            }
        }
    }

    fun onApiKeyChange(value: String) {
        _state.update { it.copy(apiKey = value, dirty = true) }
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            persist()
        }
    }

    fun save() {
        persistJob?.cancel()
        viewModelScope.launch { persist() }
    }

    private suspend fun persist() {
        val value = _state.value.apiKey.trim()
        store.setRealDebridApiKey(value)
        _state.update {
            it.copy(
                apiKey = value,
                savedApiKey = value,
                dirty = false,
            )
        }
    }

    class Factory(
        private val store: SettingsStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            SettingsViewModel(store) as T
    }

    private companion object {
        const val PERSIST_DEBOUNCE_MS = 400L
    }
}
