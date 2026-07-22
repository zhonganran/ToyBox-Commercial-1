package com.byd.wjx.helper

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.Surface
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Shell-process owner of the single APA/panorama camera instance used by the electronic mirror.
 *
 * All vendor classes are reached reflectively because they live in bmmcamera.jar / BYD framework
 * jars on the vehicle, not in the normal Android SDK. Camera identity and Surface output index are
 * separate inputs; the app selects a panorama tile with TextureView.Matrix without copying frames.
 */
@SuppressLint("PrivateApi") // BYD camera classes are vendor framework APIs on the vehicle ROM.
internal class ElectronicMirrorCameraRuntime(
    private val systemContext: Context?,
) {
    private val lock = Any()

    private var camera: Any? = null
    private var previewSurface: Surface? = null
    private var currentSurfaceIndex: Int = SURFACE_NONE
    private var cameraApi: CameraApi? = null
    private var fpsSetterSupported: Boolean? = null

    fun start(
        surface: Surface,
        cameraTag: String,
        surfaceIndex: Int,
        tileCount: Int,
        bufferWidth: Int,
        bufferHeight: Int,
        fps: Int,
    ): Int = synchronized(lock) {
        if (!surface.isValid) {
            surface.release()
            return@synchronized STATUS_INVALID_SURFACE
        }
        val safeCameraTag = cameraTag.trim().lowercase()
        val requestValid = safeCameraTag == PANORAMA_CAMERA_TAG &&
            surfaceIndex in MIN_SURFACE_INDEX..MAX_SURFACE_INDEX &&
            tileCount == PANORAMA_TILE_COUNT &&
            bufferWidth in MIN_BUFFER_WIDTH..MAX_BUFFER_WIDTH &&
            bufferHeight in MIN_BUFFER_HEIGHT..MAX_BUFFER_HEIGHT
        if (!requestValid) {
            surface.release()
            return@synchronized fail(
                STATUS_INVALID_REQUEST,
                "invalid request cameraTag=$cameraTag surfaceIndex=$surfaceIndex tiles=$tileCount " +
                    "buffer=${bufferWidth}x$bufferHeight",
            )
        }

        stopLocked()
        // The Surface reconstructed from the Binder Parcel is owned by this runtime from here on;
        // assigning it before reflection guarantees every early failure releases it via stopLocked.
        previewSurface = surface
        try {
            val api = resolveCameraApi()
            // Do not silently fall back to pano_l/APA side feeds: those are the round/door-facing
            // images that caused the incorrect mirror view. pano_h plus the validated numeric
            // fallback is the only producer route for this feature.
            val cameraCandidates = resolveCameraCandidates(listOf(safeCameraTag))
            val fpsValue = fps.coerceIn(15, 30)
            var startedCamera: StartedCamera? = null
            for (candidate in cameraCandidates) {
                helperLog(
                    TAG,
                    "opening cameraId=${candidate.id} source=${candidate.source}",
                )
                val opened = openCamera(api, candidate.id) ?: continue
                val bindingMethod = bindPreviewSurface(opened, surface, surfaceIndex)
                if (bindingMethod == null) {
                    helperLog(
                        TAG,
                        "cameraId=${candidate.id} rejected Surface index=$surfaceIndex; trying next",
                    )
                    closeCameraAttempt(opened, surface, surfaceIndex, surfaceBound = false)
                    continue
                }
                val fpsApplied = applyCameraFps(opened, fpsValue)
                if (!invokeBoolean(opened, "startPreview")) {
                    helperLog(
                        TAG,
                        "cameraId=${candidate.id} startPreview rejected; trying next",
                    )
                    closeCameraAttempt(opened, surface, surfaceIndex, surfaceBound = true)
                    continue
                }
                startedCamera = StartedCamera(
                    candidate = candidate,
                    camera = opened,
                    bindingMethod = bindingMethod,
                    fpsApplied = fpsApplied,
                )
                break
            }
            if (startedCamera == null) {
                stopLocked()
                return@synchronized fail(
                    STATUS_OPEN_FAILED,
                    "AVMCamera producer paths failed candidates=" +
                        cameraCandidates.joinToString { "${it.id}(${it.source})" },
                )
            }
            camera = startedCamera.camera
            currentSurfaceIndex = surfaceIndex

            helperLog(
                TAG,
                "started cameraId=${startedCamera.candidate.id} " +
                    "source=${startedCamera.candidate.source} " +
                    "cameraTag=$safeCameraTag " +
                    "surfaceIndex=$surfaceIndex tiles=$tileCount " +
                    "binding=${startedCamera.bindingMethod} " +
                    "fps=$fpsValue fpsApplied=${startedCamera.fpsApplied} " +
                    "buffer=${bufferWidth}x$bufferHeight",
            )
            STATUS_OK
        } catch (t: Throwable) {
            stopLocked()
            fail(STATUS_OPEN_FAILED, "start failed: ${summary(t)}")
        }
    }

    fun stop(): Int = synchronized(lock) {
        stopLocked()
        STATUS_OK
    }

    private fun stopLocked() {
        val activeCamera = camera
        val surface = previewSurface
        camera = null
        previewSurface = null
        val activeSurfaceIndex = currentSurfaceIndex
        currentSurfaceIndex = SURFACE_NONE
        if (activeCamera != null) {
            if (surface != null && activeSurfaceIndex >= MIN_SURFACE_INDEX) {
                runCatching {
                    invokeBoolean(activeCamera, "rmPreviewSurface", surface, activeSurfaceIndex)
                }
            }
            runCatching { invokeBoolean(activeCamera, "stopPreview") }
            runCatching { invokeBoolean(activeCamera, "close") }
            helperLog(TAG, "stopped surfaceIndex=$activeSurfaceIndex")
        }
        runCatching { surface?.release() }
    }

    private fun closeCameraAttempt(
        attemptedCamera: Any,
        surface: Surface,
        surfaceIndex: Int,
        surfaceBound: Boolean,
    ) {
        if (surfaceBound) {
            runCatching {
                invokeBoolean(attemptedCamera, "rmPreviewSurface", surface, surfaceIndex)
            }
        }
        runCatching { invokeBoolean(attemptedCamera, "stopPreview") }
        runCatching { invokeBoolean(attemptedCamera, "close") }
    }

    private fun resolveCameraCandidates(tags: List<String>): List<CameraIdCandidate> {
        val candidates = linkedMapOf<Int, CameraIdCandidate>()
        fun addCandidate(id: Int?, source: String) {
            id?.takeIf { it >= 0 }?.let { validId ->
                candidates.putIfAbsent(validId, CameraIdCandidate(validId, source))
            }
        }

        // Vehicle configuration is authoritative. On the tested vehicle BmmCameraInfo reports
        // pano_h as camera 0 (the raw circular fisheye producer), while cam_sort explicitly maps
        // pano_h to camera 1 (the validated rectified horizontal panorama). Read both BYD
        // property spellings before consulting BmmCameraInfo so the raw producer cannot win.
        CAMERA_SORT_PROPERTIES.forEach { propertyName ->
            val cameraSort = readSystemProperty(propertyName)
            tags.forEach { tag ->
                val propertyId = cameraIdFromProperty(cameraSort, tag)
                helperLog(
                    TAG,
                    "camera property name=$propertyName tag=$tag " +
                        "id=${propertyId ?: "unavailable"}",
                )
                addCandidate(propertyId, "property:$propertyName:$tag")
            }
        }

        val info = runCatching { Class.forName(BMM_CAMERA_INFO_CLASS) }.getOrNull()
        val idMethods = info?.let { clazz ->
            allMethods(clazz)
                .filter { method ->
                    method.name in CAMERA_ID_METHOD_NAMES &&
                        method.parameterTypes.contentEquals(arrayOf(String::class.java))
                }
                .distinctBy(::methodSignature)
                .sortedByDescending { Modifier.isStatic(it.modifiers) }
                .toList()
        }.orEmpty()
        val infoInstance = info
            ?.takeIf { idMethods.any { method -> !Modifier.isStatic(method.modifiers) } }
            ?.let(::instantiateCameraInfo)
        tags.forEach { tag ->
            var resolved: Int? = null
            for (method in idMethods) {
                val receiver = if (Modifier.isStatic(method.modifiers)) null else infoInstance ?: continue
                val value = runCatching {
                    method.isAccessible = true
                    (method.invoke(receiver, tag) as? Number)?.toInt()
                }.onFailure {
                    helperLog(TAG, "camera id ${methodSignature(method)} tag=$tag failed: ${summary(it)}")
                }.getOrNull()
                if (value != null && value >= 0) {
                    resolved = value
                    break
                }
            }
            helperLog(TAG, "camera tag=$tag id=${resolved ?: "unavailable"}")
            addCandidate(resolved, "BmmCameraInfo:$tag")
        }
        addCandidate(DEFAULT_PANORAMA_CAMERA_ID, "fallback")
        return candidates.values.toList().also { resolved ->
            helperLog(
                TAG,
                "camera candidates=" + resolved.joinToString { "${it.id}(${it.source})" },
            )
        }
    }

    /**
     * Prefer the public factory. If the vehicle's cam_sort property is unavailable, that factory
     * can reject a valid fallback id before touching the HAL; the private constructor + instance
     * open path keeps those BYD framework variants usable without another video pipeline.
     */
    private fun openCamera(api: CameraApi, cameraId: Int): Any? {
        for (factory in api.staticFactories) {
            val args = defaultArguments(factory.parameterTypes, cameraId) ?: continue
            val opened = runCatching {
                factory.isAccessible = true
                factory.invoke(null, *args)
            }.onFailure {
                helperLog(TAG, "factory ${methodSignature(factory)} failed cameraId=$cameraId: ${summary(it)}")
            }.getOrNull()
            if (opened != null && api.cameraClass.isInstance(opened)) {
                helperLog(TAG, "factory ${methodSignature(factory)} succeeded cameraId=$cameraId")
                return opened
            }
            helperLog(TAG, "factory ${methodSignature(factory)} returned null cameraId=$cameraId")
        }

        val instanceOpen = api.instanceOpen ?: return null
        for (constructor in api.constructors) {
            val args = defaultArguments(constructor.parameterTypes, cameraId) ?: continue
            val candidate = runCatching {
                constructor.isAccessible = true
                constructor.newInstance(*args)
            }.onFailure {
                helperLog(TAG, "constructor ${constructorSignature(constructor)} failed cameraId=$cameraId: ${summary(it)}")
            }.getOrNull() ?: continue
            val openResult = runCatching { instanceOpen.invoke(candidate) }
            if (openResult.isFailure) {
                helperLog(
                    TAG,
                    "instance open failed cameraId=$cameraId: ${summary(openResult.exceptionOrNull()!!)}",
                )
                runCatching { invokeBoolean(candidate, "close") }
                continue
            }
            val opened = openResult.getOrNull()
            if (operationAccepted(opened)) {
                helperLog(TAG, "constructor ${constructorSignature(constructor)} succeeded cameraId=$cameraId")
                return candidate
            }
            runCatching { invokeBoolean(candidate, "close") }
        }
        return null
    }

    private fun resolveCameraApi(): CameraApi {
        cameraApi?.let { return it }
        val cameraClass = Class.forName(AVM_CAMERA_CLASS)
        val staticFactories = (cameraClass.methods.asSequence() + cameraClass.declaredMethods.asSequence())
            .filter { method ->
                method.name == "open" &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.firstOrNull().isIntType()
            }
            .distinctBy(::methodSignature)
            .sortedBy { it.parameterTypes.size }
            .toList()
        val instanceOpen = (cameraClass.methods.asSequence() + cameraClass.declaredMethods.asSequence())
            .firstOrNull { method ->
                method.name == "open" &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty()
            }
            ?.apply { isAccessible = true }
        val constructors = cameraClass.declaredConstructors
            .asSequence()
            .filter { it.parameterTypes.firstOrNull().isIntType() }
            .sortedBy { it.parameterTypes.size }
            .toList()
        return CameraApi(
            cameraClass = cameraClass,
            staticFactories = staticFactories,
            instanceOpen = instanceOpen,
            constructors = constructors,
        ).also { resolved ->
            cameraApi = resolved
            helperLog(
                TAG,
                "capabilities factories=${resolved.staticFactories.size} " +
                    "constructors=${resolved.constructors.size} instanceOpen=${resolved.instanceOpen != null}",
            )
        }
    }

    private fun instantiateCameraInfo(clazz: Class<*>): Any? {
        val bypassContext = systemContext?.let(::PermissionBypassContext)
        val factories = allMethods(clazz)
            .filter { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.name == "getInstance" &&
                    (method.parameterTypes.isEmpty() ||
                        (method.parameterTypes.size == 1 && bypassContext != null &&
                            method.parameterTypes[0].isAssignableFrom(bypassContext.javaClass)))
            }
            .distinctBy(::methodSignature)
            .sortedBy { it.parameterTypes.size }
        for (factory in factories) {
            val args = if (factory.parameterTypes.isEmpty()) emptyArray() else arrayOf(bypassContext)
            val instance = runCatching {
                factory.isAccessible = true
                factory.invoke(null, *args)
            }.onFailure {
                helperLog(TAG, "camera info ${methodSignature(factory)} failed: ${summary(it)}")
            }.getOrNull()
            if (instance != null && clazz.isInstance(instance)) return instance
        }

        for (constructor in clazz.declaredConstructors.sortedBy { it.parameterTypes.size }) {
            val args = Array<Any?>(constructor.parameterTypes.size) { index ->
                val type = constructor.parameterTypes[index]
                when {
                    bypassContext != null && type.isAssignableFrom(bypassContext.javaClass) -> bypassContext
                    type.isPrimitive -> defaultValue(type)
                    else -> null
                }
            }
            val instance = runCatching {
                constructor.isAccessible = true
                constructor.newInstance(*args)
            }.onFailure {
                helperLog(TAG, "camera info ${constructorSignature(constructor)} failed: ${summary(it)}")
            }.getOrNull()
            if (instance != null) {
                helperLog(TAG, "camera info ${constructorSignature(constructor)} succeeded")
                return instance
            }
        }
        helperLog(TAG, "camera info instance unavailable")
        return null
    }

    /**
     * setPreviewSurface is the primary binding API. addPreviewSurface is only a compatibility
     * fallback: registering the same Surface through both paths can leave two producers attached
     * and causes the flashing seen in the vehicle.
     */
    private fun bindPreviewSurface(camera: Any, surface: Surface, surfaceIndex: Int): String? {
        check(surfaceIndex >= MIN_SURFACE_INDEX) { "invalid Surface index=$surfaceIndex" }
        if (invokeBoolean(camera, "setPreviewSurface", surface, surfaceIndex)) return "set"
        if (invokeBoolean(camera, "addPreviewSurface", surface, surfaceIndex)) return "add"
        return null
    }

    private fun defaultArguments(parameterTypes: Array<Class<*>>, cameraId: Int): Array<Any?>? {
        if (!parameterTypes.firstOrNull().isIntType()) return null
        return Array(parameterTypes.size) { index ->
            if (index == 0) cameraId else defaultValue(parameterTypes[index])
        }
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }

    private fun Class<*>?.isIntType(): Boolean =
        this == java.lang.Integer.TYPE || this == java.lang.Integer::class.java

    private fun methodSignature(method: Method): String =
        "${method.name}(${method.parameterTypes.joinToString { it.simpleName }})"

    private fun constructorSignature(constructor: Constructor<*>): String =
        "${constructor.declaringClass.simpleName}(${constructor.parameterTypes.joinToString { it.simpleName }})"

    private fun readSystemProperty(name: String): String = runCatching {
        val systemProperties = Class.forName("android.os.SystemProperties")
        systemProperties.getMethod("get", String::class.java, String::class.java)
            .invoke(null, name, "") as? String
    }.getOrNull().orEmpty()

    private fun invokeBoolean(target: Any, name: String, vararg args: Any?): Boolean {
        val invocation = invokeCompatible(target, name, *args) ?: return false
        return operationAccepted(invocation.value)
    }

    private fun applyCameraFps(camera: Any, fps: Int): Boolean {
        if (fpsSetterSupported == false) return false
        val applied = invokeBoolean(camera, "setCameraFps", fps)
        fpsSetterSupported = applied
        if (!applied) helperLog(TAG, "setCameraFps unsupported; future starts will skip it")
        return applied
    }

    private fun operationAccepted(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() >= 0
        else -> true
    }

    private fun invokeCompatible(target: Any, name: String, vararg args: Any?): Invocation? {
        val methods = allMethods(target.javaClass)
            .filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.name == name &&
                    electronicMirrorArgumentsCompatible(method.parameterTypes, args)
            }
            .distinctBy(::methodSignature)
            .sortedByDescending { method -> compatibilityScore(method.parameterTypes, args) }
            .toList()
        if (methods.isEmpty()) {
            helperLog(
                TAG,
                "method unavailable ${target.javaClass.simpleName}.$name(${args.joinToString { it?.javaClass?.simpleName ?: "null" }})",
            )
            return null
        }
        for (method in methods) {
            val result = runCatching {
                method.isAccessible = true
                method.invoke(target, *args)
            }.onFailure {
                helperLog(TAG, "invoke ${methodSignature(method)} failed: ${summary(it)}")
            }
            if (result.isSuccess) return Invocation(result.getOrNull())
        }
        return null
    }

    private fun allMethods(clazz: Class<*>): Sequence<Method> {
        val declared = generateSequence(clazz) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
        return clazz.methods.asSequence() + declared
    }

    private fun compatibilityScore(parameterTypes: Array<Class<*>>, args: Array<out Any?>): Int =
        parameterTypes.indices.fold(0) { score, index ->
            val argument = args[index] ?: return@fold score
            val wrapped = parameterTypes[index].boxedType()
            score + when {
                wrapped == argument.javaClass -> 4
                wrapped.isAssignableFrom(argument.javaClass) -> 2
                else -> 0
            }
        }

    private fun Class<*>.boxedType(): Class<*> = when (this) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        else -> this
    }

    private fun fail(status: Int, message: String): Int {
        helperLog(TAG, message)
        return status
    }

    private fun summary(t: Throwable): String {
        val root = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
        return "${root.javaClass.simpleName}:${root.message.orEmpty()}"
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
        const val STATUS_INVALID_REQUEST = -3
        const val STATUS_OPEN_FAILED = -4
        const val STATUS_PREVIEW_FAILED = -6

        private const val TAG = "ElectronicMirrorCamera"
        private const val AVM_CAMERA_CLASS = "android.hardware.AVMCamera"
        private const val BMM_CAMERA_INFO_CLASS = "android.hardware.BmmCameraInfo"
        private const val DEFAULT_PANORAMA_CAMERA_ID = 3
        private const val PANORAMA_CAMERA_TAG = "pano_h"
        private const val PANORAMA_TILE_COUNT = 4
        private const val SURFACE_NONE = -1
        private const val MIN_SURFACE_INDEX = 0
        private const val MAX_SURFACE_INDEX = 15
        private const val MIN_BUFFER_WIDTH = 320
        private const val MAX_BUFFER_WIDTH = 8192
        private const val MIN_BUFFER_HEIGHT = 160
        private const val MAX_BUFFER_HEIGHT = 2160
        private val CAMERA_ID_METHOD_NAMES = setOf("getCameraId", "getCameraID")
        private val CAMERA_SORT_PROPERTIES = listOf(
            "vehicle.config.cam_sort",
            "vendor.vehicle.config.cam_sort",
        )
    }

    private data class Invocation(val value: Any?)

    private data class CameraApi(
        val cameraClass: Class<*>,
        val staticFactories: List<Method>,
        val instanceOpen: Method?,
        val constructors: List<Constructor<*>>,
    )

    private data class CameraIdCandidate(
        val id: Int,
        val source: String,
    )

    private data class StartedCamera(
        val candidate: CameraIdCandidate,
        val camera: Any,
        val bindingMethod: String,
        val fpsApplied: Boolean,
    )
}

