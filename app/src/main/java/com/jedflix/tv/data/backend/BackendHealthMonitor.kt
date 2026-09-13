package com.jedflix.tv.data.backend

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-wide holder for the last known API health. [check] is idempotent while a
 * check is in flight, so Settings can call it freely on every visit.
 */
class BackendHealthMonitor(
    private val client: BackendHealthClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<BackendHealthState>(BackendHealthState.Unknown)
    val state: StateFlow<BackendHealthState> = _state.asStateFlow()

    private var inFlight: Job? = null

    fun check() {
        if (inFlight?.isActive == true) return
        _state.value = BackendHealthState.Checking
        inFlight = scope.launch {
            _state.value = client.check()
        }
    }
}
