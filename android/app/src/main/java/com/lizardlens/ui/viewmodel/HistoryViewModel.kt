package com.lizardlens.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lizardlens.core.data.DetectionRepository
import com.lizardlens.core.model.DetectionSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: DetectionRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow<DetectionSource?>(null)
    val selectedFilter = _selectedFilter

    val filteredDetections = _selectedFilter
        .flatMapLatest { filter ->
            if (filter == null) {
                repository.allDetections
            } else {
                repository.getDetectionsBySource(filter)
            }
        }

    fun setFilter(source: DetectionSource?) {
        _selectedFilter.value = source
    }

    fun deleteDetection(id: Long) {
        viewModelScope.launch {
            repository.deleteDetection(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}