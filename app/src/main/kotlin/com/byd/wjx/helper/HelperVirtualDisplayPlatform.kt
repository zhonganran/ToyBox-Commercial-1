package com.byd.wjx.helper

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Surface
import java.util.concurrent.ConcurrentHashMap

internal class OwnedResourceRegistry<K : Any, V : Any>(
    private val releaseResource: (V) -> Unit,
) {
    private val resources = ConcurrentHashMap<K, V>()

    fun register(key: K, value: V) {
        resources[key] = value
    }

    fun release(key: K): Boolean {
        val value = resources.remove(key) ?: return false
        releaseResource(value)
        return true
    }
}

internal class HelperVirtualDisplayRegistry(
    private val resources: OwnedResourceRegistry<Int, VirtualDisplay> =
        OwnedResourceRegistry { it.release() },
) {
    fun register(displayId: Int, display: VirtualDisplay) {
        resources.register(displayId, display)
    }

    fun release(displayId: Int): Boolean = resources.release(displayId)
}

/** Creates a shell-owned virtual display and records it only after obtaining a valid display id. */
internal fun createVirtualDisplay(
    ctx: Context,
    store: HelperVirtualDisplayRegistry,
    name: String,
    width: Int,
    height: Int,
    density: Int,
    surface: Surface,
    flags: Int,
): Int {
    val shellContext = ctx.createPackageContext("com.android.shell", 0)
    val displayManager = shellContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val display = displayManager.createVirtualDisplay(
        name,
        width,
        height,
        density,
        surface,
        flags,
    ) ?: return -1
    val displayId = display.display?.displayId ?: -1
    if (displayId <= 0) {
        display.release()
        return -1
    }
    store.register(displayId, display)
    return displayId
}
