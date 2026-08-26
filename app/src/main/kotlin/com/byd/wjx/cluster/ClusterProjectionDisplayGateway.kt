package com.byd.wjx.cluster

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.provider.Settings
import android.view.Surface
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.delay

internal enum class RemoteDisplayOwner { NONE, HELPER, APP, MEDIA_PROJECTION }

internal data class ProjectionDisplayHandle(
    val id: Int,
    val owner: RemoteDisplayOwner,
)

/** Owns the platform and helper-specific details for creating and releasing projection displays. */
internal class ClusterProjectionDisplayGateway {
    private val appMirrorDisplays = mutableMapOf<Int, VirtualDisplay>()
    private var projectionPermissionsGranted = false

    suspend fun create(
        context: Context,
        helper: HelperClient,
        layout: ProjectionLayout,
        plan: RenderPlan,
        surface: Surface,
    ): ProjectionDisplayHandle? = when (layout) {
        ProjectionLayout.INDEPENDENT -> createIndependent(helper, plan, surface)
        ProjectionLayout.DUPLICATE -> createDuplicate(context, helper, plan, surface)
    }

    suspend fun release(id: Int, owner: RemoteDisplayOwner, helper: HelperClient): Boolean {
        return when (owner) {
            RemoteDisplayOwner.NONE -> true
            RemoteDisplayOwner.HELPER -> helper.releaseVirtualDisplay(id)
            RemoteDisplayOwner.APP -> {
                appMirrorDisplays.remove(id)?.release()
                true
            }
            RemoteDisplayOwner.MEDIA_PROJECTION -> {
                ClusterMirrorService.releaseProjectionAndStop()
                true
            }
        }
    }

    suspend fun ensureOverlayPermission(context: Context, helper: HelperClient): Boolean {
        // PROJECT_MEDIA has no app-side query API, so grant it together with SYSTEM_ALERT_WINDOW.
        if (!projectionPermissionsGranted) {
            WjxFileLogger.i(TAG, "granting overlay + project_media via daemon")
            if (helper.grantOverlayPermission()) projectionPermissionsGranted = true
        }
        if (Settings.canDrawOverlays(context)) return true
        WjxFileLogger.w(TAG, "SYSTEM_ALERT_WINDOW still missing; waiting for grant to apply")
        repeat(OVERLAY_PERMISSION_CHECK_COUNT) {
            delay(OVERLAY_PERMISSION_CHECK_INTERVAL_MS)
            if (Settings.canDrawOverlays(context)) return true
        }
        return false
    }

    private suspend fun createIndependent(
        helper: HelperClient,
        plan: RenderPlan,
        surface: Surface,
    ): ProjectionDisplayHandle? {
        val id = helper.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            plan.bufferWidth,
            plan.bufferHeight,
            plan.densityDpi,
            INDEPENDENT_VIRTUAL_DISPLAY_FLAGS,
            surface,
        )
        return id?.let { ProjectionDisplayHandle(it, RemoteDisplayOwner.HELPER) }
    }

    private suspend fun createDuplicate(
        context: Context,
        helper: HelperClient,
        plan: RenderPlan,
        surface: Surface,
    ): ProjectionDisplayHandle? {
        for (flag in DUPLICATE_HELPER_FLAGS) {
            val id = helper.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                plan.bufferWidth,
                plan.bufferHeight,
                plan.densityDpi,
                flag,
                surface,
            )
            if (id != null) {
                WjxFileLogger.i(TAG, "duplicate display via helper flag=$flag id=$id")
                return ProjectionDisplayHandle(id, RemoteDisplayOwner.HELPER)
            }
        }

        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        for (flag in DUPLICATE_APP_FLAGS) {
            val display = runCatching {
                displayManager.createVirtualDisplay(
                    VIRTUAL_DISPLAY_NAME,
                    plan.bufferWidth,
                    plan.bufferHeight,
                    plan.densityDpi,
                    surface,
                    flag,
                )
            }.getOrNull()
            val id = display?.display?.displayId ?: -1
            if (id > 0 && display != null) {
                appMirrorDisplays[id] = display
                WjxFileLogger.i(TAG, "duplicate display via app DisplayManager flag=$flag id=$id")
                return ProjectionDisplayHandle(id, RemoteDisplayOwner.APP)
            }
            display?.release()
        }

        if (ClusterMirrorService.ensureProjection(context)) {
            val id = ClusterMirrorService.createMirrorVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                plan.bufferWidth,
                plan.bufferHeight,
                plan.densityDpi,
                surface,
            )
            if (id != null) {
                WjxFileLogger.i(TAG, "duplicate display via MediaProjection id=$id")
                return ProjectionDisplayHandle(id, RemoteDisplayOwner.MEDIA_PROJECTION)
            }
        }
        return null
    }

    private companion object {
        const val TAG = "ClusterProjection"
        const val VIRTUAL_DISPLAY_NAME = "SuperToyBox_Cluster_VD"
        const val INDEPENDENT_VIRTUAL_DISPLAY_FLAGS = 322
        const val OVERLAY_PERMISSION_CHECK_COUNT = 10
        const val OVERLAY_PERMISSION_CHECK_INTERVAL_MS = 200L
        val DUPLICATE_HELPER_FLAGS = intArrayOf(16, 17, 18, 272, 274)
        val DUPLICATE_APP_FLAGS = intArrayOf(18, 16, 17)
    }
}
