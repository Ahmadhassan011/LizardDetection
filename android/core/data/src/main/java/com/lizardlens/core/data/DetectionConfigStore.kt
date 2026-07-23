package com.lizardlens.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lizardlens.core.inference.InferenceConfig
import com.lizardlens.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.detectionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "detection_config"
)

data class DetectionConfig(
    val confidenceThreshold: Float = 0.45f,
    val iouThreshold: Float = 0.45f,
    val delegate: InferenceConfig.Delegate = InferenceConfig.Delegate.AUTO,
    val thermalWarningsEnabled: Boolean = true
)

@Singleton
class DetectionConfigStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore get() = context.detectionDataStore

    val configFlow: Flow<DetectionConfig> = dataStore.data.map { prefs ->
        DetectionConfig(
            confidenceThreshold = prefs[CONFIDENCE_THRESHOLD] ?: 0.45f,
            iouThreshold = prefs[IOU_THRESHOLD] ?: 0.45f,
            delegate = prefs[DELEGATE]?.let { InferenceConfig.Delegate.valueOf(it) }
                ?: InferenceConfig.Delegate.AUTO,
            thermalWarningsEnabled = prefs[THERMAL_WARNINGS] ?: true
        )
    }

    suspend fun updateConfidenceThreshold(value: Float) {
        AppLogger.i("Config: confidence threshold updated to $value")
        dataStore.edit { it[CONFIDENCE_THRESHOLD] = value }
    }

    suspend fun updateIouThreshold(value: Float) {
        AppLogger.i("Config: IoU threshold updated to $value")
        dataStore.edit { it[IOU_THRESHOLD] = value }
    }

    suspend fun updateDelegate(value: InferenceConfig.Delegate) {
        AppLogger.i("Config: delegate updated to $value")
        dataStore.edit { it[DELEGATE] = value.name }
    }

    suspend fun updateThermalWarningsEnabled(value: Boolean) {
        AppLogger.i("Config: thermal warnings $value")
        dataStore.edit { it[THERMAL_WARNINGS] = value }
    }

    fun toInferenceConfig(config: DetectionConfig): InferenceConfig = InferenceConfig(
        confidenceThreshold = config.confidenceThreshold,
        iouThreshold = config.iouThreshold,
        delegate = config.delegate
    )

    companion object {
        private val CONFIDENCE_THRESHOLD = floatPreferencesKey("confidence_threshold")
        private val IOU_THRESHOLD = floatPreferencesKey("iou_threshold")
        private val DELEGATE = stringPreferencesKey("delegate")
        private val THERMAL_WARNINGS = booleanPreferencesKey("thermal_warnings")
    }
}
