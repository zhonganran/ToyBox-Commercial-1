package com.byd.wjx.data.backup

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPreferenceStoreTest {
    @Test
    fun `restore reports a failed preference commit`() {
        val context = mockk<Context>()
        val preferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        every { context.getSharedPreferences("settings", Context.MODE_PRIVATE) } returns preferences
        every { preferences.edit() } returns editor
        every { editor.clear() } returns editor
        every { editor.commit() } returns false
        val store = BackupPreferenceStore(context, listOf("settings"), emptySet())

        val error = assertThrows(IllegalStateException::class.java) {
            store.replaceAll(emptyMap())
        }

        assertEquals("配置恢复写入失败：settings", error.message)
    }

    @Test
    fun `restore ignores retired preference files and leaves new files at defaults`() {
        val context = mockk<Context>()
        val store = BackupPreferenceStore(context, listOf("current", "new"), emptySet())

        val restorable = store.restorableSubset(
            preferences = mapOf(
                "current" to mapOf("enabled" to true),
                "retired" to mapOf("legacy" to true),
            ),
            declaredFiles = listOf("current", "retired"),
        )

        assertTrue("current" in restorable)
        assertFalse("retired" in restorable)
        assertFalse("new" in restorable)
    }

    @Test
    fun `restore rejects preference payload not declared by its manifest`() {
        val context = mockk<Context>()
        val store = BackupPreferenceStore(context, listOf("current"), emptySet())

        assertThrows(IllegalStateException::class.java) {
            store.restorableSubset(
                preferences = mapOf("current" to emptyMap()),
                declaredFiles = emptyList(),
            )
        }
    }
}
