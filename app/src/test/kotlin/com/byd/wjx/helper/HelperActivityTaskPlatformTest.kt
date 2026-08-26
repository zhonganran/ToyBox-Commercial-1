package com.byd.wjx.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperActivityTaskPlatformTest {
    @Test
    fun `projection package names follow Android identifier syntax`() {
        assertTrue(isValidAndroidPackageName("com.autonavi.amapauto"))
        assertTrue(isValidAndroidPackageName("com.example.app_2"))

        assertFalse(isValidAndroidPackageName("com.example.app;id"))
        assertFalse(isValidAndroidPackageName("com.example.app && reboot"))
        assertFalse(isValidAndroidPackageName("com.example.app/name"))
        assertFalse(isValidAndroidPackageName("single"))
        assertFalse(isValidAndroidPackageName("com.2invalid"))
        assertFalse(isValidAndroidPackageName(""))
    }
}
