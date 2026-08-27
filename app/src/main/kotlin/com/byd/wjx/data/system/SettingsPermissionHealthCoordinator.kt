package com.byd.wjx.data.system

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.byd.wjx.AppRuntimePermissionPolicy
import com.byd.wjx.AppSpecialPermissionPolicy
import com.byd.wjx.FirstLaunchSpecialPermission
import com.byd.wjx.data.autoservice.AdbOnDeviceClient
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class SettingsPermissionHealthItem(
    val id: String,
    val title: String,
    val detail: String,
    val ready: Boolean,
    val optional: Boolean = false,
    val specialPermission: FirstLaunchSpecialPermission? = null,
    val openAppPermissionSettings: Boolean = false,
)

data class SettingsPermissionHealthSnapshot(
    val items: List<SettingsPermissionHealthItem> = emptyList(),
    val message: String? = null,
) {
    val unresolvedRequiredCount: Int
        get() = items.count { !it.ready && !it.optional }
}

/** Inspects user-facing permissions and actively retries the grants owned by ADB/Helper. */
@Singleton
class SettingsPermissionHealthCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val adb: AdbOnDeviceClient,
    private val helperBootstrap: HelperBootstrap,
    private val helper: HelperClient,
) {
    private val appContext = context.applicationContext

    suspend fun inspect(message: String? = null): SettingsPermissionHealthSnapshot =
        withContext(Dispatchers.IO) {
            val runtimeMissing = requiredRuntimePermissions().filterNot(::isRuntimePermissionGranted)
            val adbConnected = runCatching { adb.isConnected() }.getOrDefault(false)
            val helperReady = runCatching { helper.isAlive() }.getOrDefault(false)
            val usageStatsReady = usageStatsGranted()
            val batteryReady = batteryOptimizationIgnored()
            val installReady = canRequestPackageInstalls()

            SettingsPermissionHealthSnapshot(
                items = listOf(
                    SettingsPermissionHealthItem(
                        id = "runtime",
                        title = "运行时权限",
                        detail = if (runtimeMissing.isEmpty()) {
                            "文件、位置、摄像头、麦克风、蓝牙和通知权限已允许"
                        } else {
                            "缺少：${runtimeMissing.joinToString("、", transform = ::runtimePermissionLabel)}"
                        },
                        ready = runtimeMissing.isEmpty(),
                        openAppPermissionSettings = runtimeMissing.isNotEmpty(),
                    ),
                    specialItem(
                        id = "all_files_access",
                        title = "所有文件访问",
                        permission = FirstLaunchSpecialPermission.ALL_FILES_ACCESS,
                        readyDetail = "可读取整个本地共享存储",
                        missingDetail = "需要在系统设置中允许管理所有文件",
                        optional = Build.VERSION.SDK_INT < Build.VERSION_CODES.R,
                    ),
                    specialItem(
                        id = "location_services",
                        title = "定位服务",
                        permission = FirstLaunchSpecialPermission.LOCATION_SERVICES,
                        readyDetail = "系统定位开关已打开",
                        missingDetail = "系统定位开关未打开",
                    ),
                    specialItem(
                        id = "bluetooth",
                        title = "蓝牙服务",
                        permission = FirstLaunchSpecialPermission.BLUETOOTH,
                        readyDetail = "系统蓝牙开关已打开",
                        missingDetail = "系统蓝牙开关未打开，LED设备无法连接",
                        optional = true,
                    ),
                    specialItem(
                        id = "overlay",
                        title = "悬浮窗",
                        permission = FirstLaunchSpecialPermission.OVERLAY,
                        readyDetail = "桌面歌词、桌面卡片和仪表投射可用",
                        missingDetail = "需要在系统设置中允许悬浮窗",
                    ),
                    specialItem(
                        id = "notification_listener",
                        title = "通知读取",
                        permission = FirstLaunchSpecialPermission.NOTIFICATION_LISTENER,
                        readyDetail = "第三方音乐播放信息可读取",
                        missingDetail = "需要在系统设置中允许通知读取",
                    ),
                    specialItem(
                        id = "accessibility",
                        title = "无障碍服务",
                        permission = FirstLaunchSpecialPermission.ACCESSIBILITY,
                        readyDetail = "方向盘按键和后台守护服务均已开启",
                        missingDetail = "两项无障碍服务尚未全部开启",
                    ),
                    SettingsPermissionHealthItem(
                        id = "usage_stats",
                        title = "使用情况访问",
                        detail = if (usageStatsReady) "前台应用识别可用" else "ADB自动授权尚未生效",
                        ready = usageStatsReady,
                        specialPermission = FirstLaunchSpecialPermission.USAGE_STATS,
                    ),
                    SettingsPermissionHealthItem(
                        id = "battery",
                        title = "后台与电池白名单",
                        detail = if (batteryReady) "已加入系统电池优化白名单" else "后台白名单尚未确认",
                        ready = batteryReady,
                        specialPermission = FirstLaunchSpecialPermission.BATTERY_OPTIMIZATION,
                    ),
                    SettingsPermissionHealthItem(
                        id = "adb",
                        title = "本机ADB",
                        detail = if (adbConnected) "ADB授权连接正常" else "ADB未连接，请确认调试授权弹窗",
                        ready = adbConnected,
                    ),
                    SettingsPermissionHealthItem(
                        id = "helper",
                        title = "Helper守护进程",
                        detail = if (helperReady) "车辆底层服务运行正常" else "Helper未启动或不可访问",
                        ready = helperReady,
                    ),
                    SettingsPermissionHealthItem(
                        id = "package_install",
                        title = "安装应用更新",
                        detail = if (installReady) "允许安装应用内更新" else "首次更新时会引导允许安装未知应用",
                        ready = installReady,
                        optional = true,
                    ),
                ),
                message = message,
            )
        }

    suspend fun repairAndInspect(): SettingsPermissionHealthSnapshot {
        val adbReady = runCatching { adb.connect().isSuccess }.getOrDefault(false)
        val usageGranted = adbReady && runCatching {
            adb.grantUsageStatsAppop(appContext.packageName)
        }.getOrDefault(false)
        val listenerGranted = adbReady && runCatching {
            adb.grantMusicNotificationListenerAccess()
        }.getOrDefault(false)
        val helperReady = runCatching { helperBootstrap.ensureCurrent() }.getOrDefault(false)
        val overlayGranted = helperReady && runCatching {
            helper.grantOverlayPermission()
        }.getOrDefault(false)
        val accessibilityGranted = helperReady && runCatching {
            helper.enableAccessibilityService()
        }.getOrDefault(false)
        delay(400L)
        val failedGrants = buildList {
            if (adbReady && !usageGranted) add("使用情况访问")
            if (adbReady && !listenerGranted) add("通知读取")
            if (helperReady && !overlayGranted) add("悬浮窗/投射")
            if (helperReady && !accessibilityGranted) add("无障碍")
        }
        val summary = when {
            !adbReady -> "自动修复未完成：请先在ADB授权弹窗中选择始终允许"
            !helperReady -> "ADB已连接，但Helper启动失败；请上传诊断日志"
            failedGrants.isNotEmpty() -> "自动授权失败：${failedGrants.joinToString("、")}"
            else -> "已重新执行ADB与Helper自动授权"
        }
        return inspect(summary)
    }

    private fun specialItem(
        id: String,
        title: String,
        permission: FirstLaunchSpecialPermission,
        readyDetail: String,
        missingDetail: String,
        optional: Boolean = false,
    ): SettingsPermissionHealthItem {
        val ready = runCatching {
            AppSpecialPermissionPolicy.isGranted(appContext, permission)
        }.getOrDefault(false)
        return SettingsPermissionHealthItem(
            id = id,
            title = title,
            detail = if (ready) readyDetail else missingDetail,
            ready = ready,
            optional = optional,
            specialPermission = permission,
        )
    }

    private fun requiredRuntimePermissions(): List<String> = buildList {
        addAll(AppRuntimePermissionPolicy.initialPermissions(Build.VERSION.SDK_INT))
        add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }.distinct()

    private fun isRuntimePermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private fun runtimePermissionLabel(permission: String): String = when (permission) {
        Manifest.permission.READ_EXTERNAL_STORAGE -> "文件读取"
        Manifest.permission.WRITE_EXTERNAL_STORAGE -> "文件写入"
        Manifest.permission.ACCESS_COARSE_LOCATION -> "大致位置"
        Manifest.permission.ACCESS_FINE_LOCATION -> "精确位置"
        Manifest.permission.ACCESS_BACKGROUND_LOCATION -> "后台位置"
        Manifest.permission.CAMERA -> "摄像头"
        Manifest.permission.RECORD_AUDIO -> "麦克风"
        Manifest.permission.BLUETOOTH_SCAN -> "蓝牙扫描"
        Manifest.permission.BLUETOOTH_CONNECT -> "蓝牙连接"
        Manifest.permission.POST_NOTIFICATIONS -> "通知"
        else -> permission.substringAfterLast('.')
    }

    private fun usageStatsGranted(): Boolean = runCatching {
        val manager = appContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return@runCatching false
        val mode = manager.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            appContext.packageName,
        )
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    private fun batteryOptimizationIgnored(): Boolean = runCatching {
        val manager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return@runCatching false
        manager.isIgnoringBatteryOptimizations(appContext.packageName)
    }.getOrDefault(false)

    private fun canRequestPackageInstalls(): Boolean =
        runCatching { appContext.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
}
