package com.lizardlens

import android.app.Application
import com.lizardlens.core.logging.AppLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LizardLensApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            AppLogger.plantDebugTree()
        } else {
            AppLogger.plantReleaseTree()
        }
        AppLogger.i("LizardLens application started")
    }
}
