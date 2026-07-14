package com.byd.wjx.helper

import android.os.IBinder

/**
 * Wire contract shared by the in-app binder client (HelperClientImpl) and the
 * shell-uid daemon (HelperDaemon). The daemon registers under SERVICE_NAME via
 * ServiceManager.addService; the app reaches it with ServiceManager.getService +
 * IBinder.transact.
 *
 * Parcel layout (after data.writeInterfaceToken(DESCRIPTOR) on the request):
 *   TX_PING  : request (no args)                       -> reply: writeInt(status=0), writeInt(protocolVersion)
 *   TX_READ  : writeInt(tx), writeInt(dev), writeInt(fid)
 *                                                        -> reply: writeInt(status), writeInt(value)
 *   TX_READ_BATCH : writeInt(count), repeated [writeInt(tx), writeInt(dev), writeInt(fid)]
 *       -> reply: writeInt(batchStatus), writeInt(count), repeated [writeInt(status), writeInt(value)]
 *   TX_WRITE : writeInt(dev), writeInt(fid), writeInt(value)
 *                                                        -> reply: writeInt(status), writeInt(value)
 *   TX_CREATE_VIRTUAL_DISPLAY : writeString(name), writeInt(width), writeInt(height),
 *                               writeInt(density), writeInt(flags), Surface.writeToParcel(surface)
 *       -> reply: writeInt(status), writeInt(displayId)   // status 0 = ok, displayId>0
 *   TX_RELEASE_VIRTUAL_DISPLAY : writeInt(displayId)      -> reply: writeInt(status), writeInt(0)
 *   TX_LAUNCH_APP : writeString(packageName)              -> reply: writeInt(status), writeInt(0)
 *   TX_GET_TASK_ID : writeString(packageName)             -> reply: writeInt(status), writeInt(taskId)  // taskId -1 = not found
 *   TX_MOVE_TASK_TO_DISPLAY : writeInt(taskId), writeInt(displayId)   -> reply: writeInt(status), writeInt(0)
 *   TX_SET_TASK_BOUNDS : writeInt(taskId), writeInt(left), writeInt(top), writeInt(right), writeInt(bottom)
 *       -> reply: writeInt(status), writeInt(0)
 *   TX_SET_FOCUSED_TASK : writeInt(taskId)                -> reply: writeInt(status), writeInt(0)
 *   TX_SET_TASK_WINDOWING_MODE : writeInt(taskId), writeInt(windowingMode) -> reply: writeInt(status), writeInt(0)
 *   TX_GRANT_OVERLAY_PERMISSION : (no args)               -> reply: writeInt(status), writeInt(0)
 *   TX_LAUNCH_AND_FORCE : writeString(packageName), writeInt(displayId), writeInt(width), writeInt(height)
 *       -> reply: writeInt(status), writeInt(0)           // status 0 = redirection completed
 *   TX_ENABLE_ACCESSIBILITY : (no args)                   -> reply: writeInt(status), writeInt(0)  // status 0 = our a11y services enabled
 *   TX_READ_VEHICLE_VIN : (no args)                       -> reply: writeInt(status), writeString(raw method dump)
 *   TX_SENTRY_SHELL : writeString(op), writeString(arg)    -> reply: writeInt(status), writeString(value)
 *   TX_SYNC_LOCK_SOUND_CONFIG : writeString(json)          -> reply: writeInt(status), writeInt(0)
 *   TX_DEBUG_PLAY_LOCK_SOUND : writeInt(kind)              -> reply: writeInt(status), writeInt(played)
 *   TX_STOP_LOCK_SOUND : writeInt(kind)                    -> reply: writeInt(status), writeInt(stopped)
 *   TX_EXEC_SHELL : writeString(command)                   -> reply: writeInt(status), writeInt(exitCode), writeString(output)
 *
 * Projection status: 0 = success, <0 = error/unavailable. Surface is written LAST so a
 * marshalling test can assert the scalar args without round-tripping the Surface.
 *
 * status/value carry the raw autoservice transact result (see HelperDaemon).
 */
