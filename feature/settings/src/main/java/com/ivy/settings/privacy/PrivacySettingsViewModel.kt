package com.ivy.settings.privacy

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.data.datastore.DatastoreKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class PrivacySettingsState(
    val secureScreen: Boolean = false,
    val hideAmounts: Boolean = false,
)

sealed interface PrivacySettingsEvent {
    data class SetSecureScreen(val enabled: Boolean) : PrivacySettingsEvent
    data class SetHideAmounts(val enabled: Boolean) : PrivacySettingsEvent
}

@HiltViewModel
class PrivacySettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val _state = MutableStateFlow(PrivacySettingsState())
    val state: StateFlow<PrivacySettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            _state.update {
                it.copy(
                    secureScreen = prefs[DatastoreKeys.SECURE_SCREEN_ENABLED] ?: false,
                    hideAmounts = prefs[DatastoreKeys.HIDE_AMOUNTS] ?: false,
                )
            }
        }
    }

    fun onEvent(event: PrivacySettingsEvent) {
        when (event) {
            is PrivacySettingsEvent.SetSecureScreen -> {
                _state.update { it.copy(secureScreen = event.enabled) }
                write(DatastoreKeys.SECURE_SCREEN_ENABLED, event.enabled)
            }

            is PrivacySettingsEvent.SetHideAmounts -> {
                _state.update { it.copy(hideAmounts = event.enabled) }
                write(DatastoreKeys.HIDE_AMOUNTS, event.enabled)
            }
        }
    }

    private fun write(key: Preferences.Key<Boolean>, value: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[key] = value }
        }
    }
}
