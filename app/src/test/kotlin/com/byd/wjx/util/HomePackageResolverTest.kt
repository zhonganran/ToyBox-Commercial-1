package com.byd.wjx.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePackageResolverTest {
    @Test
    fun resolvedPackages_containsOnlyDefaultAndKnownLaunchers() {
        val packages = HomePackageResolver.resolvedPackages("com.vendor.actualhome")

        assertTrue("com.vendor.actualhome" in packages)
        assertTrue("com.android.launcher3" in packages)
        assertFalse("com.byd.carsettings" in packages)
        assertFalse("com.byd.mycar" in packages)
    }

    @Test
    fun isHomePackage_rejectsCarSettingsCapabilityCandidate() {
        val packages = HomePackageResolver.resolvedPackages("com.android.launcher3")

        assertFalse(HomePackageResolver.isHomePackage("com.byd.carsettings", packages))
        assertTrue(HomePackageResolver.isHomePackage("com.android.launcher3", packages))
    }

    @Test
    fun launcherHeuristic_excludesMapPackages() {
        assertTrue(HomePackageResolver.isKnownHomePackage("com.vendor.launcher"))
        assertFalse(HomePackageResolver.isKnownHomePackage("com.vendor.launchermap"))
    }
}