object HelperBinderProtocol {
    const val SERVICE_NAME = "wjx_helper"
    const val PROCESS_NAME = "wjx_helper"   // app_process --nice-name + ps lookup
    const val DESCRIPTOR = "com.byd.wjx.helper.IHelper"
    // v6 adds the restricted sentry put_global/set_prop operations. Bumping forces an
    // old app_process helper to restart after APK replacement before automation uses them.
    const val CURRENT_VERSION = 6

    const val TX_PING = IBinder.FIRST_CALL_TRANSACTION       // 1
    const val TX_READ = IBinder.FIRST_CALL_TRANSACTION + 1   // 2
    const val TX_WRITE = IBinder.FIRST_CALL_TRANSACTION + 2  // 3
    // Slot 4 previously held a local diagnostic. Reusing it for the production batch-read
    // contract is safe because no released client sends the retired diagnostic payload.
    const val TX_READ_BATCH = IBinder.FIRST_CALL_TRANSACTION + 3 // 4
    // tx 5 remains retired (diagnostic getInstrumentFeature).

    const val TX_CREATE_VIRTUAL_DISPLAY = IBinder.FIRST_CALL_TRANSACTION + 5    // 6
    const val TX_RELEASE_VIRTUAL_DISPLAY = IBinder.FIRST_CALL_TRANSACTION + 6   // 7
    const val TX_LAUNCH_APP = IBinder.FIRST_CALL_TRANSACTION + 7                // 8
    const val TX_GET_TASK_ID = IBinder.FIRST_CALL_TRANSACTION + 8               // 9
    const val TX_MOVE_TASK_TO_DISPLAY = IBinder.FIRST_CALL_TRANSACTION + 9      // 10
    const val TX_SET_TASK_BOUNDS = IBinder.FIRST_CALL_TRANSACTION + 10          // 11
    const val TX_SET_FOCUSED_TASK = IBinder.FIRST_CALL_TRANSACTION + 11         // 12
    const val TX_SET_TASK_WINDOWING_MODE = IBinder.FIRST_CALL_TRANSACTION + 12  // 13
    const val TX_GRANT_OVERLAY_PERMISSION = IBinder.FIRST_CALL_TRANSACTION + 13 // 14
    const val TX_LAUNCH_AND_FORCE = IBinder.FIRST_CALL_TRANSACTION + 14         // 15
    const val TX_ENABLE_ACCESSIBILITY = IBinder.FIRST_CALL_TRANSACTION + 15     // 16
    const val TX_READ_VEHICLE_VIN = IBinder.FIRST_CALL_TRANSACTION + 16         // 17
    // tx 18, 19 kept empty for compatibility with earlier local probes.
    const val TX_SENTRY_SHELL = IBinder.FIRST_CALL_TRANSACTION + 19             // 20
    // Match AutoTark's lock-sound helper slots for easier future diffing.
    const val TX_SYNC_LOCK_SOUND_CONFIG = IBinder.FIRST_CALL_TRANSACTION + 23   // 24
    const val TX_DEBUG_PLAY_LOCK_SOUND = IBinder.FIRST_CALL_TRANSACTION + 24    // 25
    const val TX_EXEC_SHELL = IBinder.FIRST_CALL_TRANSACTION + 25               // 26
    const val TX_STOP_LOCK_SOUND = IBinder.FIRST_CALL_TRANSACTION + 26          // 27

    /** Hard upper bound keeps a malformed caller from creating an oversized Binder reply. */
    const val MAX_BATCH_READS = 128

    /** Our own package — target of the narrow grantOverlayPermission appops call. */
    const val APP_PACKAGE = "com.byd.wjx"

    /** Steering-wheel key service, enabled together with the keepalive a11y service. */
    const val STAR_ACCESSIBILITY_SERVICE_COMPONENT =
        "com.byd.wjx/com.byd.wjx.cluster.SteeringWheelKeyService"

    /** No-op accessibility service used only to keep the process priority high. */
    const val KEEPALIVE_ACCESSIBILITY_SERVICE_COMPONENT =
        "com.byd.wjx/com.byd.wjx.service.KeepAliveAccessibilityService"

    /** Backwards-compatible name used by existing star-control checks. */
    const val ACCESSIBILITY_SERVICE_COMPONENT = STAR_ACCESSIBILITY_SERVICE_COMPONENT
}
