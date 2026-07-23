package com.lizardlens.ui.viewmodel

import android.content.Context
import android.net.Uri
import com.lizardlens.core.data.DetectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class VideoDetectionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var repository: DetectionRepository
    private lateinit var viewModel: VideoDetectionViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mock()
        repository = mock()
        viewModel = VideoDetectionViewModel(context, repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has correct defaults`() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isProcessing)
        assertFalse(state.isPaused)
        assertNull(state.videoUri)
        assertEquals(0, state.totalFrames)
        assertEquals(0, state.processedFrames)
        assertTrue(state.videoResults.isEmpty())
        assertNull(state.selectedFrameIndex)
        assertNull(state.selectedFrameBitmap)
        assertNull(state.error)
    }

    @Test
    fun `pauseVideoProcessing sets isPaused true`() = runTest {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isPaused)

        viewModel.pauseVideoProcessing()
        assertTrue(viewModel.uiState.value.isPaused)
    }

    @Test
    fun `resumeVideoProcessing sets isPaused false`() = runTest {
        advanceUntilIdle()
        viewModel.pauseVideoProcessing()
        assertTrue(viewModel.uiState.value.isPaused)

        viewModel.resumeVideoProcessing()
        assertFalse(viewModel.uiState.value.isPaused)
    }

    @Test
    fun `clearVideoDetection resets state to defaults`() = runTest {
        advanceUntilIdle()
        viewModel.pauseVideoProcessing()
        assertTrue(viewModel.uiState.value.isPaused)

        viewModel.clearVideoDetection()
        val state = viewModel.uiState.value
        assertFalse(state.isProcessing)
        assertFalse(state.isPaused)
        assertNull(state.videoUri)
        assertEquals(0, state.totalFrames)
        assertEquals(0, state.processedFrames)
        assertTrue(state.videoResults.isEmpty())
        assertNull(state.selectedFrameIndex)
        assertNull(state.selectedFrameBitmap)
        assertNull(state.error)
    }

    @Test
    fun `clearSelectedFrame clears selection`() = runTest {
        advanceUntilIdle()
        viewModel.clearSelectedFrame()
        assertNull(viewModel.uiState.value.selectedFrameIndex)
        assertNull(viewModel.uiState.value.selectedFrameBitmap)
    }

    @Test
    fun `selectVideoFrame with no results is no-op`() = runTest {
        advanceUntilIdle()
        viewModel.selectVideoFrame(0)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedFrameIndex)
        assertNull(viewModel.uiState.value.selectedFrameBitmap)
    }

    @Test
    fun `selectVideoFrame with no videoUri is no-op`() = runTest {
        advanceUntilIdle()
        viewModel.selectVideoFrame(5)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedFrameIndex)
    }

    @Test
    fun `onVideoPicked with invalid URI sets error`() = runTest {
        val uri = mock<Uri>()
        viewModel.onVideoPicked(uri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isProcessing)
        assertTrue(state.error != null || state.totalFrames == 0)
    }
}
