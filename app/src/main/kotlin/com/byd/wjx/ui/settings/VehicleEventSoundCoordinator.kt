package com.byd.wjx.ui.settings

import android.content.Context
import android.net.Uri
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.audio.LockSoundConfig
import com.byd.wjx.audio.LockSoundConfigSync
import com.byd.wjx.audio.LockSoundEvent
import com.byd.wjx.audio.LockSoundSettings
import com.byd.wjx.data.sound.LocalSoundLibraryCoordinator
import com.byd.wjx.storage.SoundLibrary
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VehicleEventSoundState(
    val config: LockSoundConfig,
    val choices: List<LockSoundChoice>,
    val status: String? = null,
    val statusIsError: Boolean = false,
)

data class VehicleEventSoundChange(
    val state: VehicleEventSoundState,
    val syncReason: String? = null,
)

/** Owns validation, persistence, import, and helper synchronization for vehicle event sounds. */
@Singleton
class VehicleEventSoundCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val activationManager: ActivationManager,
    private val soundLibrary: SoundLibrary,
    private val localSoundLibraryCoordinator: LocalSoundLibraryCoordinator,
    private val configSync: LockSoundConfigSync,
) {
    private val appContext = context.applicationContext
    private val settings = LockSoundSettings(appContext)

    suspend fun snapshot(
        status: String? = null,
        statusIsError: Boolean = false,
    ): VehicleEventSoundState = withContext(Dispatchers.IO) {
        snapshotBlocking(status, statusIsError)
    }

    private fun snapshotBlocking(
        status: String? = null,
        statusIsError: Boolean = false,
    ): VehicleEventSoundState {
        val allowed = activationManager.canUse(PremiumFeature.VEHICLE_EVENT_SOUNDS)
        val config = settings.snapshot().let { stored ->
            if (allowed) stored else stored.copy(
                unlockEnabled = false,
                lockEnabled = false,
                sentryEnabled = false,
            )
        }
        val choices = soundLibrary.listSounds().map { sound ->
            LockSoundChoice(
                path = sound.file.absolutePath,
                name = sound.name,
                sizeLabel = sound.sizeLabel,
            )
        }
        return VehicleEventSoundState(
            config = config,
            choices = choices,
            status = status,
            statusIsError = statusIsError,
        )
    }

    suspend fun setEnabled(event: LockSoundEvent, enabled: Boolean): VehicleEventSoundChange =
        withContext(Dispatchers.IO) {
            if (enabled && !canUsePremiumFeature()) {
                return@withContext VehicleEventSoundChange(
                    snapshotBlocking("试用已结束，${event.title}需要激活后使用", statusIsError = true),
                )
            }
            val current = settings.snapshot()
            if (enabled && !soundLibrary.isManagedSound(current.path(event))) {
                val first = soundLibrary.listSounds().firstOrNull()
                    ?: return@withContext VehicleEventSoundChange(
                        snapshotBlocking("WJX 音效库为空，请先导入音效", statusIsError = true),
                    )
                settings.setSoundPath(event, first.file.absolutePath)
            }
            settings.setEnabled(event, enabled)
            VehicleEventSoundChange(
                state = snapshotBlocking(if (enabled) "${event.title}已开启" else "${event.title}已关闭"),
                syncReason = "settings:${event.configKey}:enabled",
            )
        }

    suspend fun setPath(event: LockSoundEvent, path: String): VehicleEventSoundChange =
        withContext(Dispatchers.IO) {
            if (!canUsePremiumFeature()) {
                return@withContext VehicleEventSoundChange(
                    snapshotBlocking("试用已结束，${event.title}需要激活后使用", statusIsError = true),
                )
            }
            val source = File(path)
            if (!source.isFile) {
                return@withContext VehicleEventSoundChange(
                    snapshotBlocking("音效文件不存在", statusIsError = true),
                )
            }
            if (!soundLibrary.isManagedSound(source.absolutePath)) {
                return@withContext VehicleEventSoundChange(
                    snapshotBlocking("请从 WJX 音效库中选择", statusIsError = true),
                )
            }
            settings.setSoundPath(event, source.absolutePath)
            settings.setEnabled(event, true)
            VehicleEventSoundChange(
                state = snapshotBlocking("${event.title}已选择：${source.nameWithoutExtension}"),
                syncReason = "settings:${event.configKey}:path",
            )
        }

    suspend fun importSound(uri: Uri): VehicleEventSoundChange = withContext(Dispatchers.IO) {
        localSoundLibraryCoordinator.importSound(uri).fold(
            onSuccess = { saved ->
                VehicleEventSoundChange(snapshotBlocking("已导入到应用音效库：${saved.file.name}"))
            },
            onFailure = { error ->
                VehicleEventSoundChange(
                    snapshotBlocking(
                        "导入失败：${error.message ?: "文件不可用"}",
                        statusIsError = true,
                    ),
                )
            },
        )
    }

    suspend fun deleteSound(path: String): VehicleEventSoundChange = withContext(Dispatchers.IO) {
        val result = localSoundLibraryCoordinator.deleteSound(path)
        if (!result.deleted) {
            return@withContext VehicleEventSoundChange(
                snapshotBlocking("删除失败：音效文件不存在", statusIsError = true),
            )
        }
        VehicleEventSoundChange(
            state = snapshotBlocking(
                status = if (result.eventSoundSyncSucceeded == false) {
                    "音效已删除，helper 暂不可用，稍后会自动重试"
                } else {
                    "音效已删除，车辆事件音效已同步"
                },
                statusIsError = result.eventSoundSyncSucceeded == false,
            ),
        )
    }

    suspend fun sync(reason: String): VehicleEventSoundState {
        val ok = configSync.sync(reason)
        return snapshot(
            status = if (ok) {
                "车辆事件音效已同步到后台 helper"
            } else {
                "helper 暂不可用，设置已保存，稍后会自动重试"
            },
            statusIsError = !ok,
        )
    }

    private fun canUsePremiumFeature(): Boolean =
        activationManager.canUse(PremiumFeature.VEHICLE_EVENT_SOUNDS)
}
