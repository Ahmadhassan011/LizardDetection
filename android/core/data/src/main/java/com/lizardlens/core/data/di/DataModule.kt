package com.lizardlens.core.data.di

import android.content.Context
import androidx.room.Room
import com.lizardlens.core.data.AppDatabase
import com.lizardlens.core.data.DetectionDao
import com.lizardlens.core.inference.InferenceConfig
import com.lizardlens.core.inference.InferenceEngine
import com.lizardlens.core.inference.TfliteInferenceEngine
import com.lizardlens.core.logging.AppLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        AppLogger.i("Initializing Room database: lizard_lens.db")
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "lizard_lens.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideDetectionDao(database: AppDatabase): DetectionDao {
        return database.detectionDao()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object InferenceModule {

    @Provides
    @Singleton
    fun provideInferenceEngine(@ApplicationContext context: Context): InferenceEngine {
        AppLogger.i("Creating TFLite inference engine")
        return TfliteInferenceEngine.create(context, InferenceConfig())
    }
}
