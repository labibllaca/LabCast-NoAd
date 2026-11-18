package com.example.asr

import android.content.Context

sealed class AsrModelState {
    object Disabled : AsrModelState()
    object NotDownloaded : AsrModelState()
    data class Downloading(val progress: Float) : AsrModelState()
    object Ready : AsrModelState()
    data class Failed(val message: String) : AsrModelState()
}

class AsrPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("asr_prefs", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) {
            prefs.edit().putBoolean("enabled", value).apply()
        }

    var modelVersion: String?
        get() = prefs.getString("model_version", null)
        set(value) {
            prefs.edit().putString("model_version", value).apply()
        }

    fun isModelInstalled(): Boolean = modelVersion == AsrModelRegistry.modelDirName
}
