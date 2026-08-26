package com.byd.wjx

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FirstLaunchPermissionCoordinatorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun clearPreferences() {
        context.getSharedPreferences(FIRST_LAUNCH_PERMISSION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `android 10 initial request covers storage location and microphone`() {
        val permissions = AppRuntimePermissionPolicy.initialPermissions(Build.VERSION_CODES.Q)

        assertTrue(Manifest.permission.READ_EXTERNAL_STORAGE in permissions)
        assertTrue(Manifest.permission.WRITE_EXTERNAL_STORAGE in permissions)
        assertTrue(Manifest.permission.ACCESS_COARSE_LOCATION in permissions)
        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in permissions)
        assertTrue(Manifest.permission.RECORD_AUDIO in permissions)
        assertFalse(Manifest.permission.BLUETOOTH_SCAN in permissions)
        assertFalse(Manifest.permission.POST_NOTIFICATIONS in permissions)
    }

    @Test
    fun `android 13 uses bluetooth and notification permissions without legacy storage`() {
        val permissions = AppRuntimePermissionPolicy.initialPermissions(Build.VERSION_CODES.TIRAMISU)

        assertFalse(Manifest.permission.READ_EXTERNAL_STORAGE in permissions)
        assertFalse(Manifest.permission.WRITE_EXTERNAL_STORAGE in permissions)
        assertTrue(Manifest.permission.BLUETOOTH_SCAN in permissions)
        assertTrue(Manifest.permission.BLUETOOTH_CONNECT in permissions)
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in permissions)
    }

    @Test
    fun `first launch requests base permissions and later launches retry denied background location`() {
        val granted = mutableSetOf<String>()
        val baseRequests = mutableListOf<List<String>>()
        val backgroundRequests = mutableListOf<String>()
        val specialRequests = mutableListOf<FirstLaunchSpecialPermission>()
        val coordinator = coordinator(granted, baseRequests, backgroundRequests, specialRequests)

        coordinator.start()
        assertEquals(1, baseRequests.size)
        assertTrue(Manifest.permission.ACCESS_BACKGROUND_LOCATION !in baseRequests.single())

        granted += baseRequests.single()
        coordinator.onRuntimePermissionsResult(baseRequests.single().associateWith { true })
        assertEquals(listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), backgroundRequests)

        coordinator.onBackgroundLocationResult(granted = false)
        assertEquals(listOf(FirstLaunchSpecialPermission.LOCATION_SERVICES), specialRequests)
        repeat(FirstLaunchSpecialPermission.entries.size) { coordinator.onActivityResumed() }
        assertEquals(FirstLaunchSpecialPermission.entries, specialRequests)

        coordinator.start()
        assertEquals(1, baseRequests.size)
        assertEquals(2, backgroundRequests.size)
        assertEquals(FirstLaunchSpecialPermission.entries.size, specialRequests.size)
    }

    @Test
    fun `denied foreground location completes without requesting background location`() {
        val baseRequests = mutableListOf<List<String>>()
        val backgroundRequests = mutableListOf<String>()
        val specialRequests = mutableListOf<FirstLaunchSpecialPermission>()
        val coordinator = coordinator(
            mutableSetOf(),
            baseRequests,
            backgroundRequests,
            specialRequests,
        )

        coordinator.start()
        coordinator.onRuntimePermissionsResult(baseRequests.single().associateWith { false })

        assertTrue(backgroundRequests.isEmpty())
        assertEquals(listOf(FirstLaunchSpecialPermission.LOCATION_SERVICES), specialRequests)
        repeat(FirstLaunchSpecialPermission.entries.size) { coordinator.onActivityResumed() }
        coordinator.start()
        assertEquals(2, baseRequests.size)
    }

    @Test
    fun `completed onboarding rechecks revoked runtime permissions without reopening special settings`() {
        val granted = AppRuntimePermissionPolicy.initialPermissions(Build.VERSION_CODES.Q).toMutableSet().apply {
            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        val baseRequests = mutableListOf<List<String>>()
        val specialRequests = mutableListOf<FirstLaunchSpecialPermission>()
        val firstRun = FirstLaunchPermissionCoordinator(
            context = context,
            sdkInt = Build.VERSION_CODES.Q,
            permissionGranted = granted::contains,
            specialPermissionGranted = { true },
            launchRuntimePermissions = { baseRequests += it.toList() },
            launchBackgroundLocation = { error("background location should be granted") },
            launchSpecialPermissionSettings = { specialRequests += it; true },
        )
        firstRun.start()
        granted.remove(Manifest.permission.RECORD_AUDIO)

        val nextLaunch = FirstLaunchPermissionCoordinator(
            context = context,
            sdkInt = Build.VERSION_CODES.Q,
            permissionGranted = granted::contains,
            specialPermissionGranted = { false },
            launchRuntimePermissions = { baseRequests += it.toList() },
            launchBackgroundLocation = { error("background location should remain granted") },
            launchSpecialPermissionSettings = { specialRequests += it; true },
        )
        nextLaunch.start()
        assertEquals(listOf(Manifest.permission.RECORD_AUDIO), baseRequests.single())

        nextLaunch.onRuntimePermissionsResult(mapOf(Manifest.permission.RECORD_AUDIO to false))
        assertTrue(specialRequests.isEmpty())
    }

    @Test
    fun `already granted special permissions skip all system settings pages`() {
        val granted = AppRuntimePermissionPolicy.initialPermissions(Build.VERSION_CODES.Q).toSet() +
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        val specialRequests = mutableListOf<FirstLaunchSpecialPermission>()
        val coordinator = FirstLaunchPermissionCoordinator(
            context = context,
            sdkInt = Build.VERSION_CODES.Q,
            permissionGranted = granted::contains,
            specialPermissionGranted = { true },
            launchRuntimePermissions = { error("runtime permissions should be skipped") },
            launchBackgroundLocation = { error("background location should be skipped") },
            launchSpecialPermissionSettings = { specialRequests += it; true },
        )

        coordinator.start()

        assertTrue(specialRequests.isEmpty())
        coordinator.start()
        assertTrue(specialRequests.isEmpty())
    }

    private fun coordinator(
        granted: Set<String>,
        baseRequests: MutableList<List<String>>,
        backgroundRequests: MutableList<String>,
        specialRequests: MutableList<FirstLaunchSpecialPermission>,
    ) = FirstLaunchPermissionCoordinator(
        context = context,
        sdkInt = Build.VERSION_CODES.Q,
        permissionGranted = granted::contains,
        specialPermissionGranted = { false },
        launchRuntimePermissions = { baseRequests += it.toList() },
        launchBackgroundLocation = { backgroundRequests += it },
        launchSpecialPermissionSettings = { specialRequests += it; true },
    )
}
