package com.byd.wjx.data.vehicle

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class HelperInstallIdentityStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences().edit().clear().commit()
    }

    @Test
    fun `identity is stable until app data is removed`() {
        val first = HelperInstallIdentityStore(context).current()
        val repeated = HelperInstallIdentityStore(context).current()

        preferences().edit().clear().commit()
        val reinstalled = HelperInstallIdentityStore(context).current()

        assertEquals(first, repeated)
        assertNotEquals(first, reinstalled)
    }

    private fun preferences() =
        context.getSharedPreferences("helper_runtime_identity", Context.MODE_PRIVATE)
}
