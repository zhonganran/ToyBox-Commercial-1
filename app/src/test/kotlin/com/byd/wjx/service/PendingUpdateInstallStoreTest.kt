package com.byd.wjx.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PendingUpdateInstallStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = PendingUpdateInstallStore(context)

    @Before
    @After
    fun clearStore() {
        store.clear()
    }

    @Test
    fun `verified update survives settings round trip`() {
        val file = File(context.cacheDir, "update.apk")
        val update = UpdateChecker.UpdateInfo(
            version = "2.2.0",
            downloadUrl = "https://download.example/app.apk",
            releaseNotes = "稳定性更新",
            forceUpdate = true,
            versionCode = 61L,
            sha256 = "a".repeat(64),
            packageName = context.packageName,
            certificateSha256 = "b".repeat(64),
            apkSize = 1234L,
        )

        store.save(file, update)

        assertEquals(PendingUpdateInstall(file, update), store.load())
        store.clear()
        assertNull(store.load())
    }
}
