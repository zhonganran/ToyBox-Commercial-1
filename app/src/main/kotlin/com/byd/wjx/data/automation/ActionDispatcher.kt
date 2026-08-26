package com.byd.wjx.data.automation

import android.content.Context
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.vehicle.AutoDoorController
import com.byd.wjx.data.vehicle.VehicleApi
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.security.EndpointPrivacy
import com.byd.wjx.vehicle.CarProfileDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

data class DispatchResult(val success: Boolean, val reason: String? = null)

@Singleton
class ActionDispatcher @Inject constructor(
    vehicleApi: VehicleApi,
    autoDoorController: AutoDoorController,
    httpClient: OkHttpClient,
    sentryModeController: SentryModeController,
    notificationPresenter: AutomationNotificationPresenter,
    @ApplicationContext context: Context,
    profileDetector: CarProfileDetector = CarProfileDetector(),
) {
    private val vehicleActions = AutomationVehicleActionHandler(
        vehicleApi = vehicleApi,
        autoDoorController = autoDoorController,
        sentryModeController = sentryModeController,
    )
    private val notificationActions = AutomationNotificationActionHandler(
        context = context,
        notificationPresenter = notificationPresenter,
    )
    private val externalActions = AutomationExternalActionHandler(context)
    private val httpActions = AutomationHttpActionHandler(httpClient)
    private val mediaActions = AutomationMediaActionHandler(context, profileDetector)

    suspend fun dispatch(
        action: ActionDef,
        data: DiParsData?,
        owner: String? = null,
    ): DispatchResult = try {
        WjxFileLogger.i(
            TAG,
            "dispatch kind=${action.kind} command=${action.command} owner=${owner.orEmpty()}",
        )
        when (action.kind) {
            "param" -> vehicleActions.dispatchParam(action, data)
            "notification_silent" -> notificationActions.show(action, silent = true)
            "notification_sound" -> notificationActions.show(action, silent = false)
            "app_launch" -> externalActions.launchApp(action)
            "call" -> externalActions.dial(action)
            "navigate" -> externalActions.navigate(action)
            "url" -> externalActions.openUrl(action)
            "delay" -> dispatchDelay(action)
            "media_volume" -> mediaActions.setMediaVolume(action)
            "inside_sound" -> mediaActions.playInsideSound(action, owner)
            "stop_inside_sound" -> mediaActions.stopAllInsideSound()
            "outside_sound" -> mediaActions.playOutsideSound(action, data, owner)
            "stop_outside_sound" -> mediaActions.stopAllOutsideSound()
            "auto_door" -> vehicleActions.controlAutoDoor(action)
            "go_home" -> externalActions.goHome()
            "http" -> httpActions.send(action)
            "play_music" -> mediaActions.playMusic(action, owner)
            "stop_music" -> mediaActions.stopMusic()
            "play_video" -> mediaActions.playVideo(action, owner)
            "stop_video" -> mediaActions.stopVideo()
            "sentry_mode" -> vehicleActions.setSentryMode(action)
            else -> DispatchResult(false, "未知动作类型：${action.kind}")
        }
    } catch (error: Exception) {
        WjxFileLogger.e(
            TAG,
            "dispatch failed for kind=${action.kind}: ${error.message}",
            error,
        )
        DispatchResult(false, EndpointPrivacy.userMessage(error, "未知错误"))
    }

    fun stopOutsideSound(owner: String) {
        mediaActions.stopOutsideSound(owner)
    }

    fun stopTransientMedia(kind: String, owner: String) {
        mediaActions.stopTransientMedia(kind, owner)
    }

    fun stopMediaOwnedByInactiveRules(activeRuleIds: Set<Long>) {
        mediaActions.stopMediaOwnedByInactiveRules(activeRuleIds)
    }

    fun stopMediaOwnedByRule(ruleId: Long) {
        mediaActions.stopMediaOwnedByRule(ruleId)
    }

    private suspend fun dispatchDelay(action: ActionDef): DispatchResult {
        val milliseconds = action.payload?.toLongOrNull()
            ?: return DispatchResult(false, "未设置延迟时长")
        if (milliseconds !in 0..AUTOMATION_MAX_DELAY_MS) {
            return DispatchResult(
                false,
                "延迟时长超出范围（0..${AUTOMATION_MAX_DELAY_MS} 毫秒）",
            )
        }
        delay(milliseconds)
        return DispatchResult(true)
    }

    companion object {
        private const val TAG = "ActionDispatcher"

        internal fun isWindowOpenCommand(command: String): Boolean =
            isAutomationWindowOpenCommand(command)

        internal fun windowOpenBlockReason(command: String, speedKmh: Int?): String? =
            automationWindowOpenBlockReason(command, speedKmh)

        internal fun shouldSkipAutomationOutsideSound(
            owner: String?,
            parkingStatus: Int?,
            allowDuringAutoParking: Boolean,
        ): Boolean = com.byd.wjx.data.automation.shouldSkipAutomationOutsideSound(
            owner = owner,
            parkingStatus = parkingStatus,
            allowDuringAutoParking = allowDuringAutoParking,
        )

        internal fun clampVolume(level: Int, max: Int): Int =
            clampAutomationVolume(level, max)
    }
}
