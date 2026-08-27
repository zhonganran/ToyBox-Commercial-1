package com.byd.wjx.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreTransactionTest {
    @Test
    fun `preference failure rolls database and preferences back`() {
        val events = mutableListOf<String>()

        val error = assertThrows(BackupRestoreException::class.java) {
            BackupRestoreTransaction.execute(
                applyDatabase = { events += "apply-db" },
                applyPreferences = {
                    events += "apply-prefs"
                    error("commit failed")
                },
                rollbackDatabase = { events += "rollback-db" },
                rollbackPreferences = { events += "rollback-prefs" },
            )
        }

        assertEquals(
            listOf("apply-db", "apply-prefs", "rollback-db", "rollback-prefs"),
            events,
        )
        assertTrue(error.restartRequired)
        assertTrue(error.message.orEmpty().contains("已自动恢复"))
    }

    @Test
    fun `rollback failure remains visible on the restore exception`() {
        val error = assertThrows(BackupRestoreException::class.java) {
            BackupRestoreTransaction.execute(
                applyDatabase = {},
                applyPreferences = { error("commit failed") },
                rollbackDatabase = { error("database rollback failed") },
                rollbackPreferences = {},
            )
        }

        assertTrue(error.message.orEmpty().contains("未完整完成"))
        assertEquals(1, error.cause?.suppressed?.size)
    }
}
