package com.lizardlens.core.logging

import android.util.Log
import timber.log.Timber

class LizardLensTree(private val isDebug: Boolean) : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (!isDebug && priority < Log.WARN) return

        val logTag = if (isDebug) {
            val caller = Throwable().stackTrace.firstOrNull {
                !it.className.startsWith("timber.log") &&
                    !it.className.startsWith("com.lizardlens.core.logging")
            }
            val simpleTag = caller?.let {
                it.className.substringAfterLast('.').substringBefore('$')
            } ?: "Unknown"
            "LL/$simpleTag"
        } else {
            tag ?: "LizardLens"
        }

        val logMessage = if (t != null) {
            "$message\n${Log.getStackTraceString(t)}"
        } else {
            message
        }

        Log.println(priority, logTag, logMessage)
    }
}
