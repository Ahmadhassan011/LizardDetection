package com.lizardlens.core.logging

import timber.log.Timber

object AppLogger {

    fun d(message: String, vararg args: Any) = Timber.d(message, *args)

    fun i(message: String, vararg args: Any) = Timber.i(message, *args)

    fun w(message: String, vararg args: Any) = Timber.w(message, *args)

    fun e(message: String, vararg args: Any) = Timber.e(message, *args)

    fun e(throwable: Throwable, message: String, vararg args: Any) =
        Timber.e(throwable, message, *args)

    fun v(message: String, vararg args: Any) = Timber.v(message, *args)

    fun plantDebugTree() {
        Timber.plant(LizardLensTree(isDebug = true))
    }

    fun plantReleaseTree() {
        Timber.plant(LizardLensTree(isDebug = false))
    }
}
