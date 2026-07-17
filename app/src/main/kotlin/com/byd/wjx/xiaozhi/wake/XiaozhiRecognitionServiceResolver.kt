package com.byd.wjx.xiaozhi.wake

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.speech.RecognitionService
import com.byd.wjx.log.WjxFileLogger

internal data class XiaozhiRecognitionService(
    val component: ComponentName,
    val engineLabel: String,
)

/**
 * Finds an explicit RecognitionService when the ROM has not configured a system default.
 *
 * Several vehicle ROMs ship their own recognizer but make SpeechRecognizer.isRecognitionAvailable
 * return false. Binding the exported RecognitionService explicitly lets those ROMs work without
 * bundling a large offline speech model into the app.
 */
internal object XiaozhiRecognitionServiceResolver {
    fun resolve(context: Context): XiaozhiRecognitionService? {
        val services = runCatching { query(context.packageManager) }
            .onFailure { error ->
                WjxFileLogger.w(TAG, "recognition service query unavailable: ${error.message}")
            }
            .getOrDefault(emptyList())
        val candidates = services
            .asSequence()
            .mapNotNull(ResolveInfo::serviceInfo)
            .filter { service ->
                service.enabled && service.exported && service.applicationInfo.enabled
            }
            .sortedWith(
                compareBy(
                    { service -> recognitionServiceRank(service.packageName, service.name) },
                    { service -> service.packageName },
                    { service -> service.name },
                ),
            )
            .toList()
        val selected = candidates.firstOrNull() ?: return null
        val component = ComponentName(selected.packageName, selected.name)
        val label = if (selected.packageName.contains("byd", ignoreCase = true)) {
            "车机语音识别"
        } else {
            "系统语音识别"
        }
        WjxFileLogger.i(
            TAG,
            "explicit recognition service selected=${component.flattenToShortString()} candidates=${candidates.size}",
        )
        return XiaozhiRecognitionService(component, label)
    }

    @Suppress("DEPRECATION")
    private fun query(packageManager: PackageManager): List<ResolveInfo> {
        val intent = Intent(RecognitionService.SERVICE_INTERFACE)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL)
        }
    }

    private const val TAG = "XiaozhiWake"
}

internal fun recognitionServiceRank(packageName: String, className: String): Int {
    val identity = "$packageName/$className".lowercase()
    return when {
        "byd" in identity || "autovoice" in identity -> 0
        "google" in identity -> 1
        "speech" in identity || "recogn" in identity || "voice" in identity -> 2
        else -> 3
    }
}
