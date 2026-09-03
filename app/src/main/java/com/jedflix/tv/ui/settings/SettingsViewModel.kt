package com.jedflix.tv.ui.settings

import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.jedflix.tv.data.rdpairing.RdKeyPairingClient
import com.jedflix.tv.data.rdpairing.RdKeyPollResult
import com.jedflix.tv.data.rdpairing.encodeQrBitmap
import com.jedflix.tv.data.rdpairing.generatePairingCode
import com.jedflix.tv.data.rdpairing.pairingPageUrl
import com.jedflix.tv.data.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class SettingsViewModel(
    private val store: SettingsStore,
    private val pairingClient: RdKeyPairingClient = RdKeyPairingClient(),
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private var persistJob: Job? = null
    private var pairingJob: Job? = null

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

    fun startQrPairing() {
        pairingJob?.cancel()
        pairingJob = viewModelScope.launch { runPairing() }
    }

    fun cancelQrPairing() {
        pairingJob?.cancel()
        pairingJob = null
        _state.update { it.copy(qrPairing = QrPairingUi.Hidden) }
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

    private suspend fun runPairing() {
        _state.update { it.copy(qrPairing = QrPairingUi.Opening) }
        val code = generatePairingCode()
        try {
            pairingClient.openSlot(code)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(qrPairing = QrPairingUi.Failed) }
            return
        }

        val qr = try {
            withContext(Dispatchers.Default) {
                encodeQrBitmap(pairingPageUrl(code)).asImageBitmap()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(qrPairing = QrPairingUi.Failed) }
            return
        }
        _state.update { it.copy(qrPairing = QrPairingUi.Waiting(qr)) }

        val startedAt = System.nanoTime()
        var consecutiveFailures = 0
        while (true) {
            if (elapsedMs(startedAt) >= SLOT_TTL_MS) {
                _state.update { it.copy(qrPairing = QrPairingUi.Expired) }
                return
            }
            val result = try {
                pairingClient.poll(code)
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                consecutiveFailures += 1
                if (consecutiveFailures >= MAX_POLL_FAILURES) {
                    _state.update { it.copy(qrPairing = QrPairingUi.Failed) }
                    return
                }
                delay(POLL_RETRY_DELAY_MS)
                continue
            } catch (_: Exception) {
                _state.update { it.copy(qrPairing = QrPairingUi.Failed) }
                return
            }
            consecutiveFailures = 0
            when (result) {
                RdKeyPollResult.Waiting -> Unit
                RdKeyPollResult.Expired -> {
                    _state.update { it.copy(qrPairing = QrPairingUi.Expired) }
                    return
                }
                is RdKeyPollResult.Ready -> {
                    persistJob?.cancel()
                    store.setRealDebridApiKey(result.apiKey)
                    _state.update {
                        it.copy(
                            apiKey = result.apiKey,
                            savedApiKey = result.apiKey,
                            dirty = false,
                            qrPairing = QrPairingUi.Hidden,
                        )
                    }
                    return
                }
            }
        }
    }

    private fun elapsedMs(startedAtNanos: Long): Long =
        (System.nanoTime() - startedAtNanos) / 1_000_000L

    class Factory(
        private val store: SettingsStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            SettingsViewModel(store) as T
    }

    private companion object {
        const val PERSIST_DEBOUNCE_MS = 400L
        const val SLOT_TTL_MS = 10 * 60 * 1000L
        const val POLL_RETRY_DELAY_MS = 2_000L
        const val MAX_POLL_FAILURES = 3
    }
}
