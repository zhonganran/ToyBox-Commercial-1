package com.byd.wjx.helper

import android.content.Context
import android.net.Uri
import android.os.IBinder
import android.os.Process
import java.lang.reflect.InvocationTargetException

internal data class BydHeadlightReply(
    val transportStatus: Int,
    val serviceCode: Int,
    val value: Int,
)

/**
 * Shell-side adapter for BYD's official DiCar external-light service.
 *
 * The proprietary DiCar classes are resolved only at runtime from the vehicle's
 * `com.byd.car.server` APK, so the app has no compile-time dependency on one
 * specific vehicle software build.
 */
internal class BydHeadlightBinderClient(
    private val systemContext: Context?,
) {
    private data class CachedService(
        val binder: IBinder,
        val proxy: Any,
    )

    private data class RemoteReply(
        val code: Int,
        val value: Int?,
        val message: String,
    )

    private class TransportFailure(
        val status: Int,
        message: String,
        cause: Throwable? = null,
    ) : Exception(message, cause)

    private val cacheLock = Any()
    @Volatile private var cachedService: CachedService? = null

    fun querySupport(): BydHeadlightReply = call("querySupport") { service ->
        val remote = parseRemoteReply(
            invoke(service.proxy, "getExternalLightConfig", HEADLIGHT_FEATURE_MASK),
            requireValue = true,
        )
        val supported = remote.code == SERVICE_SUCCESS &&
            remote.value?.and(HEADLIGHT_FEATURE_MASK) != 0
        helperLog(
            TAG,
            "support serviceCode=${remote.code} config=${remote.value} supported=$supported message=${remote.message}",
        )
        BydHeadlightReply(
            transportStatus = TRANSPORT_OK,
            serviceCode = remote.code,
            value = if (supported) 1 else 0,
        )
    }

    fun getMode(): BydHeadlightReply = call("getMode") { service ->
        val remote = parseRemoteReply(
            invoke(service.proxy, "getHeadlightMode"),
            requireValue = true,
        )
        helperLog(
            TAG,
            "get mode=${remote.value} serviceCode=${remote.code} message=${remote.message}",
        )
        BydHeadlightReply(TRANSPORT_OK, remote.code, remote.value ?: VALUE_UNAVAILABLE)
    }

    fun setMode(mode: Int): BydHeadlightReply {
        if (mode !in ALLOWED_MODES) {
            helperLog(TAG, "set rejected invalid mode=$mode")
            return BydHeadlightReply(TRANSPORT_INVALID_ARGUMENT, SERVICE_CODE_UNAVAILABLE, VALUE_UNAVAILABLE)
        }
        return call("setMode") { service ->
            val setReply = parseRemoteReply(
                invoke(service.proxy, "setHeadlightMode", mode),
                requireValue = false,
            )
            if (setReply.code != SERVICE_SUCCESS) {
                helperLog(
                    TAG,
                    "set requested=$mode serviceCode=${setReply.code} message=${setReply.message}",
                )
                return@call BydHeadlightReply(
                    TRANSPORT_OK,
                    setReply.code,
                    VALUE_UNAVAILABLE,
                )
            }

            val readback = parseRemoteReply(
                invoke(service.proxy, "getHeadlightMode"),
                requireValue = true,
            )
            helperLog(
                TAG,
                "set requested=$mode setCode=${setReply.code} readCode=${readback.code} actual=${readback.value} readMessage=${readback.message}",
            )
            BydHeadlightReply(
                transportStatus = TRANSPORT_OK,
                serviceCode = readback.code,
                value = readback.value ?: VALUE_UNAVAILABLE,
            )
        }
    }

    private fun call(
        operation: String,
        block: (CachedService) -> BydHeadlightReply,
    ): BydHeadlightReply = try {
        block(resolveService())
    } catch (error: Throwable) {
        val cause = unwrap(error)
        if (cause is android.os.DeadObjectException || cachedService?.binder?.pingBinder() != true) {
            cachedService = null
        }
        val status = (cause as? TransportFailure)?.status ?: TRANSPORT_CALL_FAILED
        helperLog(TAG, "$operation failed transport=$status error=${throwableSummary(cause)}")
        BydHeadlightReply(status, SERVICE_CODE_UNAVAILABLE, VALUE_UNAVAILABLE)
    }

    private fun resolveService(): CachedService {
        cachedService?.takeIf { it.binder.pingBinder() }?.let { return it }
        synchronized(cacheLock) {
            cachedService?.takeIf { it.binder.pingBinder() }?.let { return it }
            cachedService = null

            val context = clientContext()
            connectCarService(context)
            val binder = queryRemoteBinder(context, LIGHT_SERVICE_NAME)
                ?: throw TransportFailure(
                    TRANSPORT_SERVICE_UNAVAILABLE,
                    "provider returned no binder for $LIGHT_SERVICE_NAME",
                )
            val proxy = createProxy(LIGHT_SERVICE_NAME, binder)
            return CachedService(binder, proxy).also { cachedService = it }
        }
    }

    private fun clientContext(): Context {
        val base = systemContext
            ?: throw TransportFailure(TRANSPORT_NO_CONTEXT, "system context unavailable")
        return runCatching {
            base.createPackageContext(SHELL_PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
        }.onFailure {
            helperLog(TAG, "shell package context unavailable; using system context: ${throwableSummary(it)}")
        }.getOrDefault(base)
    }

    /** Best-effort DiCar registration. Some vehicle builds permit the feature binder directly. */
    private fun connectCarService(context: Context) {
        runCatching {
            val binder = queryRemoteBinder(context, CAR_SERVICE_NAME) ?: return
            val service = createProxy(CAR_SERVICE_NAME, binder)
            val result = parseRemoteReply(
                invoke(service, "connect", SHELL_PACKAGE, DICAR_CLIENT_VERSION),
                requireValue = false,
            )
            helperLog(TAG, "connect serviceCode=${result.code} message=${result.message}")
        }.onFailure {
            helperLog(TAG, "connect skipped/failed: ${throwableSummary(unwrap(it))}")
        }
    }

    @Suppress("DEPRECATION")
    private fun queryRemoteBinder(context: Context, serviceName: String): IBinder? {
        var lastError: Throwable? = null
        for (uri in providerUris()) {
            try {
                val cursor = context.contentResolver.query(
                    uri,
                    null,
                    null,
                    arrayOf(serviceName),
                    null,
                ) ?: continue
                cursor.use {
                    val parcelableClass = Class.forName(BINDER_PARCELABLE_CLASS)
                    val extras = it.extras
                    extras.classLoader = parcelableClass.classLoader
                    val parcelable = extras.get(BINDER_EXTRA_KEY)
                    if (parcelable != null) {
                        val binder = parcelable.javaClass.getMethod("getBinder").invoke(parcelable) as? IBinder
                        if (binder != null && binder.pingBinder()) return binder
                    }
                }
            } catch (error: Throwable) {
                lastError = unwrap(error)
            }
        }
        if (lastError != null) {
            throw TransportFailure(
                TRANSPORT_PROVIDER_FAILED,
                "provider query failed for $serviceName",
                lastError,
            )
        }
        return null
    }

    private fun providerUris(): List<Uri> {
        val base = Uri.parse(PROVIDER_URI)
        val userId = Process.myUid() / PER_USER_RANGE
        val scoped = Uri.parse("content://$userId@$PROVIDER_AUTHORITY/sync_binder")
        return listOf(base, scoped).distinct()
    }

    private fun createProxy(serviceName: String, binder: IBinder): Any = try {
        Class.forName("$serviceName\$Stub")
            .getDeclaredMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)
            ?: throw TransportFailure(TRANSPORT_PROXY_FAILED, "asInterface returned null for $serviceName")
    } catch (error: TransportFailure) {
        throw error
    } catch (error: Throwable) {
        throw TransportFailure(
            TRANSPORT_PROXY_FAILED,
            "unable to create proxy for $serviceName",
            unwrap(error),
        )
    }

    private fun invoke(target: Any, methodName: String, vararg args: Any): Any? {
        val method = target.javaClass.methods.firstOrNull {
            it.name == methodName && it.parameterTypes.size == args.size
        } ?: throw TransportFailure(
            TRANSPORT_PROXY_FAILED,
            "method $methodName/${args.size} missing on ${target.javaClass.name}",
        )
        return try {
            method.invoke(target, *args)
        } catch (error: Throwable) {
            throw unwrap(error)
        }
    }

    private fun parseRemoteReply(value: Any?, requireValue: Boolean): RemoteReply {
        if (value == null) {
            throw TransportFailure(TRANSPORT_NULL_REPLY, "DiCar returned null")
        }
        val code = readMember(value, "code") as? Number
            ?: throw TransportFailure(TRANSPORT_PARSE_FAILED, "reply code missing on ${value.javaClass.name}")
        val data = (readMember(value, "data") as? Number)?.toInt()
        if (requireValue && code.toInt() == SERVICE_SUCCESS && data == null) {
            throw TransportFailure(TRANSPORT_PARSE_FAILED, "successful reply has no integer data")
        }
        return RemoteReply(
            code = code.toInt(),
            value = data,
            message = readMember(value, "message")?.toString().orEmpty().take(160),
        )
    }

    private fun readMember(target: Any, name: String): Any? {
        runCatching { return target.javaClass.getField(name).get(target) }
        runCatching {
            val field = target.javaClass.getDeclaredField(name)
            field.isAccessible = true
            return field.get(target)
        }
        val getter = "get" + name.replaceFirstChar { it.uppercaseChar() }
        return target.javaClass.methods.firstOrNull {
            it.name == getter && it.parameterTypes.isEmpty()
        }?.invoke(target)
    }

    private fun unwrap(error: Throwable): Throwable =
        (error as? InvocationTargetException)?.targetException ?: error

    companion object {
        private const val TAG = "HeadlightBinder"
        private const val PROVIDER_AUTHORITY = "com.byd.car.server.provider.CarServiceProvider"
        private const val PROVIDER_URI = "content://$PROVIDER_AUTHORITY/sync_binder"
        private const val BINDER_PARCELABLE_CLASS = "com.byd.spi.ipc.cursor.BinderCursor\$BinderParcelable"
        private const val BINDER_EXTRA_KEY = "binder"
        private const val LIGHT_SERVICE_NAME = "com.byd.car.feature.light.ICarExternalLightService"
        private const val CAR_SERVICE_NAME = "com.byd.car.ICarService"
        private const val SHELL_PACKAGE = "com.android.shell"
        private const val DICAR_CLIENT_VERSION = "3.3.0-alpha.4"
        private const val PER_USER_RANGE = 100_000
        private const val HEADLIGHT_FEATURE_MASK = 2
        private const val SERVICE_SUCCESS = 0
        private const val SERVICE_CODE_UNAVAILABLE = -1
        private const val VALUE_UNAVAILABLE = -1

        private const val TRANSPORT_OK = 0
        private const val TRANSPORT_NO_CONTEXT = -101
        private const val TRANSPORT_PROVIDER_FAILED = -102
        private const val TRANSPORT_SERVICE_UNAVAILABLE = -103
        private const val TRANSPORT_PROXY_FAILED = -104
        private const val TRANSPORT_NULL_REPLY = -105
        private const val TRANSPORT_PARSE_FAILED = -106
        private const val TRANSPORT_CALL_FAILED = -107
        private const val TRANSPORT_INVALID_ARGUMENT = -108

        private val ALLOWED_MODES = setOf(16, 2, 4, 8)
    }
}
