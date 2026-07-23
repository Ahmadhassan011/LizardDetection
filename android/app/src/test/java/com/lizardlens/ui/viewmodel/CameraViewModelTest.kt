package com.lizardlens.ui.viewmodel

import android.graphics.Bitmap
import com.lizardlens.core.camera.ThermalLevel
import com.lizardlens.core.camera.ThermalManager
import com.lizardlens.core.data.DetectionConfig
import com.lizardlens.core.data.DetectionConfigStore
import com.lizardlens.core.data.DetectionRepository
import com.lizardlens.core.inference.InferenceConfig
import com.lizardlens.core.inference.InferenceEngine
import com.lizardlens.core.model.DetectionResult
import com.lizardlens.core.model.DetectionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: DetectionRepository
    private lateinit var thermalManager: ThermalManager
    private lateinit var configStore: DetectionConfigStore
    private lateinit var inferenceEngine: InferenceEngine
    private lateinit var viewModel: CameraViewModel

    private val defaultConfig = DetectionConfig(
        confidenceThreshold = 0.45f,
        iouThreshold = 0.45f,
        delegate = InferenceConfig.Delegate.CPU,
        thermalWarningsEnabled = true
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        repository = mock()
        thermalManager = mock()
        configStore = mock()
        inferenceEngine = mock()
        whenever(inferenceEngine.activeDelegate).thenReturn(InferenceConfig.Delegate.CPU)

        val thermalFlow = MutableStateFlow(ThermalLevel.NORMAL)
        whenever(thermalManager.thermalLevel).thenReturn(thermalFlow)
        whenever(configStore.configFlow).thenReturn(MutableStateFlow(defaultConfig))

        viewModel = CameraViewModel(repository, thermalManager, configStore, inferenceEngine)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has correct defaults`() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isDetecting)
        assertTrue(state.detections.isEmpty())
        assertEquals(0, state.fps)
        assertEquals(ThermalLevel.NORMAL, state.thermalLevel)
        assertFalse(state.isFrontCamera)
        assertFalse(state.permissionGranted)
    }

    @Test
    fun `toggleDetection switches detecting state`() = runTest {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isDetecting)

        viewModel.toggleDetection()
        assertTrue(viewModel.uiState.value.isDetecting)

        viewModel.toggleDetection()
        assertFalse(viewModel.uiState.value.isDetecting)
    }

    @Test
    fun `toggleDetection clears detections when stopping`() = runTest {
        advanceUntilIdle()
        viewModel.toggleDetection()
        assertTrue(viewModel.uiState.value.isDetecting)

        viewModel.toggleDetection()
        assertFalse(viewModel.uiState.value.isDetecting)
        assertTrue(viewModel.uiState.value.detections.isEmpty())
    }

    @Test
    fun `toggleCamera switches camera facing`() = runTest {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isFrontCamera)

        viewModel.toggleCamera()
        assertTrue(viewModel.uiState.value.isFrontCamera)

        viewModel.toggleCamera()
        assertFalse(viewModel.uiState.value.isFrontCamera)
    }

    @Test
    fun `onPermissionResult updates permission state`() = runTest {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.permissionGranted)

        viewModel.onPermissionResult(true)
        assertTrue(viewModel.uiState.value.permissionGranted)

        viewModel.onPermissionResult(false)
        assertFalse(viewModel.uiState.value.permissionGranted)
    }

    @Test
    fun `processCameraFrame does nothing when not detecting`() = runTest {
        advanceUntilIdle()
        viewModel.processCameraFrame(mock())

        verify(repository, never()).detectAndPersist(
            any<Bitmap>(),
            any<DetectionSource>(),
            any()
        )
    }

    @Test
    fun `processCameraFrame calls repository when detecting`() = runTest {
        whenever(repository.detectAndPersist(any<Bitmap>(), any<DetectionSource>(), any()))
            .thenReturn(DetectionResult(emptyList(), 25L))

        advanceUntilIdle()
        viewModel.toggleDetection()
        assertTrue(viewModel.uiState.value.isDetecting)

        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(640)
        whenever(bitmap.height).thenReturn(480)

        viewModel.processCameraFrame(bitmap)
        advanceUntilIdle()

        verify(repository).detectAndPersist(bitmap, DetectionSource.CAMERA, null)
    }

    @Test
    fun `clearDetections resets detection list`() = runTest {
        advanceUntilIdle()
        viewModel.clearDetections()
        assertTrue(viewModel.uiState.value.detections.isEmpty())
    }

    @Test
    fun `thermal level updates propagate to UI state`() = runTest {
        val thermalFlow = MutableStateFlow(ThermalLevel.NORMAL)
        whenever(thermalManager.thermalLevel).thenReturn(thermalFlow)

        viewModel = CameraViewModel(repository, thermalManager, configStore, inferenceEngine)
        advanceUntilIdle()

        assertEquals(ThermalLevel.NORMAL, viewModel.uiState.value.thermalLevel)

        thermalFlow.value = ThermalLevel.MODERATE
        advanceUntilIdle()
        assertEquals(ThermalLevel.MODERATE, viewModel.uiState.value.thermalLevel)

        thermalFlow.value = ThermalLevel.SEVERE
        advanceUntilIdle()
        assertEquals(ThermalLevel.SEVERE, viewModel.uiState.value.thermalLevel)
    }

    @Test
    fun `isGpuActive reflects engine activeDelegate on init`() = runTest {
        whenever(inferenceEngine.activeDelegate).thenReturn(InferenceConfig.Delegate.GPU)

        viewModel = CameraViewModel(repository, thermalManager, configStore, inferenceEngine)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isGpuActive)
    }

    @Test
    fun `isGpuActive false when engine delegate is CPU`() = runTest {
        whenever(inferenceEngine.activeDelegate).thenReturn(InferenceConfig.Delegate.CPU)

        viewModel = CameraViewModel(repository, thermalManager, configStore, inferenceEngine)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isGpuActive)
    }

    @Test
    fun `isGpuActive reflects engine delegate when config says GPU but engine is CPU`() = runTest {
        whenever(inferenceEngine.activeDelegate).thenReturn(InferenceConfig.Delegate.CPU)
        val configFlow = MutableStateFlow(defaultConfig.copy(delegate = InferenceConfig.Delegate.GPU))
        whenever(configStore.configFlow).thenReturn(configFlow)

        viewModel = CameraViewModel(repository, thermalManager, configStore, inferenceEngine)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isGpuActive)
    }
}
