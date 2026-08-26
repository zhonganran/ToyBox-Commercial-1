package com.byd.wjx.data.backup

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
}
