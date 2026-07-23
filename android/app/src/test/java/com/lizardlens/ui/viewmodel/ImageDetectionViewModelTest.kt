package com.lizardlens.ui.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.lizardlens.core.data.DetectionRepository
import com.lizardlens.core.model.DetectionResult
import com.lizardlens.core.model.DetectionSource
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ImageDetectionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var repository: DetectionRepository
    private lateinit var viewModel: ImageDetectionViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mock()
        repository = mock()
        viewModel = ImageDetectionViewModel(context, repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has correct defaults`() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.bitmap)
        assertTrue(state.detections.isEmpty())
        assertEquals(0L, state.inferenceTimeMs)
        assertEquals(0, state.imageWidth)
        assertEquals(0, state.imageHeight)
        assertNull(state.error)
    }

    @Test
    fun `clearImageDetection resets state to defaults`() = runTest {
        advanceUntilIdle()
        viewModel.clearImageDetection()
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.bitmap)
        assertTrue(state.detections.isEmpty())
        assertEquals(0L, state.inferenceTimeMs)
        assertEquals(0, state.imageWidth)
        assertEquals(0, state.imageHeight)
        assertNull(state.error)
    }

    @Test
    fun `onImagePicked with unresolvable URI sets error`() = runTest {
        val contentResolver = mock<ContentResolver>()
        whenever(context.contentResolver).thenReturn(contentResolver)

        val uri = mock<Uri>()
        viewModel.onImagePicked(uri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.bitmap)
        assertEquals("Failed to load image", state.error)
    }

    @Test
    fun `onImagePicked sets error when context has no contentResolver`() = runTest {
        whenever(context.contentResolver).thenThrow(NullPointerException())

        val uri = mock<Uri>()
        viewModel.onImagePicked(uri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Failed to load image", state.error)
    }
}
