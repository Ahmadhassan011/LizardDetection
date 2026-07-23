package com.lizardlens.ui.viewmodel

import com.lizardlens.core.data.DetectionConfig
import com.lizardlens.core.data.DetectionConfigStore
import com.lizardlens.core.inference.InferenceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var configStore: DetectionConfigStore
    private lateinit var viewModel: SettingsViewModel

    private val defaultConfig = DetectionConfig(
        confidenceThreshold = 0.45f,
        iouThreshold = 0.45f,
        delegate = InferenceConfig.Delegate.AUTO,
        thermalWarningsEnabled = true
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        configStore = mock()
        whenever(configStore.configFlow).thenReturn(MutableStateFlow(defaultConfig))
        viewModel = SettingsViewModel(configStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial config reflects defaults from store`() = runTest {
        advanceUntilIdle()
        val config = viewModel.configFlow.value
        assertEquals(0.45f, config.confidenceThreshold)
        assertEquals(0.45f, config.iouThreshold)
        assertEquals(InferenceConfig.Delegate.AUTO, config.delegate)
        assertEquals(true, config.thermalWarningsEnabled)
    }

    @Test
    fun `updateConfidenceThreshold delegates to configStore`() = runTest {
        advanceUntilIdle()
        viewModel.updateConfidenceThreshold(0.70f)
        advanceUntilIdle()
        verify(configStore).updateConfidenceThreshold(0.70f)
    }

    @Test
    fun `updateIouThreshold delegates to configStore`() = runTest {
        advanceUntilIdle()
        viewModel.updateIouThreshold(0.60f)
        advanceUntilIdle()
        verify(configStore).updateIouThreshold(0.60f)
    }

    @Test
    fun `updateDelegate delegates to configStore`() = runTest {
        advanceUntilIdle()
        viewModel.updateDelegate(InferenceConfig.Delegate.GPU)
        advanceUntilIdle()
        verify(configStore).updateDelegate(InferenceConfig.Delegate.GPU)
    }

    @Test
    fun `updateThermalWarnings delegates to configStore`() = runTest {
        advanceUntilIdle()
        viewModel.updateThermalWarnings(false)
        advanceUntilIdle()
        verify(configStore).updateThermalWarningsEnabled(false)
    }

    @Test
    fun `configFlow emits updated values when store changes`() = runTest {
        val configFlow = MutableStateFlow(defaultConfig)
        whenever(configStore.configFlow).thenReturn(configFlow)
        viewModel = SettingsViewModel(configStore)
        advanceUntilIdle()

        assertEquals(0.45f, viewModel.configFlow.value.confidenceThreshold)

        configFlow.value = defaultConfig.copy(confidenceThreshold = 0.80f)
        advanceUntilIdle()
        assertEquals(0.80f, viewModel.configFlow.value.confidenceThreshold)
    }

    @Test
    fun `configFlow reflects delegate change`() = runTest {
        val configFlow = MutableStateFlow(defaultConfig)
        whenever(configStore.configFlow).thenReturn(configFlow)
        viewModel = SettingsViewModel(configStore)
        advanceUntilIdle()

        assertEquals(InferenceConfig.Delegate.AUTO, viewModel.configFlow.value.delegate)

        configFlow.value = defaultConfig.copy(delegate = InferenceConfig.Delegate.GPU)
        advanceUntilIdle()
        assertEquals(InferenceConfig.Delegate.GPU, viewModel.configFlow.value.delegate)
    }

    @Test
    fun `configFlow reflects thermal warnings toggle`() = runTest {
        val configFlow = MutableStateFlow(defaultConfig)
        whenever(configStore.configFlow).thenReturn(configFlow)
        viewModel = SettingsViewModel(configStore)
        advanceUntilIdle()

        assertEquals(true, viewModel.configFlow.value.thermalWarningsEnabled)

        configFlow.value = defaultConfig.copy(thermalWarningsEnabled = false)
        advanceUntilIdle()
        assertEquals(false, viewModel.configFlow.value.thermalWarningsEnabled)
    }
}
