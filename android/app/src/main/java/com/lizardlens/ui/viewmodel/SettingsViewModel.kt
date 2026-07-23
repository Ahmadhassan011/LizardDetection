package com.lizardlens.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lizardlens.core.data.DetectionConfig
import com.lizardlens.core.data.DetectionConfigStore
import com.lizardlens.core.inference.InferenceConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configStore: DetectionConfigStore
) : ViewModel() {

    val configFlow: StateFlow<DetectionConfig> = configStore.configFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DetectionConfig()
        )

    fun updateConfidenceThreshold(value: Float) {
        viewModelScope.launch {
            configStore.updateConfidenceThreshold(value)
        }
    }

    fun updateIouThreshold(value: Float) {
        viewModelScope.launch {
            configStore.updateIouThreshold(value)
        }
    }

    fun updateDelegate(value: InferenceConfig.Delegate) {
        viewModelScope.launch {
            configStore.updateDelegate(value)
        }
    }

    fun updateThermalWarnings(value: Boolean) {
        viewModelScope.launch {
            configStore.updateThermalWarningsEnabled(value)
        }
    }
}
