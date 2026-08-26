package com.byd.wjx.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.byd.wjx.R
import com.byd.wjx.log.WjxFileLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Owns verified APK handoff, unknown-source authorization, and pending-install recovery. */
internal class UpdateInstaller {
    private val verifier = UpdateApkVerifier()

    fun verify(context: Context, file: File, update: UpdateChecker.UpdateInfo) {
        verifier.verify(context, file, update)
    }

    suspend fun launchOrRequestPermission(
        context: Context,
        file: File,
        update: UpdateChecker.UpdateInfo,
        onProgress: (String) -> Unit,
    ): UpdateChecker.InstallOutcome = withContext(Dispatchers.Main) {
        val store = PendingUpdateInstallStore(context)
        if (requiresUnknownSourceGrant(context)) {
            store.save(file, update)
            onProgress(context.getString(R.string.update_install_permission_required))
            openUnknownSourceSettings(context)
            UpdateChecker.InstallOutcome.PERMISSION_REQUIRED
        } else {
            store.clear()
            installApk(context, file, update.version)
            UpdateChecker.InstallOutcome.INSTALLER_STARTED
        }
    }

    suspend fun continuePendingInstall(context: Context): Boolean {
        if (PendingUpdateInstallStore(context).load() == null) return false
        if (!requiresUnknownSourceGrant(context)) return resumePendingInstall(context)
        withContext(Dispatchers.Main) { openUnknownSourceSettings(context) }
        return true
    }

    suspend fun resumePendingInstall(context: Context): Boolean = withContext(Dispatchers.IO) {
        val store = PendingUpdateInstallStore(context)
        val pending = store.load() ?: return@withContext false
        if (!pending.file.isFile) {
            store.clear()
            WjxFileLogger.w(TAG, "pending install discarded: apk missing path=${pending.file.absolutePath}")
            return@withContext false
        }
        if (requiresUnknownSourceGrant(context)) return@withContext false

        try {
            verifier.verify(context, pending.file, pending.update)
        } catch (error: Exception) {
            pending.file.delete()
            store.clear()
            WjxFileLogger.e(TAG, "pending install verification failed", error)
            throw error
        }

        withContext(Dispatchers.Main) {
            store.clear()
            installApk(context, pending.file, pending.update.version)
        }
        true
    }

    private fun requiresUnknownSourceGrant(context: Context): Boolean {
        val canRequest = runCatching {
            context.packageManager.canRequestPackageInstalls()
        }.getOrDefault(false)
        return UpdateInstallPermissionPolicy.requiresUnknownSourceGrant(
            sdkInt = Build.VERSION.SDK_INT,
            canRequestPackageInstalls = canRequest,
        )
    }

    private fun openUnknownSourceSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        WjxFileLogger.i(TAG, "opening unknown-source settings")
        runCatching { context.startActivity(intent) }
            .onFailure { error ->
                WjxFileLogger.w(TAG, "unknown-source settings unavailable: ${error.message}", error)
                throw IllegalStateException("无法打开允许安装未知应用设置", error)
            }
    }

    private fun installApk(context: Context, file: File, version: String) {
        if (!file.exists()) {
            WjxFileLogger.w(TAG, "install skipped: apk missing version=$version path=${file.absolutePath}")
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        WjxFileLogger.i(TAG, "install intent start version=$version path=${file.absolutePath} size=${file.length()}")
        context.startActivity(intent)
    }

    private companion object {
        const val TAG = "UpdateInstaller"
    }
}
