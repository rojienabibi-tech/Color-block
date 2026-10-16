package com.example

import android.app.Application
import android.util.Log
import com.example.ads.UnityAdsManager
import java.io.File

/**
 * Application class for COLOR BLOCK.
 * Initializes Unity Ads early in the app lifecycle and ensures WebView cache directories exist.
 */
class ColorBlockApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Pre-create WebView cache directories to prevent Chromium simple_file_enumerator ENOENT errors
        try {
            val baseCache = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache")
            File(baseCache, "js").mkdirs()
            File(baseCache, "wasm").mkdirs()
        } catch (e: Exception) {
            Log.w("ColorBlockApp", "WebView cache dir init notice: ${e.message}")
        }

        Log.i("ColorBlockApp", "ColorBlockApplication started. Initializing Unity Ads SDK...")
        UnityAdsManager.initialize(this)
    }
}
