package com.byd.wjx.data.system

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppScopedDeviceBindingProviderTest {
    @Test
    fun `derive sameInputs isStable`() {
        val first = derive("com.byd.wjx", "adb-identity-backup-v1", "device-a")
        val second = derive("com.byd.wjx", "adb-identity-backup-v1", "device-a")

        assertArrayEquals(first, second)
    }

    @Test
    fun `derive separatesPackagePurposeAndDevice`() {
        val baseline = derive("com.byd.wjx", "adb-identity-backup-v1", "device-a")

        assertFalse(baseline.contentEquals(derive("com.example.other", "adb-identity-backup-v1", "device-a")))
        assertFalse(baseline.contentEquals(derive("com.byd.wjx", "another-purpose", "device-a")))
        assertFalse(baseline.contentEquals(derive("com.byd.wjx", "adb-identity-backup-v1", "device-b")))
    }

    private fun derive(packageName: String, purpose: String, device: String): ByteArray =
        AppScopedDeviceBindingProvider.derive(packageName, purpose, device)
}
