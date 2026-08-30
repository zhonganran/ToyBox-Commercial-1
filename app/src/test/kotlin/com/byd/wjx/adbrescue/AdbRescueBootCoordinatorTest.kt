package com.byd.wjx.adbrescue

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AdbRescueBootCoordinatorTest {
    private lateinit var context: Context
    private lateinit var preferences: AdbRescuePreferences

    @Before
    fun setUp() {
        ShadowAlarmManager.reset()
        context = ApplicationProvider.getApplicationContext()
        preferences = AdbRescuePreferences(context)
        preferences.setGuardEnabled(false)
        AdbRescueTriggerThrottle.resetForTest()
    }

    @Test
    fun `boot recovery is inert until user explicitly enables guard`() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadow = shadowOf(alarmManager)
        val baselineCount = shadow.scheduledAlarms.size

        AdbRescueBootCoordinator.handle(context, Intent.ACTION_BOOT_COMPLETED)

        assertEquals(baselineCount, shadow.scheduledAlarms.size)

        preferences.setGuardEnabled(true)
        AdbRescueBootCoordinator.handle(context, Intent.ACTION_BOOT_COMPLETED)

        assertEquals(baselineCount + 5, shadow.scheduledAlarms.size)
    }
}
