package com.byd.wjx.helper

import android.os.IBinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperTransactionRouterTest {
    @Test
    fun `all published transactions have exactly one owner group`() {
        val expected = mapOf(
            HelperTransactionGroup.VEHICLE_IO to listOf(
                HelperBinderProtocol.TX_PING,
                HelperBinderProtocol.TX_READ,
                HelperBinderProtocol.TX_READ_BATCH,
                HelperBinderProtocol.TX_WRITE,
            ),
            HelperTransactionGroup.PROJECTION to listOf(
                HelperBinderProtocol.TX_CREATE_VIRTUAL_DISPLAY,
                HelperBinderProtocol.TX_RELEASE_VIRTUAL_DISPLAY,
                HelperBinderProtocol.TX_LAUNCH_APP,
                HelperBinderProtocol.TX_GET_TASK_ID,
                HelperBinderProtocol.TX_MOVE_TASK_TO_DISPLAY,
                HelperBinderProtocol.TX_SET_TASK_BOUNDS,
                HelperBinderProtocol.TX_SET_FOCUSED_TASK,
                HelperBinderProtocol.TX_SET_TASK_WINDOWING_MODE,
                HelperBinderProtocol.TX_GRANT_OVERLAY_PERMISSION,
                HelperBinderProtocol.TX_LAUNCH_AND_FORCE,
                HelperBinderProtocol.TX_ENABLE_ACCESSIBILITY,
            ),
            HelperTransactionGroup.SYSTEM to listOf(
                HelperBinderProtocol.TX_READ_VEHICLE_VIN,
                HelperBinderProtocol.TX_SENTRY_SHELL,
                HelperBinderProtocol.TX_EXEC_SHELL,
            ),
            HelperTransactionGroup.VEHICLE_FEATURE to listOf(
                HelperBinderProtocol.TX_SYNC_LOCK_SOUND_CONFIG,
                HelperBinderProtocol.TX_DEBUG_PLAY_LOCK_SOUND,
                HelperBinderProtocol.TX_STOP_LOCK_SOUND,
                HelperBinderProtocol.TX_PLAY_SENTRY_EVENT,
                HelperBinderProtocol.TX_SET_LOCK_SOUND_PLAYBACK_STATE,
                HelperBinderProtocol.TX_READ_MIRROR_POSITION,
                HelperBinderProtocol.TX_APPLY_MIRROR_POSITION,
            ),
        )

        val allCodes = expected.values.flatten()
        assertEquals(allCodes.size, allCodes.toSet().size)
        expected.forEach { (group, codes) ->
            codes.forEach { code -> assertEquals(group, helperTransactionGroup(code)) }
        }
    }

    @Test
    fun `reserved and unknown transactions fall through to Binder`() {
        assertNull(helperTransactionGroup(IBinder.FIRST_CALL_TRANSACTION + 25))
        assertNull(helperTransactionGroup(IBinder.FIRST_CALL_TRANSACTION + 26))
        assertNull(helperTransactionGroup(IBinder.FIRST_CALL_TRANSACTION + 27))
        assertNull(helperTransactionGroup(9999))
    }

    @Test
    fun `caller gate accepts only the configured application uid`() {
        assertTrue(isHelperCallerAllowed(callingUid = 10234, expectedUid = 10234))
        assertTrue(!isHelperCallerAllowed(callingUid = 2000, expectedUid = 10234))
    }
}
