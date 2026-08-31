package com.byd.wjx.camera.blindspot

import android.view.Surface
import com.byd.wjx.log.WjxFileLogger
import java.lang.reflect.Proxy

/** Reflection-only adapter for BYD's hidden AVM camera stack. */
class AvmCameraAdapter {
    private var camera: Any? = null
    private val boundSurfaces = linkedMapOf<Int, Surface>()
    var cameraId: Int = -1
        private set
    var previewWidth: Int = DEFAULT_PREVIEW_WIDTH
        private set
    var previewHeight: Int = DEFAULT_PREVIEW_HEIGHT
        private set

    fun discover(): Boolean = try {
        val info = Class.forName("android.hardware.BmmCameraInfo")
        log("cameraCount=${info.getMethod("getCameraNumbers").invoke(null)}")
        runCatching { log("validTags=${info.getMethod("getValidCameraTag").invoke(null)}") }
        CAMERA_TAGS.forEach { tag ->
            val id = (info.getMethod("getCameraId", String::class.java).invoke(null, tag) as Number).toInt()
            log("tag=$tag id=$id")
            if (cameraId < 0 && id >= 0) {
                cameraId = id
                runCatching {
                    val width = (info.getMethod("getDefaultPreviewWidth", Int::class.java)
                        .invoke(null, id) as Number).toInt()
                    val height = (info.getMethod("getDefaultPreviewHeight", Int::class.java)
                        .invoke(null, id) as Number).toInt()
                    if (width > 0 && height > 0) {
                        previewWidth = width
                        previewHeight = height
                    }
                    log("cameraId=$id preview=${previewWidth}x$previewHeight")
                }
            }
        }
        cameraId >= 0
    } catch (error: Throwable) {
        log("discover failed: ${rootMessage(error)}", error)
        false
    }

    @Synchronized
    fun openWarm(surfaces: Map<Int, Surface>): Boolean {
        if (cameraId < 0 || surfaces.isEmpty()) return false
        if (camera != null) close()
        return try {
            val avmClass = Class.forName("android.hardware.AVMCamera")
            var opened = avmClass.getMethod("open", Int::class.java).invoke(null, cameraId)
            if (opened == null) opened = openWithConstructor(avmClass)
            opened = checkNotNull(opened) { "AVMCamera.open returned null" }
            camera = opened
            boundSurfaces.clear()
            boundSurfaces.putAll(surfaces)

            val callbackType = Class.forName("android.hardware.AVMCamera\$IEventCallback")
            val callback = Proxy.newProxyInstance(
                callbackType.classLoader,
                arrayOf(callbackType),
            ) { proxy, method, args ->
                when {
                    method.declaringClass == Any::class.java && method.name == "toString" -> "WjxAvmCallback"
                    method.declaringClass == Any::class.java && method.name == "hashCode" ->
                        System.identityHashCode(proxy)
                    method.declaringClass == Any::class.java && method.name == "equals" ->
                        proxy === args?.getOrNull(0)
                    method.name == "onEvent" -> {
                        log("event=${args?.joinToString()}")
                        null
                    }
                    else -> null
                }
            }
            avmClass.getMethod("setEventCallback", callbackType).invoke(opened, callback)
            val addPreview = avmClass.getMethod(
                "addPreviewSurface",
                Surface::class.java,
                Int::class.java,
            )
            val setPreview = avmClass.getMethod(
                "setPreviewSurface",
                Surface::class.java,
                Int::class.java,
            )
            var accepted = true
            surfaces.forEach { (index, surface) ->
                val added = addPreview.invoke(opened, surface, index)
                val set = setPreview.invoke(opened, surface, index)
                log("bind index=$index add=$added set=$set")
                if (added == false || set == false) accepted = false
            }
            val started = avmClass.getMethod("startPreview").invoke(opened)
            log("startPreview=$started indexes=${surfaces.keys.joinToString()}")
            if (!accepted || started == false) {
                close()
                false
            } else {
                true
            }
        } catch (error: Throwable) {
            log("open failed: ${rootMessage(error)}", error)
            close()
            false
        }
    }

    @Synchronized
    fun close() {
        val opened = camera ?: return
        val avmClass = opened.javaClass
        var firstError: Throwable? = null
        for (attempt in 1..2) {
            var failed = false
            val removePreview = runCatching {
                avmClass.getMethod("rmPreviewSurface", Surface::class.java, Int::class.java)
            }.getOrNull()
            fun step(name: String, operation: () -> Unit) {
                runCatching(operation).onFailure {
                    failed = true
                    if (firstError == null) firstError = it
                    log("close $name failed attempt=$attempt: ${rootMessage(it)}", it)
                }
            }
            fun booleanStep(name: String, operation: () -> Any?) {
                step(name) {
                    check(operation() != false) { "$name returned false" }
                }
            }
            boundSurfaces.forEach { (index, surface) ->
                booleanStep("surface[$index]") { removePreview?.invoke(opened, surface, index) }
            }
            booleanStep("stopPreview") { avmClass.getMethod("stopPreview").invoke(opened) }
            booleanStep("close") { avmClass.getMethod("close").invoke(opened) }
            if (!failed) break
        }
        camera = null
        boundSurfaces.clear()
        log(if (firstError == null) "closed" else "closed with retry: ${rootMessage(firstError!!)}")
    }

    private fun openWithConstructor(avmClass: Class<*>): Any? {
        val instance = avmClass.getDeclaredConstructor(Int::class.java).apply { isAccessible = true }
            .newInstance(cameraId)
        val open = avmClass.getDeclaredMethod("open").apply { isAccessible = true }
        return if (open.invoke(instance) == true) instance else null
    }

    private fun log(message: String, error: Throwable? = null) {
        if (error == null) WjxFileLogger.i(TAG, message) else WjxFileLogger.w(TAG, message, error)
    }

    private fun rootMessage(error: Throwable): String {
        var current = error
        while (current.cause != null && current.cause !== current) current = current.cause!!
        return "${current.javaClass.simpleName}: ${current.message}"
    }

    companion object {
        private const val TAG = "BlindSpotCamera"
        private val CAMERA_TAGS = listOf("pano_h", "pano_l", "apa", "byd_apa")
        const val DEFAULT_PREVIEW_WIDTH = 1920
        const val DEFAULT_PREVIEW_HEIGHT = 1300
    }
}
