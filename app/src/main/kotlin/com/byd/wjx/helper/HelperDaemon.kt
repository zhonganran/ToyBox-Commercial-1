@file:JvmName("HelperDaemon")

package com.byd.wjx.helper

import android.annotation.SuppressLint
import android.content.Context
import android.os.IBinder
import android.os.Looper
import kotlin.system.exitProcess

private const val LOCK_PATH = "/data/local/tmp/wjx_helper.lock"

/**
 * Shell-uid daemon entry point. Keep this file and its JVM name stable because app_process starts
 * `com.byd.wjx.helper.HelperDaemon` directly.
 */
@SuppressLint("PrivateApi")
fun main(args: Array<String>) {
    val expectedUid = args.getOrNull(0)?.toIntOrNull() ?: run {
        System.err.println("ERR: usage: HelperDaemon <appUid>")
        exitProcess(2)
    }

    val lockPair = acquireSingleOwnerLock(LOCK_PATH)
    if (lockPair == null) {
        println("ALREADY_RUNNING")
        exitProcess(0)
    }
    @Suppress("UNUSED_VARIABLE")
    val lockChannel = lockPair.first
    @Suppress("UNUSED_VARIABLE")
    val lockHandle = lockPair.second

    @Suppress("DEPRECATION")
    Looper.prepareMainLooper()
    val systemContext: Context? = acquireSystemContext()

    val serviceManagerClass = Class.forName("android.os.ServiceManager")
    val autoservice = serviceManagerClass.getMethod("getService", String::class.java)
        .invoke(null, "autoservice") as? IBinder
        ?: run {
            System.err.println("ERR: autoservice not found")
            exitProcess(3)
        }
    val runtime = HelperDaemonRuntime(
        autoservice = autoservice,
        autoInterface = autoservice.interfaceDescriptor.orEmpty(),
        systemContext = systemContext,
    )
    val helperBinder = HelperBinderService(
        expectedUid = expectedUid,
        dispatcher = HelperTransactionDispatcher(runtime),
    )

    try {
        serviceManagerClass.getMethod("addService", String::class.java, IBinder::class.java)
            .invoke(null, HelperBinderProtocol.SERVICE_NAME, helperBinder)
    } catch (error: Exception) {
        System.err.println("ERR: addService ${error.message}")
        exitProcess(4)
    }

    System.out.println("READY pid=${android.os.Process.myPid()}")
    System.out.flush()
    WjxRuntimeWatchdog.enable()
    runtime.start()
    Looper.loop()
}
