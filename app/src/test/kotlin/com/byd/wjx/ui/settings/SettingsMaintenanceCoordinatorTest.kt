package com.byd.wjx.ui.settings

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.data.backup.BackupManager
import com.byd.wjx.service.UpdateChecker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SettingsMaintenanceCoordinatorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `available update is retained and download progress becomes state events`() = runTest {
        val updateChecker = mockk<UpdateChecker>()
        val update = UpdateChecker.UpdateInfo(
            version = "2.2.0",
            downloadUrl = "https://download.example/app.apk",
            releaseNotes = "稳定性改进",
        )
        val progress = slot<(String) -> Unit>()
        coEvery { updateChecker.checkForUpdate(context, forceCheck = true) } returns update
        coEvery { updateChecker.downloadAndInstall(context, update, capture(progress)) } coAnswers {
            progress.captured("25%")
        }
        val coordinator = coordinator(updateChecker = updateChecker)

        val checkState = coordinator.checkForUpdate()
        val downloadStates = coordinator.downloadUpdates().toList()

        assertEquals(UpdateState.Available("2.2.0", "稳定性改进"), checkState)
        assertTrue(coordinator.hasAvailableUpdate())
        assertEquals(2, downloadStates.size)
        assertEquals("2.2.0", (downloadStates[0] as UpdateState.Downloading).version)
        assertEquals("25%", (downloadStates[1] as UpdateState.Downloading).progress)
        coVerify(exactly = 1) { updateChecker.downloadAndInstall(context, update, any()) }

        coordinator.clearAvailableUpdate()
        assertFalse(coordinator.hasAvailableUpdate())
    }

    @Test
    fun `failed update check returns an error without retaining stale update`() = runTest {
        val updateChecker = mockk<UpdateChecker>()
        coEvery { updateChecker.checkForUpdate(context, forceCheck = true) } throws
            IllegalStateException("更新服务不可用")
        val coordinator = coordinator(updateChecker = updateChecker)

        val state = coordinator.checkForUpdate()

        assertEquals("更新服务不可用", (state as UpdateState.Error).message)
        assertFalse(coordinator.hasAvailableUpdate())
    }

    @Test
    fun `support operations map success ids and backup failures into stable messages`() = runTest {
        val logUploader = mockk<SettingsLogUploader>()
        val backupManager = mockk<BackupManager>()
        coEvery { logUploader.upload() } returns SettingsLogUploadResult(42L, "已上传")
        val destination = Uri.parse("content://backup/test.zip")
        coEvery { backupManager.export(destination) } throws IllegalStateException("存储空间不可用")
        val coordinator = coordinator(
            backupManager = backupManager,
            logUploader = logUploader,
        )

        val upload = coordinator.uploadLogs()
        val export = coordinator.exportConfig(destination)

        assertEquals(SettingsMaintenanceResult(true, "日志上传成功，编号 #42"), upload)
        assertFalse(export.success)
        assertTrue(export.message.contains("存储空间不可用"))
    }

    private fun coordinator(
        updateChecker: UpdateChecker = mockk(relaxed = true),
        backupManager: BackupManager = mockk(relaxed = true),
        logUploader: SettingsLogUploader = mockk(relaxed = true),
    ) = SettingsMaintenanceCoordinator(
        context = context,
        updateChecker = updateChecker,
        backupManager = backupManager,
        logUploader = logUploader,
    )
}
