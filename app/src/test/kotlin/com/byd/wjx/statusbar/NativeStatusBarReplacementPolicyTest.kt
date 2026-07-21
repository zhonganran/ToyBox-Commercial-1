package com.byd.wjx.statusbar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeStatusBarReplacementPolicyTest {
    @Test
    fun replacement_requiresEnabledConfigAndReadyCustomOrCameraState() {
        assertFalse(NativeStatusBarReplacementPolicy.shouldReplace(configEnabled = false, replacementReady = false))
        assertFalse(NativeStatusBarReplacementPolicy.shouldReplace(configEnabled = true, replacementReady = false))
        assertFalse(NativeStatusBarReplacementPolicy.shouldReplace(configEnabled = false, replacementReady = true))
        assertTrue(NativeStatusBarReplacementPolicy.shouldReplace(configEnabled = true, replacementReady = true))
    }
}
