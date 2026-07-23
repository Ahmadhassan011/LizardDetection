package com.lizardlens.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lizardlens.core.data.DetectionEntity
import com.lizardlens.core.data.DetectionRepository
import com.lizardlens.core.model.Detection
import com.lizardlens.core.model.DetectionSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: DetectionRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow<DetectionSource?>(null)
    val selectedFilter = _selectedFilter

    private val pendingDeletes = ConcurrentHashMap<Long, DetectionEntity>()

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

    fun deleteWithUndo(entity: DetectionEntity): DetectionEntity {
        pendingDeletes[entity.id] = entity
        viewModelScope.launch {
            repository.deleteDetection(entity.id)
        }
        return entity
    }

    fun undoDelete(entity: DetectionEntity) {
        if (pendingDeletes.remove(entity.id) != null) {
            val boundingBox = repository.parseBoundingBox(entity.boundingBox)
            val detection = Detection(
                boundingBox = boundingBox,
                confidence = entity.confidence,
                label = "Lizard"
            )
            viewModelScope.launch {
                repository.persistDetection(
                    detection = detection,
                    source = entity.source,
                    thumbnailUri = entity.imageUri
                )
            }
        }
    }

    fun consumePendingDelete(id: Long) {
        pendingDeletes.remove(id)
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}