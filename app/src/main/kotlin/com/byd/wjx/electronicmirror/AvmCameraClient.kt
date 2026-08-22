package com.byd.wjx.electronicmirror

import android.util.Log
import android.view.Surface
import com.byd.wjx.log.WjxFileLogger
import java.lang.reflect.Proxy

/** Reflection bridge to BYD's hidden android.hardware.AVMCamera implementation. */
internal class AvmCameraClient {
    private var camera: Any? = null
    private val bound = linkedMapOf<Int, Surface>()

    var cameraId: Int = -1
        private set

    fun discover(): Boolean = try {
        val info = Class.forName("android.hardware.BmmCameraInfo")
        log("camera count=${info.getMethod("getCameraNumbers").invoke(null)}")
        for (tag in CAMERA_TAGS) {
            val id = (info.getMethod("getCameraId", String::class.java).invoke(null, tag) as Number).toInt()
            log("camera tag=$tag id=$id")
            if (id >= 0 && cameraId < 0) cameraId = id
        }
        cameraId >= 0
    } catch (error: Throwable) {
        log("discover failed: ${rootMessage(error)}")
        false
    }

    @Synchronized
    fun openWarm(surfaces: Map<Int, Surface>): Boolean {
        if (cameraId < 0 || surfaces.isEmpty()) return false
        if (camera != null) close()
        return try {
            val avm = Class.forName("android.hardware.AVMCamera")
            var opened = avm.getMethod("open", Int::class.java).invoke(null, cameraId)
            if (opened == null) opened = openWithConstructor(avm)
            checkNotNull(opened) { "AVMCamera.open returned null" }
            camera = opened
            bound.clear()
            bound.putAll(surfaces)

            val callbackType = Class.forName("android.hardware.AVMCamera\$IEventCallback")
            val callback = Proxy.newProxyInstance(
                callbackType.classLoader,
                arrayOf(callbackType),
            ) { proxy, method, args ->
                when {
                    method.declaringClass == Any::class.java && method.name == "toString" -> "ElectronicMirrorCallback"
                    method.declaringClass == Any::class.java && method.name == "hashCode" -> System.identityHashCode(proxy)
                    method.declaringClass == Any::class.java && method.name == "equals" -> proxy === args?.getOrNull(0)
                    method.name == "onEvent" -> {
                        log("event ${args?.joinToString().orEmpty()}")
                        null
                    }
                    else -> null
                }
            }
            avm.getMethod("setEventCallback", callbackType).invoke(opened, callback)

            val addPreview = avm.getMethod("addPreviewSurface", Surface::class.java, Int::class.java)
            val setPreview = avm.getMethod("setPreviewSurface", Surface::class.java, Int::class.java)
            var allBound = true
            for ((index, surface) in surfaces) {
                val added = addPreview.invoke(opened, surface, index)
                val set = setPreview.invoke(opened, surface, index)
                log("bind index=$index add=$added set=$set")
                if (added == false || set == false) allBound = false
            }
            val started = avm.getMethod("startPreview").invoke(opened)
            log("startPreview=$started indexes=${surfaces.keys.joinToString()}")
            if (!allBound || started == false) {
                close()
                false
            } else {
                true
            }
        } catch (error: Throwable) {
            log("open failed: ${rootMessage(error)}")
            close()
            false
        }
    }

    @Synchronized
    fun close() {
        val opened = camera ?: return
        val avm = opened.javaClass
        var firstError: Throwable? = null
        var failed = false
        for (attempt in 1..2) {
            failed = false
            fun step(name: String, body: () -> Unit) {
                runCatching(body).onFailure {
                    failed = true
                    if (firstError == null) firstError = it
                    Log.w(TAG, "$name failed on attempt $attempt", it)
                }
            }
            val removePreview = runCatching {
                avm.getMethod("rmPreviewSurface", Surface::class.java, Int::class.java)
            }.onFailure {
                failed = true
                if (firstError == null) firstError = it
            }.getOrNull()
            for ((index, surface) in bound) {
                step("remove index=$index") { removePreview?.invoke(opened, surface, index) }
            }
            step("stop preview") { avm.getMethod("stopPreview").invoke(opened) }
            step("close camera") { avm.getMethod("close").invoke(opened) }
            if (!failed) break
        }
        camera = null
        bound.clear()
        log(
            when {
                firstError == null -> "closed cleanly"
                failed -> "close failed after retry: ${rootMessage(firstError!!)}"
                else -> "closed after retry: ${rootMessage(firstError!!)}"
            },
        )
    }

    private fun openWithConstructor(avm: Class<*>): Any? {
        val instance = avm.getDeclaredConstructor(Int::class.java).apply { isAccessible = true }
            .newInstance(cameraId)
        val open = avm.getDeclaredMethod("open").apply { isAccessible = true }
        return if (open.invoke(instance) == true) instance else null
    }

    private fun rootMessage(error: Throwable): String {
        var current = error
        while (current.cause != null && current.cause !== current) current = current.cause!!
        return "${current.javaClass.simpleName}: ${current.message}"
    }

    private fun log(message: String) = WjxFileLogger.i(TAG, message)

    private companion object {
        const val TAG = "ElectronicMirrorCamera"
        val CAMERA_TAGS = listOf("pano_h", "pano_l", "apa", "byd_apa")
    }
}
