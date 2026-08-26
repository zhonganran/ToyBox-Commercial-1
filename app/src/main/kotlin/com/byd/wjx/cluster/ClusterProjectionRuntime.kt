package com.byd.wjx.cluster

import android.content.Context
import android.view.Display
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.withTimeoutOrNull

internal enum class ProjectionResizeResult {
    KEPT_CURRENT,
    RESIZED,
    REBUILD_REQUIRED,
}

/** Owns the active overlay, remote display handle, and projected task as one runtime session. */
internal class ClusterProjectionRuntime(
    private val displayGateway: ClusterProjectionDisplayGateway = ClusterProjectionDisplayGateway(),
    private val overlayController: ClusterProjectionOverlayController = ClusterProjectionOverlayController(),
    private val displayEnvironment: ClusterProjectionDisplayEnvironment =
        ClusterProjectionDisplayEnvironment(),
) {
    private var displayHandle: ProjectionDisplayHandle? = null
    private var projectedPackage: String? = null

    val hasLiveProjection: Boolean
        get() = overlayController.hasOverlay && displayHandle != null

    suspend fun resize(
        context: Context,
        helper: HelperClient,
        bootstrap: HelperBootstrap,
        onUnexpectedSurfaceDestroyed: () -> Unit,
    ): ProjectionResizeResult {
        if (!bootstrap.ensureCurrent()) return ProjectionResizeResult.KEPT_CURRENT
        val oldOverlay = overlayController.current ?: return ProjectionResizeResult.KEPT_CURRENT
        val oldDisplay = displayHandle
        val clusterDisplay = displayEnvironment.resolveClusterDisplay(context)
            ?: return ProjectionResizeResult.KEPT_CURRENT
        val layout = readProjectionLayout(context)
        val projectionPlan = displayEnvironment.buildSurfacePlan(context, layout)
            ?: return ProjectionResizeResult.KEPT_CURRENT

        val surface = try {
            withTimeoutOrNull(SURFACE_TIMEOUT_MS) {
                addOverlayAndAwaitSurface(
                    context,
                    clusterDisplay,
                    projectionPlan,
                    onUnexpectedSurfaceDestroyed,
                )
            }
        } catch (error: Exception) {
            WjxFileLogger.e(TAG, "resize: new overlay threw: ${error.message}")
            null
        }
        if (surface == null) {
            WjxFileLogger.e(TAG, "resize: new overlay Surface not ready; keeping current size")
            overlayController.discardCurrentAndRestore(oldOverlay)
            return ProjectionResizeResult.KEPT_CURRENT
        }

        val newDisplay = displayGateway.create(
            context,
            helper,
            layout,
            projectionPlan.render,
            surface,
        )
        if (newDisplay == null) {
            WjxFileLogger.e(TAG, "resize: createVirtualDisplay failed; keeping current size")
            overlayController.discardCurrentAndRestore(oldOverlay)
            return ProjectionResizeResult.KEPT_CURRENT
        }

        if (layout == ProjectionLayout.INDEPENDENT) {
            val packageName = displayEnvironment.targetPackage(context)
            val moved = helper.launchAndForce(
                packageName,
                newDisplay.id,
                projectionPlan.render.bufferWidth,
                projectionPlan.render.bufferHeight,
            )
            if (!moved) {
                WjxFileLogger.e(TAG, "resize: launchAndForce failed")
                displayGateway.release(newDisplay.id, newDisplay.owner, helper)
                overlayController.discardCurrentAndRestore(oldOverlay)
                return ProjectionResizeResult.REBUILD_REQUIRED
            }
            projectedPackage = packageName
        } else {
            if (projectedPackage != null) pullProjectedAppBack(context, helper, focus = true)
            projectedPackage = null
        }

        commitDisplay(context, newDisplay)
        if (oldDisplay != null && !bothUseMediaProjection(oldDisplay, newDisplay)) {
            displayGateway.release(oldDisplay.id, oldDisplay.owner, helper)
        }
        overlayController.remove(oldOverlay)
        WjxFileLogger.i(
            TAG,
            "resize: swapped to ${projectionPlan.geo.width}x${projectionPlan.geo.height} " +
                "($layout vd ${oldDisplay?.id ?: -1} -> ${newDisplay.id})",
        )
        return ProjectionResizeResult.RESIZED
    }

    suspend fun project(
        context: Context,
        mode: ClusterMode,
        helper: HelperClient,
        bootstrap: HelperBootstrap,
        onUnexpectedSurfaceDestroyed: () -> Unit,
    ): Boolean {
        if (!bootstrap.ensureCurrent()) {
            WjxFileLogger.e(TAG, "helper daemon not running; aborting projection")
            return false
        }
        val layout = readProjectionLayout(context)
        if (overlayController.hasOverlay) hideOverlay(context, helper)
        if (!displayGateway.ensureOverlayPermission(context, helper)) {
            WjxFileLogger.e(TAG, "overlay permission unavailable; aborting projection")
            return false
        }
        val clusterDisplay = displayEnvironment.resolveClusterDisplay(context) ?: run {
            WjxFileLogger.e(TAG, "cluster display not found")
            return false
        }
        val projectionPlan = displayEnvironment.buildSurfacePlan(context, layout, mode)
            ?: return false

        return try {
            val surface = withTimeoutOrNull(SURFACE_TIMEOUT_MS) {
                addOverlayAndAwaitSurface(
                    context,
                    clusterDisplay,
                    projectionPlan,
                    onUnexpectedSurfaceDestroyed,
                )
            }
            if (surface == null) {
                WjxFileLogger.e(TAG, "overlay Surface not ready within ${SURFACE_TIMEOUT_MS}ms")
                hideOverlay(context, helper)
                return false
            }

            clearPreviousDisplayBeforeProjection(context, helper)
            val newDisplay = displayGateway.create(
                context,
                helper,
                layout,
                projectionPlan.render,
                surface,
            )
            if (newDisplay == null) {
                WjxFileLogger.e(TAG, "createVirtualDisplay failed")
                hideOverlay(context, helper)
                return false
            }
            commitDisplay(context, newDisplay)
            if (layout == ProjectionLayout.INDEPENDENT) {
                val packageName = displayEnvironment.targetPackage(context)
                WjxFileLogger.i(
                    TAG,
                    "VirtualDisplay id=${newDisplay.id} " +
                        "${projectionPlan.render.bufferWidth}x${projectionPlan.render.bufferHeight}" +
                        "@${projectionPlan.render.densityDpi}; launchAndForce $packageName",
                )
                val moved = helper.launchAndForce(
                    packageName,
                    newDisplay.id,
                    projectionPlan.render.bufferWidth,
                    projectionPlan.render.bufferHeight,
                )
                if (!moved) {
                    WjxFileLogger.e(TAG, "launchAndForce failed")
                    hideOverlay(context, helper)
                    return false
                }
                projectedPackage = packageName
            } else {
                WjxFileLogger.i(
                    TAG,
                    "Duplicate VirtualDisplay id=${newDisplay.id} owner=${newDisplay.owner} " +
                        "${projectionPlan.render.bufferWidth}x${projectionPlan.render.bufferHeight}" +
                        "@${projectionPlan.render.densityDpi} " +
                        "surface=${projectionPlan.surfaceWidth}x${projectionPlan.surfaceHeight}" +
                        "+${projectionPlan.surfaceX},${projectionPlan.surfaceY}",
                )
                projectedPackage = null
            }
            true
        } catch (error: Exception) {
            WjxFileLogger.e(TAG, "projection threw: ${error.message}", error)
            hideOverlay(context, helper)
            false
        }
    }

    suspend fun stop(context: Context, helper: HelperClient, focus: Boolean = true) {
        if (projectedPackage != null) pullProjectedAppBack(context, helper, focus)
        hideOverlay(context, helper)
        projectedPackage = null
    }

    suspend fun pullProjectedAppBack(context: Context, helper: HelperClient, focus: Boolean) {
        val packageName = projectedPackage ?: displayEnvironment.targetPackage(context)
        val taskId = helper.getTaskId(packageName) ?: run {
            WjxFileLogger.d(TAG, "pullBackToMain: projected task ($packageName) not found")
            return
        }
        helper.moveTaskToDisplay(taskId, Display.DEFAULT_DISPLAY)
        helper.setTaskBounds(taskId, 0, 0, 0, 0)
        if (focus) helper.setFocusedTask(taskId)
    }

    suspend fun releaseRemoteDisplayIfAlive(context: Context, helper: HelperClient) {
        val active = displayHandle ?: return
        WjxFileLogger.d(
            TAG,
            "surfaceDestroyed: releasing leaked VirtualDisplay ${active.id}/${active.owner}",
        )
        if (displayGateway.release(active.id, active.owner, helper)) {
            displayHandle = null
            if (active.owner == RemoteDisplayOwner.HELPER) clearLastDisplayId(context)
        }
    }

    fun clearProjectedPackage() {
        projectedPackage = null
    }

    fun readProjectionLayout(context: Context): ProjectionLayout =
        ClusterProjectionPreferences(context).snapshot().layout

    private suspend fun addOverlayAndAwaitSurface(
        context: Context,
        display: Display,
        plan: ProjectionSurfacePlan,
        onUnexpectedSurfaceDestroyed: () -> Unit,
    ) = overlayController.addAndAwaitSurface(
        context,
        display,
        plan,
        onUnexpectedSurfaceDestroyed,
    )

    private suspend fun clearPreviousDisplayBeforeProjection(
        context: Context,
        helper: HelperClient,
    ) {
        val stale = displayHandle
        if (stale != null) {
            if (!displayGateway.release(stale.id, stale.owner, helper)) {
                WjxFileLogger.w(
                    TAG,
                    "stale releaseDisplay(${stale.id}/${stale.owner}) failed; clearing handle and continuing",
                )
            }
            displayHandle = null
            if (stale.owner == RemoteDisplayOwner.HELPER) clearLastDisplayId(context)
        } else {
            releaseOrphanedDisplay(context, helper)
        }
    }

    private suspend fun releaseOrphanedDisplay(context: Context, helper: HelperClient) {
        val preferences = ClusterProjectionPreferences(context)
        val orphan = preferences.snapshot().lastVirtualDisplayId
        if (orphan == ClusterProjectionPreferences.NO_VIRTUAL_DISPLAY) return
        WjxFileLogger.i(TAG, "releasing orphaned VirtualDisplay id=$orphan from a prior session")
        if (!helper.releaseVirtualDisplay(orphan)) {
            WjxFileLogger.w(
                TAG,
                "orphaned VirtualDisplay id=$orphan release failed; clearing stale handle",
            )
        }
        preferences.clearLastVirtualDisplayId()
    }

    private fun commitDisplay(context: Context, handle: ProjectionDisplayHandle) {
        displayHandle = handle
        val preferences = ClusterProjectionPreferences(context)
        if (handle.owner == RemoteDisplayOwner.HELPER) {
            preferences.saveLastVirtualDisplayId(handle.id)
        } else {
            preferences.clearLastVirtualDisplayId()
        }
    }

    private suspend fun hideOverlay(context: Context, helper: HelperClient) {
        val active = displayHandle
        if (active != null) {
            if (displayGateway.release(active.id, active.owner, helper)) {
                displayHandle = null
                if (active.owner == RemoteDisplayOwner.HELPER) clearLastDisplayId(context)
            } else {
                WjxFileLogger.w(
                    TAG,
                    "releaseDisplay(${active.id}/${active.owner}) failed; keeping id for retry",
                )
            }
        }
        overlayController.removeCurrent()
    }

    private fun clearLastDisplayId(context: Context) {
        ClusterProjectionPreferences(context).clearLastVirtualDisplayId()
    }

    private fun bothUseMediaProjection(
        oldDisplay: ProjectionDisplayHandle,
        newDisplay: ProjectionDisplayHandle,
    ): Boolean = oldDisplay.owner == RemoteDisplayOwner.MEDIA_PROJECTION &&
        newDisplay.owner == RemoteDisplayOwner.MEDIA_PROJECTION

    private companion object {
        const val TAG = "ClusterProjection"
        const val SURFACE_TIMEOUT_MS = 3_000L
    }
}
