package com.lizardlens.ui.viewmodel

import com.lizardlens.core.data.DetectionEntity
import com.lizardlens.core.data.DetectionRepository
import com.lizardlens.core.model.BoundingBox
import com.lizardlens.core.model.DetectionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: DetectionRepository
    private lateinit var viewModel: HistoryViewModel

    private val sampleDetection = DetectionEntity(
        id = 42L,
        timestamp = 1700000000000L,
        confidence = 0.87f,
        boundingBox = """{"x1":0.1,"y1":0.2,"x2":0.5,"y2":0.6}""",
        source = DetectionSource.CAMERA,
        imageUri = "content://thumbnails/42.jpg"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()
        whenever(repository.allDetections).thenReturn(MutableStateFlow(emptyList()))
        whenever(repository.getDetectionsBySource(DetectionSource.CAMERA))
            .thenReturn(flowOf(emptyList()))
        whenever(repository.getDetectionsBySource(DetectionSource.IMAGE))
            .thenReturn(flowOf(emptyList()))
        whenever(repository.getDetectionsBySource(DetectionSource.VIDEO))
            .thenReturn(flowOf(emptyList()))
        whenever(repository.parseBoundingBox(org.mockito.kotlin.any()))
            .thenReturn(BoundingBox(0.1f, 0.2f, 0.5f, 0.6f))
        viewModel = HistoryViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `deleteWithUndo calls deleteDetection on repository`() = runTest {
        advanceUntilIdle()
        viewModel.deleteWithUndo(sampleDetection)
        advanceUntilIdle()
        verify(repository).deleteDetection(42L)
    }

    @Test
    fun `deleteWithUndo returns the entity for snackbar display`() = runTest {
        advanceUntilIdle()
        val result = viewModel.deleteWithUndo(sampleDetection)
        advanceUntilIdle()
        assertNotNull(result)
        assertEquals(42L, result.id)
        assertEquals(0.87f, result.confidence)
    }

    @Test
    fun `undoDelete re-inserts the entity into repository`() = runTest {
        advanceUntilIdle()
        viewModel.deleteWithUndo(sampleDetection)
        advanceUntilIdle()

        viewModel.undoDelete(sampleDetection)
        advanceUntilIdle()

        verify(repository).persistDetection(
            detection = argThat { confidence == 0.87f && label == "Lizard" },
            source = org.mockito.kotlin.eq(DetectionSource.CAMERA),
            thumbnailUri = org.mockito.kotlin.eq("content://thumbnails/42.jpg")
        )
    }

    @Test
    fun `undoDelete clears the pending delete`() = runTest {
        advanceUntilIdle()
        viewModel.deleteWithUndo(sampleDetection)
        advanceUntilIdle()

        viewModel.undoDelete(sampleDetection)
        advanceUntilIdle()

        // Calling undoDelete again should be a no-op (entity no longer pending)
        viewModel.undoDelete(sampleDetection)
        advanceUntilIdle()

        // persistDetection should only be called once (from the first undo)
        verify(repository, org.mockito.kotlin.times(1)).persistDetection(
            detection = org.mockito.kotlin.any(),
            source = org.mockito.kotlin.eq(DetectionSource.CAMERA),
            thumbnailUri = org.mockito.kotlin.any()
        )
    }

    @Test
    fun `consumePendingDelete removes entry without re-inserting`() = runTest {
        advanceUntilIdle()
        viewModel.deleteWithUndo(sampleDetection)
        advanceUntilIdle()

        viewModel.consumePendingDelete(sampleDetection.id)

        // undoDelete should now be a no-op (entry was consumed)
        viewModel.undoDelete(sampleDetection)
        advanceUntilIdle()

        verify(repository, org.mockito.kotlin.never()).persistDetection(
            detection = org.mockito.kotlin.any(),
            source = org.mockito.kotlin.any(),
            thumbnailUri = org.mockito.kotlin.any()
        )
    }

    @Test
    fun `deleteWithUndo stores multiple deletions independently`() = runTest {
        val secondDetection = sampleDetection.copy(id = 99L, confidence = 0.65f)

        advanceUntilIdle()
        viewModel.deleteWithUndo(sampleDetection)
        viewModel.deleteWithUndo(secondDetection)
        advanceUntilIdle()

        verify(repository).deleteDetection(42L)
        verify(repository).deleteDetection(99L)
    }

    @Test
    fun `undoDelete for one detection does not affect another`() = runTest {
        val secondDetection = sampleDetection.copy(id = 99L, confidence = 0.65f)

        advanceUntilIdle()
        viewModel.deleteWithUndo(sampleDetection)
        viewModel.deleteWithUndo(secondDetection)
        advanceUntilIdle()

        viewModel.undoDelete(sampleDetection)
        advanceUntilIdle()

        // Only one persistDetection should be called (for sampleDetection)
        verify(repository, org.mockito.kotlin.times(1)).persistDetection(
            detection = org.mockito.kotlin.any(),
            source = org.mockito.kotlin.eq(DetectionSource.CAMERA),
            thumbnailUri = org.mockito.kotlin.any()
        )
    }

    @Test
    fun `clearHistory delegates to repository`() = runTest {
        advanceUntilIdle()
        viewModel.clearHistory()
        advanceUntilIdle()
        verify(repository).clearHistory()
    }

    @Test
    fun `setFilter updates selectedFilter`() = runTest {
        advanceUntilIdle()
        assertNull(viewModel.selectedFilter.value)

        viewModel.setFilter(DetectionSource.IMAGE)
        advanceUntilIdle()
        assertEquals(DetectionSource.IMAGE, viewModel.selectedFilter.value)
    }

    @Test
    fun `setFilter null shows all detections`() = runTest {
        advanceUntilIdle()
        viewModel.setFilter(DetectionSource.CAMERA)
        advanceUntilIdle()
        assertEquals(DetectionSource.CAMERA, viewModel.selectedFilter.value)

        viewModel.setFilter(null)
        advanceUntilIdle()
        assertNull(viewModel.selectedFilter.value)
    }
}
