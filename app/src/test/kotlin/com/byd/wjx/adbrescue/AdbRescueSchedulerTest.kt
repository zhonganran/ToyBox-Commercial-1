package com.byd.wjx.adbrescue

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AdbRescueSchedulerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        ShadowAlarmManager.reset()
        context = ApplicationProvider.getApplicationContext()
        AdbRescuePreferences(context).setGuardEnabled(false)
    }

    @Test
    fun `schedule creates five alarms and cancel removes them`() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadow = shadowOf(alarmManager)
        val baselineCount = shadow.scheduledAlarms.size

        AdbRescueScheduler.scheduleAll(context)

        assertEquals(baselineCount + 5, shadow.scheduledAlarms.size)
        AdbRescueScheduler.cancelAll(context)
        assertEquals(baselineCount, shadow.scheduledAlarms.size)
    }

    @Test
    fun `retry receiver never starts the application background stack`() {
        AdbRescuePreferences(context).setGuardEnabled(true)
        val application = context.applicationContext as Application

        AdbRescueRetryReceiver().onReceive(
            context,
            Intent(context, AdbRescueRetryReceiver::class.java)
                .putExtra(AdbRescueScheduler.EXTRA_DELAY_SECONDS, 10),
        )

        assertNull(shadowOf(application).nextStartedService)
    }
}