internal fun electronicMirrorArgumentsCompatible(
    parameterTypes: Array<Class<*>>,
    args: Array<out Any?>,
): Boolean {
    if (parameterTypes.size != args.size) return false
    return parameterTypes.indices.all { index ->
        val type = parameterTypes[index]
        val argument = args[index]
        if (argument == null) {
            !type.isPrimitive
        } else {
            val boxed = when (type) {
                java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
                java.lang.Byte.TYPE -> java.lang.Byte::class.java
                java.lang.Short.TYPE -> java.lang.Short::class.java
                java.lang.Integer.TYPE -> java.lang.Integer::class.java
                java.lang.Long.TYPE -> java.lang.Long::class.java
                java.lang.Float.TYPE -> java.lang.Float::class.java
                java.lang.Double.TYPE -> java.lang.Double::class.java
                java.lang.Character.TYPE -> java.lang.Character::class.java
                else -> type
            }
            boxed.isAssignableFrom(argument.javaClass)
        }
    }
}

internal fun cameraIdFromProperty(raw: String, tag: String): Int? =
    raw.split(';', ',')
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull { entry ->
            val separator = entry.indexOfAny(charArrayOf(':', '='))
            if (separator <= 0) null
            else entry.substring(0, separator).trim() to entry.substring(separator + 1).trim()
        }
        .firstOrNull { (name, _) -> name.equals(tag, ignoreCase = true) }
        ?.second
        ?.toIntOrNull()
        ?.takeIf { it >= 0 }
