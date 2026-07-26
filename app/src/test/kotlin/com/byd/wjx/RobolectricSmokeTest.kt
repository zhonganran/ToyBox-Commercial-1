package com.byd.wjx

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.log.WjxFileLogger
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class RobolectricSmokeTest {
    @After
    fun flushFileLogger() {
        WjxFileLogger.flush()
    }

    @Test
    fun `application context is available`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(ctx)
    }
}
