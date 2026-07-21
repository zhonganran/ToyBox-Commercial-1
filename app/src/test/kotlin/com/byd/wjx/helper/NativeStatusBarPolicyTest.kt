package com.byd.wjx.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeStatusBarPolicyTest {
    @Test
    fun replacement_hidesNativeVisualsButKeepsShadeExpansionEnabled() {
        assertEquals(0, NativeStatusBarPolicy.REPLACEMENT_DISABLE_FLAGS and NativeStatusBarPolicy.DISABLE_EXPAND)
        assertEquals(
            NativeStatusBarPolicy.DISABLE_NOTIFICATION_ICONS or
                NativeStatusBarPolicy.DISABLE_SYSTEM_INFO or
                NativeStatusBarPolicy.DISABLE_CLOCK,
            NativeStatusBarPolicy.REPLACEMENT_DISABLE_FLAGS,
        )
    }
}
