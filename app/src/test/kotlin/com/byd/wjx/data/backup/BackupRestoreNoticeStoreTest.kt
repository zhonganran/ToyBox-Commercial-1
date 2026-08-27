package com.byd.wjx.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class BackupRestoreNoticeStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(BackupRestoreNoticeStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `notice is consumed only once after restart`() {
        BackupRestoreNoticeStore.save(context, "请检查权限")

        assertEquals("请检查权限", BackupRestoreNoticeStore.consume(context))
        assertNull(BackupRestoreNoticeStore.consume(context))
    }
}
