package com.byd.wjx.data.foreground

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.byd.wjx.log.WjxFileLogger
import java.lang.ref.WeakReference

/**
 * Lightweight process bridge for the accessibility foreground signals already available on the
 * car. AutoTark uses the active accessibility root/windows as its fastest foreground source; the
 * task stack and UsageEvents remain fallbacks in [ForegroundAppMonitor].
 */
object AccessibilityForegroundBridge {
    private val lock = Any()
    private val services = mutableListOf<WeakReference<AccessibilityService>>()

    @Volatile
    private var latestEvent: ForegroundApp? = null
    @Volatile
    private var rescanListener: (() -> Unit)? = null

    fun setRescanListener(listener: (() -> Unit)?) {
        rescanListener = listener
    }

    fun onServiceConnected(service: AccessibilityService) {
        synchronized(lock) {
            services.removeAll { it.get() == null || it.get() === service }
            services += WeakReference(service)
        }
        snapshotLive()?.let(::record)
    }

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString()?.trim().orEmpty()
        if (packageName.isBlank()) return
        record(
            ForegroundApp(
                packageName = packageName,
                className = event?.className?.toString()?.trim(),
                observedAtMs = event?.eventTime?.takeIf { it > 0L } ?: System.currentTimeMillis(),
                source = ForegroundAppSource.ACCESSIBILITY_EVENT,
            ),
        )
        rescanListener?.invoke()
    }

    fun onServiceDisconnected(service: AccessibilityService) {
        synchronized(lock) {
            services.removeAll { it.get() == null || it.get() === service }
        }
    }

    fun snapshot(nowMs: Long): ForegroundApp? {
        snapshotLive(nowMs)?.let {
            record(it)
            return it
        }
        return latestEvent?.takeIf { nowMs - it.observedAtMs <= EVENT_MAX_AGE_MS }
    }

    private fun snapshotLive(nowMs: Long = System.currentTimeMillis()): ForegroundApp? {
        val activeServices = synchronized(lock) {
            services.mapNotNull(WeakReference<AccessibilityService>::get).also {
                services.removeAll { reference -> reference.get() == null }
            }
        }
        activeServices.asReversed().forEach { service ->
            readNode(service.rootInActiveWindow, nowMs)?.let { return it }
            val windows = runCatching { service.windows }.getOrNull().orEmpty()
            val orderedWindows = windows.filter { it.isActive || it.isFocused } +
                windows.filterNot { it.isActive || it.isFocused }
            orderedWindows.forEach { window ->
                    readNode(runCatching { window.root }.getOrNull(), nowMs)?.let { return it }
                }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun readNode(node: AccessibilityNodeInfo?, nowMs: Long): ForegroundApp? {
        if (node == null) return null
        return try {
            val packageName = node.packageName?.toString()?.trim().orEmpty()
            if (packageName.isBlank()) {
                null
            } else {
                ForegroundApp(
                    packageName = packageName,
                    className = node.className?.toString()?.trim(),
                    observedAtMs = nowMs,
                    source = ForegroundAppSource.ACCESSIBILITY_LIVE,
                )
            }
        } catch (error: Exception) {
            WjxFileLogger.w(TAG, "read accessibility foreground failed: ${error.message}")
            null
        } finally {
            runCatching { node.recycle() }
        }
    }

    private fun record(value: ForegroundApp) {
        latestEvent = value
    }

    private const val TAG = "ForegroundA11y"
    private const val EVENT_MAX_AGE_MS = 5_000L
}
