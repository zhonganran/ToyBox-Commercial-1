package com.byd.wjx.ui.map

import android.content.Context
import org.osmdroid.config.Configuration as OsmdroidConfig
import java.io.File

/**
 * Lazily initializes osmdroid when a map is actually opened.
 *
 * Most background/vehicle-runtime sessions never render a map. Keeping this work out of
 * Application.onCreate avoids loading map preferences and touching the tile-cache directories on
 * every cold start while preserving one process-wide osmdroid configuration.
 */
internal object OsmdroidRuntime {
    @Volatile private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            OsmdroidConfig.getInstance().apply {
                userAgentValue = appContext.packageName
                val basePath = File(appContext.filesDir, "osmdroid")
                basePath.mkdirs()
                osmdroidBasePath = basePath
                val tilePath = File(basePath, "tiles")
                tilePath.mkdirs()
                osmdroidTileCache = tilePath
                tileFileSystemCacheMaxBytes = 100L * 1024 * 1024
                tileFileSystemCacheTrimBytes = 80L * 1024 * 1024
                load(
                    appContext,
                    appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                )
            }
            initialized = true
        }
    }

    private const val PREFS_NAME = "osmdroid"
}
