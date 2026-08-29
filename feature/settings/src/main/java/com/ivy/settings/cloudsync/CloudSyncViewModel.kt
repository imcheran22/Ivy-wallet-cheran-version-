package com.ivy.settings.cloudsync

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.data.sync.CloudSyncRepository
import com.ivy.data.sync.CloudSyncSettings
import com.ivy.data.sync.RestorePreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class CloudSyncState(
    val loading: Boolean = true,
    val enabled: Boolean = false,
    val configured: Boolean = false,
    val ownerId: String = "",
    val pairingInput: String = "",
    val lastSyncedEpochMs: Long? = null,
    val lastResult: String? = null,
    val busy: Boolean = false,
    val preview: RestorePreview? = null,
    val error: String? = null,
    val paired: Boolean = false,
)

sealed interface CloudSyncEvent {
    data class SetPairingInput(val value: String) : CloudSyncEvent
    data object PairWithCode : CloudSyncEvent
    data object SyncNow : CloudSyncEvent
    data object PreviewRestore : CloudSyncEvent
    data object ConfirmRestore : CloudSyncEvent
    data object DismissPreview : CloudSyncEvent
}

/**
 * The screen that turns "a backup" into "sync": pairing, a merge, and enough status to tell
 * whether either is actually happening.
 */
@HiltViewModel
class CloudSyncViewModel @Inject constructor(
    private val settings: CloudSyncSettings,
    private val repository: CloudSyncRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CloudSyncState())
    val state: StateFlow<CloudSyncState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onEvent(event: CloudSyncEvent) {
        when (event) {
            is CloudSyncEvent.SetPairingInput -> _state.update {
                it.copy(pairingInput = event.value, paired = false)
            }

            CloudSyncEvent.PairWithCode -> pair()
            CloudSyncEvent.SyncNow -> syncNow()
            CloudSyncEvent.PreviewRestore -> previewRestore()
            CloudSyncEvent.ConfirmRestore -> confirmRestore()
            CloudSyncEvent.DismissPreview -> _state.update { it.copy(preview = null) }
        }
    }

    private fun pair() {
        val code = _state.value.pairingInput.trim()
        if (code.isBlank()) return

        viewModelScope.launch {
            settings.setOwnerId(code)
            refreshNow()
            _state.update { it.copy(paired = true, pairingInput = "") }
        }
    }

    private fun syncNow() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            repository.sync().fold(
                ifLeft = { error ->
                    settings.setLastResult(error)
                    _state.update { it.copy(busy = false, error = error) }
                },
                ifRight = { result ->
                    settings.setLastResult("Pulled ${result.pulled}, pushed ${result.pushed}")
                    refreshNow()
                    _state.update { it.copy(busy = false) }
                },
            )
        }
    }

    private fun previewRestore() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            repository.previewRestore().fold(
                ifLeft = { error -> _state.update { it.copy(busy = false, error = error) } },
                ifRight = { preview ->
                    _state.update { it.copy(busy = false, preview = preview) }
                },
            )
        }
    }

    private fun confirmRestore() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, preview = null, error = null) }
            repository.pullAll().fold(
                ifLeft = { error ->
                    settings.setLastResult(error)
                    _state.update { it.copy(busy = false, error = error) }
                },
                ifRight = {
                    settings.setLastResult(RESTORED)
                    refreshNow()
                    _state.update { it.copy(busy = false) }
                },
            )
        }
    }

    private fun refresh() {
        viewModelScope.launch { refreshNow() }
    }

    private suspend fun refreshNow() {
        val prefs = settings.current()
        val ownerId = settings.ownerId()

        _state.update {
            it.copy(
                loading = false,
                enabled = prefs.enabled,
                configured = prefs.supabaseUrl.isNotBlank() &&
                    prefs.supabaseAnonKey.isNotBlank(),
                ownerId = ownerId,
                lastSyncedEpochMs = prefs.lastSyncedEpochMs,
                lastResult = prefs.lastResult,
            )
        }
    }

    companion object {
        const val RESTORED = "Restored from cloud"
    }
}
