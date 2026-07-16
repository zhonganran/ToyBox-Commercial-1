package com.byd.wjx

import android.app.Application
import org.lsposed.hiddenapibypass.HiddenApiBypass
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.byd.wjx.data.local.DataThinningWorker
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.service.BootState
import com.byd.wjx.service.ProcessRevivalReceiver
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class SuperToyBoxApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var activationManager: com.byd.wjx.activation.ActivationManager
    @Volatile private var unlockedRuntimeInitialized = false

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        installUnifiedCrashLogger()
        WjxFileLogger.i("SuperToyBoxApp", "application onCreate package=$packageName version=${BuildConfig.VERSION_NAME}/${BuildConfig.VERSION_CODE}")
        // Allow in-process ServiceManager.getService() on Android 9+ to reach the helper
        // binder service without hidden-API restrictions (UnsatisfiedLinkError / NoSuchMethodError).
        // Guarded: under JVM/Robolectric unit tests the HiddenApiBypass static initializer throws
        // (no Android ART runtime), which would otherwise crash onCreate for every test that
        // instantiates this Application. Swallow there; on a real device the call succeeds.
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/os/ServiceManager;",
                "Landroid/os/SystemProperties;",
            )
        }.onFailure {
            WjxFileLogger.w("SuperToyBoxApp", "hidden-api exemption unavailable", it)
        }
        ProcessRevivalReceiver.schedule(this)
        if (BootState.isUserUnlocked(this)) {
            ensureUnlockedRuntime()
        } else {
            WjxFileLogger.i("SuperToyBoxApp", "direct boot: credential runtime deferred")
        }
    }

    @Synchronized
    fun ensureUnlockedRuntime() {
        if (unlockedRuntimeInitialized || !BootState.isUserUnlocked(this)) return
        activationManager.ensureStarted()
        scheduleDataThinning()
        unlockedRuntimeInitialized = true
        WjxFileLogger.i("SuperToyBoxApp", "credential runtime initialized")
    }

    fun isUnlockedRuntimeInitialized(): Boolean = unlockedRuntimeInitialized

    private fun installUnifiedCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            WjxFileLogger.e(
                "Crash",
                "uncaught exception thread=${thread.name} id=${thread.id}",
                throwable,
            )
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        /** 高德地图道路瓦片 — 中国大陆可用，免 API Key。
         *  查询参数风格 (?x=&y=&z=) 需要重写 getTileURLString。 */
        val AmapTileSource = object : OnlineTileSourceBase(
            "Amap", 0, 18, 256, ".png",
            arrayOf(
                "https://webrd01.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x=%d&y=%d&z=%d",
                "https://webrd02.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x=%d&y=%d&z=%d",
                "https://webrd03.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x=%d&y=%d&z=%d",
                "https://webrd04.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x=%d&y=%d&z=%d",
            ),
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                return String.format(
                    baseUrl,
                    MapTileIndex.getX(pMapTileIndex),
                    MapTileIndex.getY(pMapTileIndex),
                    MapTileIndex.getZoom(pMapTileIndex),
                )
            }
        }

        val OsmTileSource = TileSourceFactory.MAPNIK
    }

    private fun scheduleDataThinning() {
        val request = PeriodicWorkRequestBuilder<DataThinningWorker>(
            1, TimeUnit.DAYS
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DataThinningWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
