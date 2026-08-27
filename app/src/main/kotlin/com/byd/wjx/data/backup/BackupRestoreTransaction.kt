package com.byd.wjx.data.backup

internal class BackupRestoreException(
    message: String,
    cause: Throwable,
    val restartRequired: Boolean,
) : IllegalStateException(message, cause)

/** Applies database and preference changes as one recoverable restore operation. */
internal object BackupRestoreTransaction {
    fun execute(
        applyDatabase: () -> Unit,
        applyPreferences: () -> Unit,
        rollbackDatabase: () -> Unit,
        rollbackPreferences: () -> Unit,
    ) {
        try {
            applyDatabase()
            applyPreferences()
        } catch (restoreError: Exception) {
            val rollbackErrors = buildList {
                runCatching(rollbackDatabase).exceptionOrNull()?.let(::add)
                runCatching(rollbackPreferences).exceptionOrNull()?.let(::add)
            }
            rollbackErrors.forEach(restoreError::addSuppressed)
            val message = if (rollbackErrors.isEmpty()) {
                "配置恢复失败，已自动恢复操作前的数据"
            } else {
                "配置恢复失败，自动回滚未完整完成"
            }
            throw BackupRestoreException(
                message = message,
                cause = restoreError,
                restartRequired = true,
            )
        }
    }
}
