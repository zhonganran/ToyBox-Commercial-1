package com.byd.wjx.service

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInstallPermissionPolicyTest {
    @Test
    fun `android 8 and newer require per-app installer access`() {
        assertTrue(
            UpdateInstallPermissionPolicy.requiresUnknownSourceGrant(
                sdkInt = Build.VERSION_CODES.O,
                canRequestPackageInstalls = false,
            ),
        )
        assertFalse(
            UpdateInstallPermissionPolicy.requiresUnknownSourceGrant(
                sdkInt = Build.VERSION_CODES.O,
                canRequestPackageInstalls = true,
            ),
        )
    }

    @Test
    fun `legacy android does not use per-app installer access`() {
        assertFalse(
            UpdateInstallPermissionPolicy.requiresUnknownSourceGrant(
                sdkInt = Build.VERSION_CODES.N_MR1,
                canRequestPackageInstalls = false,
            ),
        )
    }
}
