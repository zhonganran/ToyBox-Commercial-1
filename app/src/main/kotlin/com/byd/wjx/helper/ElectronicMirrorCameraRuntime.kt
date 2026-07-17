package com.byd.wjx.helper

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.Surface
import java.lang.reflect.Method

/**
 * Shell-process owner of the single APA camera instance used by the electronic mirror.
 *
 * All vendor classes are reached reflectively because they live in bmmcamera.jar / BYD framework
 * jars on the vehicle, not in the normal Android SDK. The helper's app_process classpath already
 * includes bmmcamera.jar, so frames can be sent straight into the app-created Surface without a
 * JPEG encoder, socket, or per-frame allocation.
 */
internal class ElectronicMirrorCameraRuntime(
    private val systemContext: Context?,
) {
    private val lock = Any()

    private var camera: Any? = null
    private var previewSurface: Surface? = null
    private var panoramaDevice: Any? = null
    private var setPanoFive: Method? = null
    private var setPanoThree: Method? = null
    private var currentViewpoint: Int = VIEW_NONE

    fun start(surface: Surface, viewpoint: Int, fps: Int): Int = synchronized(lock) {
        if (!surface.isValid) {
            surface.release()
            return@synchronized STATUS_INVALID_SURFACE
        }
        if (viewParams(viewpoint) == null) {
            surface.release()
            return@synchronized STATUS_INVALID_VIEWPOINT
        }

        stopLocked()
        // The Surface reconstructed from the Binder Parcel is owned by this runtime from here on;
        // assigning it before reflection guarantees every early failure releases it via stopLocked.
        previewSurface = surface
        try {
            val cameraClass = Class.forName(AVM_CAMERA_CLASS)
            val cameraId = resolveApaCameraId()
            val opened = openCamera(cameraClass, cameraId)
            if (opened == null) {
                stopLocked()
                return@synchronized fail(STATUS_OPEN_FAILED, "AVMCamera open paths failed cameraId=$cameraId")
            }

            camera = opened

            val setOk = invokeBoolean(opened, "setPreviewSurface", surface, 0)
            // BYD builds differ: some accept setPreviewSurface, others only start producing after
            // addPreviewSurface. Calling both mirrors the validated L1Mini APA implementation.
            val addOk = invokeBoolean(opened, "addPreviewSurface", surface, 0)
            if (!setOk && !addOk) {
                stopLocked()
                return@synchronized fail(STATUS_SURFACE_FAILED, "preview Surface rejected")
            }

            invokeBoolean(opened, "setCameraFps", fps.coerceIn(15, 30))
            if (!invokeBoolean(opened, "startPreview")) {
                stopLocked()
                return@synchronized fail(STATUS_PREVIEW_FAILED, "startPreview returned false")
            }
            val viewStatus = switchViewLocked(viewpoint)
            if (viewStatus != STATUS_OK) {
                stopLocked()
                return@synchronized viewStatus
            }
            helperLog(
                TAG,
                "started cameraId=$cameraId viewpoint=$viewpoint fps=${fps.coerceIn(15, 30)} set=$setOk add=$addOk",
            )
            STATUS_OK
        } catch (t: Throwable) {
            stopLocked()
            fail(STATUS_OPEN_FAILED, "start failed: ${summary(t)}")
        }
    }

    fun switchView(viewpoint: Int): Int = synchronized(lock) {
        if (camera == null) return@synchronized STATUS_NOT_ACTIVE
        switchViewLocked(viewpoint)
    }

    fun stop(): Int = synchronized(lock) {
        stopLocked()
        STATUS_OK
    }

    fun isActive(): Boolean = synchronized(lock) { camera != null }

    fun activeViewpoint(): Int = synchronized(lock) { currentViewpoint }

    private fun switchViewLocked(viewpoint: Int): Int {
        if (currentViewpoint == viewpoint) return STATUS_OK
        val params = viewParams(viewpoint) ?: return STATUS_INVALID_VIEWPOINT
        if (!ensurePanoramaDevice()) {
            return fail(STATUS_PANORAMA_FAILED, "BYDAutoPanoramaDevice unavailable")
        }
        return try {
            val result = when {
                setPanoFive != null -> setPanoFive?.invoke(
                    panoramaDevice,
                    params[0],
                    params[1],
                    params[2],
                    params[3],
                    params[4],
                )
                setPanoThree != null -> setPanoThree?.invoke(
                    panoramaDevice,
                    params[0],
                    params[1],
                    params[2],
                )
                else -> null
            }
            currentViewpoint = viewpoint
            helperLog(TAG, "viewpoint=$viewpoint params=${params.joinToString()} result=$result")
            STATUS_OK
        } catch (t: Throwable) {
            fail(STATUS_PANORAMA_FAILED, "setPanoParams failed: ${summary(t)}")
        }
    }

    private fun stopLocked() {
        val activeCamera = camera
        val surface = previewSurface
        camera = null
        previewSurface = null
        currentViewpoint = VIEW_NONE
        if (activeCamera != null) {
            runCatching { invokeBoolean(activeCamera, "stopPreview") }
            if (surface != null) {
                runCatching { invokeBoolean(activeCamera, "rmPreviewSurface", surface, 0) }
            }
            runCatching { activeCamera.javaClass.getMethod("close").invoke(activeCamera) }
            helperLog(TAG, "stopped")
        }
        runCatching { surface?.release() }
    }

    private fun resolveApaCameraId(): Int {
        return runCatching {
            val info = Class.forName(BMM_CAMERA_INFO_CLASS)
            val getCameraId = info.getMethod("getCameraId", String::class.java)
            listOf("apa", "byd_apa")
                .firstNotNullOfOrNull { tag ->
                    (getCameraId.invoke(null, tag) as? Number)?.toInt()?.takeIf { it >= 0 }
                }
        }.getOrNull() ?: DEFAULT_APA_CAMERA_ID
    }

    /**
     * Prefer the public factory. If the vehicle's cam_sort property is unavailable, that factory
     * can reject a valid fallback id before touching the HAL; the private constructor + private
     * open path mirrors what L1Mini's reflectLoad_5 loader does on those builds.
     */
    private fun openCamera(cameraClass: Class<*>, cameraId: Int): Any? {
        val publicCamera = runCatching {
            cameraClass.getMethod("open", Int::class.javaPrimitiveType).invoke(null, cameraId)
        }.getOrNull()
        if (publicCamera != null) return publicCamera

        return runCatching {
            val constructor = cameraClass.getDeclaredConstructor(Int::class.javaPrimitiveType).apply {
                isAccessible = true
            }
            val candidate = constructor.newInstance(cameraId)
            val opened = cameraClass.getDeclaredMethod("open").apply { isAccessible = true }
                .invoke(candidate) as? Boolean ?: false
            if (opened) {
                helperLog(TAG, "public open rejected cameraId=$cameraId; private fallback succeeded")
                candidate
            } else {
                runCatching { cameraClass.getMethod("close").invoke(candidate) }
                null
            }
        }.onFailure {
            helperLog(TAG, "private camera open failed: ${summary(it)}")
        }.getOrNull()
    }

    private fun ensurePanoramaDevice(): Boolean {
        if (panoramaDevice != null && (setPanoFive != null || setPanoThree != null)) return true
        val context = systemContext ?: return false
        return try {
            val clazz = Class.forName(PANORAMA_DEVICE_CLASS)
            val bypassContext = PermissionBypassContext(context)
            val instance = runCatching {
                clazz.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
                    .newInstance(bypassContext)
            }.getOrNull() ?: runCatching {
                clazz.methods.firstOrNull {
                    it.name == "getInstance" && it.parameterTypes.contentEquals(arrayOf(Context::class.java))
                }?.invoke(null, bypassContext)
            }.getOrNull() ?: runCatching {
                clazz.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            }.getOrNull()
            if (instance == null) return false

            panoramaDevice = instance
            setPanoFive = findMethod(clazz, "setPanoParams", 5)
            setPanoThree = findMethod(clazz, "setPanoParams", 3)
            helperLog(
                TAG,
                "panorama loaded class=${instance.javaClass.name} five=${setPanoFive != null} three=${setPanoThree != null}",
            )
            setPanoFive != null || setPanoThree != null
        } catch (t: Throwable) {
            helperLog(TAG, "panorama load failed: ${summary(t)}")
            false
        }
    }

    private fun findMethod(clazz: Class<*>, name: String, parameterCount: Int): Method? =
        (clazz.methods.asSequence() + clazz.declaredMethods.asSequence())
            .firstOrNull { method ->
                method.name == name &&
                    method.parameterTypes.size == parameterCount &&
                    method.parameterTypes.all { it == Int::class.javaPrimitiveType }
            }
            ?.apply { isAccessible = true }

    private fun invokeBoolean(target: Any, name: String, vararg args: Any): Boolean {
        val method = target.javaClass.methods.firstOrNull {
            it.name == name && it.parameterTypes.size == args.size
        } ?: return false
        val result = method.invoke(target, *args)
        return result !is Boolean || result
    }

    private fun fail(status: Int, message: String): Int {
        helperLog(TAG, message)
        return status
    }

    private fun summary(t: Throwable): String {
        val root = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
        return "${root.javaClass.simpleName}:${root.message.orEmpty()}"
    }

    private fun viewParams(viewpoint: Int): IntArray? = when (viewpoint) {
        2000 -> intArrayOf(3, 0, 1, 0, 0) // rear
        2002 -> intArrayOf(4, 0, 1, 0, 0) // left side/rear
        2003 -> intArrayOf(5, 0, 1, 0, 0) // right side/rear
        else -> null
    }

    private class PermissionBypassContext(base: Context) : ContextWrapper(base) {
        override fun checkCallingOrSelfPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED
        override fun checkCallingPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int = PackageManager.PERMISSION_GRANTED
        override fun enforceCallingOrSelfPermission(permission: String, message: String?) = Unit
        override fun enforceCallingPermission(permission: String, message: String?) = Unit
        override fun enforcePermission(permission: String, pid: Int, uid: Int, message: String?) = Unit
    }

    companion object {
        const val STATUS_OK = 0
        const val STATUS_INVALID_SURFACE = -2
        const val STATUS_INVALID_VIEWPOINT = -3
        const val STATUS_OPEN_FAILED = -4
        const val STATUS_SURFACE_FAILED = -5
        const val STATUS_PREVIEW_FAILED = -6
        const val STATUS_PANORAMA_FAILED = -7
        const val STATUS_NOT_ACTIVE = -8

        private const val TAG = "ElectronicMirrorCamera"
        private const val AVM_CAMERA_CLASS = "android.hardware.AVMCamera"
        private const val BMM_CAMERA_INFO_CLASS = "android.hardware.BmmCameraInfo"
        private const val PANORAMA_DEVICE_CLASS = "android.hardware.bydauto.panorama.BYDAutoPanoramaDevice"
        private const val DEFAULT_APA_CAMERA_ID = 3
        private const val VIEW_NONE = -1
    }
}
