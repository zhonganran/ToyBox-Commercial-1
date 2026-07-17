package com.byd.wjx.helper

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.Surface
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier

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
            val cameraIds = resolveApaCameraIds()
            logCameraApi(cameraClass, cameraIds)
            val openedCamera = cameraIds.firstNotNullOfOrNull { cameraId ->
                openCamera(cameraClass, cameraId)?.let { cameraId to it }
            }
            if (openedCamera == null) {
                stopLocked()
                return@synchronized fail(
                    STATUS_OPEN_FAILED,
                    "AVMCamera open paths failed cameraIds=${cameraIds.joinToString()}",
                )
            }
            val (cameraId, opened) = openedCamera

            camera = opened

            val reportedCameraId = readInt(opened, "getCameraId")
            val (previewWidth, previewHeight) = resolvePreviewSize(opened, cameraId)
            val previewSizeApplied = if (previewWidth != null && previewHeight != null &&
                previewWidth > 0 && previewHeight > 0
            ) {
                invokeBoolean(opened, "setPreviewSize", previewWidth, previewHeight)
            } else {
                false
            }

            val setOk = invokeBoolean(opened, "setPreviewSurface", surface, 0)
            // BYD builds differ: some accept setPreviewSurface, others only start producing after
            // addPreviewSurface. Calling both mirrors the validated L1Mini APA implementation.
            val addOk = invokeBoolean(opened, "addPreviewSurface", surface, 0)
            if (!setOk && !addOk) {
                stopLocked()
                return@synchronized fail(STATUS_SURFACE_FAILED, "preview Surface rejected")
            }

            val fpsValue = fps.coerceIn(15, 30)
            val fpsApplied = invokeBoolean(opened, "setCameraFps", fpsValue)
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
                "started selectedId=$cameraId reportedId=$reportedCameraId viewpoint=$viewpoint " +
                    "fps=$fpsValue fpsApplied=$fpsApplied preview=${previewWidth}x$previewHeight " +
                    "sizeApplied=$previewSizeApplied set=$setOk add=$addOk " +
                    "isOpen=${readBoolean(opened, "isOpen")} isPreview=${readBoolean(opened, "isPreview")}",
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
            if (!operationAccepted(result)) {
                return fail(STATUS_PANORAMA_FAILED, "setPanoParams rejected params=${params.joinToString()}")
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
            runCatching { invokeBoolean(activeCamera, "close") }
            helperLog(TAG, "stopped")
        }
        runCatching { surface?.release() }
    }

    private fun resolveApaCameraIds(): List<Int> {
        val ids = linkedSetOf<Int>()
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
        CAMERA_TAGS.forEach { tag ->
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
            resolved?.let(ids::add)
        }

        val cameraSort = readSystemProperty(CAMERA_SORT_PROPERTY)
        CAMERA_TAGS.forEach { tag ->
            val propertyId = cameraIdFromProperty(cameraSort, tag)
            helperLog(TAG, "camera property tag=$tag id=${propertyId ?: "unavailable"}")
            propertyId?.let(ids::add)
        }
        ids += DEFAULT_APA_CAMERA_ID
        return ids.toList()
    }

    /**
     * Prefer the public factory. If the vehicle's cam_sort property is unavailable, that factory
     * can reject a valid fallback id before touching the HAL; the private constructor + private
     * open path mirrors what L1Mini's reflectLoad_5 loader does on those builds.
     */
    private fun openCamera(cameraClass: Class<*>, cameraId: Int): Any? {
        val staticFactories = (cameraClass.methods.asSequence() + cameraClass.declaredMethods.asSequence())
            .filter { method ->
                method.name == "open" &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.firstOrNull().isIntType()
            }
            .distinctBy(::methodSignature)
            .sortedBy { it.parameterTypes.size }
            .toList()
        for (factory in staticFactories) {
            val args = defaultArguments(factory.parameterTypes, cameraId) ?: continue
            val opened = runCatching {
                factory.isAccessible = true
                factory.invoke(null, *args)
            }.onFailure {
                helperLog(TAG, "factory ${methodSignature(factory)} failed cameraId=$cameraId: ${summary(it)}")
            }.getOrNull()
            if (opened != null && cameraClass.isInstance(opened)) {
                helperLog(TAG, "factory ${methodSignature(factory)} succeeded cameraId=$cameraId")
                return opened
            }
            helperLog(TAG, "factory ${methodSignature(factory)} returned null cameraId=$cameraId")
        }

        val instanceOpen = (cameraClass.methods.asSequence() + cameraClass.declaredMethods.asSequence())
            .firstOrNull { method ->
                method.name == "open" &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty()
            }
            ?.apply { isAccessible = true }
            ?: return null
        val constructors = cameraClass.declaredConstructors
            .asSequence()
            .filter { it.parameterTypes.firstOrNull().isIntType() }
            .sortedBy { it.parameterTypes.size }
            .toList()
        for (constructor in constructors) {
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

    private fun resolvePreviewSize(camera: Any, cameraId: Int): Pair<Int?, Int?> {
        val cameraWidth = readInt(camera, "getPreviewWidth")
        val cameraHeight = readInt(camera, "getPreviewHeight")
        if (cameraWidth != null && cameraHeight != null && cameraWidth > 0 && cameraHeight > 0) {
            helperLog(TAG, "preview size source=AVMCamera id=$cameraId size=${cameraWidth}x$cameraHeight")
            return cameraWidth to cameraHeight
        }

        val infoClass = runCatching { Class.forName(BMM_CAMERA_INFO_CLASS) }.getOrNull()
            ?: return cameraWidth to cameraHeight
        val width = STATIC_PREVIEW_WIDTH_METHODS.firstNotNullOfOrNull { methodName ->
            invokeStaticInt(infoClass, methodName, cameraId)
        }
        val height = STATIC_PREVIEW_HEIGHT_METHODS.firstNotNullOfOrNull { methodName ->
            invokeStaticInt(infoClass, methodName, cameraId)
        }
        helperLog(TAG, "preview size source=BmmCameraInfo id=$cameraId size=${width}x$height")
        return width?.takeIf { it > 0 } to height?.takeIf { it > 0 }
    }

    private fun invokeStaticInt(clazz: Class<*>, name: String, vararg args: Any?): Int? {
        val methods = allMethods(clazz)
            .filter { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.name == name &&
                    electronicMirrorArgumentsCompatible(method.parameterTypes, args)
            }
            .distinctBy(::methodSignature)
            .sortedByDescending { method -> compatibilityScore(method.parameterTypes, args) }
        for (method in methods) {
            val value = runCatching {
                method.isAccessible = true
                method.invoke(null, *args)
            }.onFailure {
                helperLog(TAG, "invoke static ${methodSignature(method)} failed: ${summary(it)}")
            }.getOrNull()
            if (value is Number) return value.toInt()
        }
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

    private fun logCameraApi(cameraClass: Class<*>, cameraIds: List<Int>) {
        val factories = (cameraClass.methods.asSequence() + cameraClass.declaredMethods.asSequence())
            .filter { it.name == "open" }
            .distinctBy(::methodSignature)
            .joinToString { methodSignature(it) }
        val constructors = cameraClass.declaredConstructors.joinToString { constructorSignature(it) }
        helperLog(
            TAG,
            "camera API ids=${cameraIds.joinToString()} open=[$factories] constructors=[$constructors]",
        )
    }

    private fun readSystemProperty(name: String): String = runCatching {
        val systemProperties = Class.forName("android.os.SystemProperties")
        systemProperties.getMethod("get", String::class.java, String::class.java)
            .invoke(null, name, "") as? String
    }.getOrNull().orEmpty()

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
        allMethods(clazz)
            .firstOrNull { method ->
                method.name == name &&
                    method.parameterTypes.size == parameterCount &&
                    method.parameterTypes.all { it.isIntType() }
            }
            ?.apply { isAccessible = true }

    private fun invokeBoolean(target: Any, name: String, vararg args: Any?): Boolean {
        val invocation = invokeCompatible(target, name, *args) ?: return false
        return operationAccepted(invocation.value)
    }

    private fun operationAccepted(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() >= 0
        else -> true
    }

    private fun readInt(target: Any, name: String): Int? =
        (invokeCompatible(target, name)?.value as? Number)?.toInt()

    private fun readBoolean(target: Any, name: String): Boolean? = when (
        val value = invokeCompatible(target, name)?.value
    ) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        else -> null
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
        private const val CAMERA_SORT_PROPERTY = "vehicle.config.cam_sort"
        private const val DEFAULT_APA_CAMERA_ID = 3
        private const val VIEW_NONE = -1
        private val CAMERA_ID_METHOD_NAMES = setOf("getCameraId", "getCameraID")
        private val CAMERA_TAGS = listOf("apa", "byd_apa", "pano_h", "pano_l")
        private val STATIC_PREVIEW_WIDTH_METHODS = listOf("getDefaultPreviewWidth", "getPreviewWidth")
        private val STATIC_PREVIEW_HEIGHT_METHODS = listOf("getDefaultPreviewHeight", "getPreviewHeight")
    }

    private data class Invocation(val value: Any?)
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
