package com.byd.wjx.service.runtime

import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.clustercard.ClusterCardRuntime
import com.byd.wjx.data.automation.NetworkAvailableMonitor
import com.byd.wjx.desktopcard.DesktopQuickCardRuntime
import com.byd.wjx.led.ipixelfa.IpixelFaRuntimeCoordinator
import com.byd.wjx.led.mafu.MafuRuntimeCoordinator
import com.byd.wjx.lyrics.LyricRuntime
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TrackingFeatureRuntimesTest {

    @Test
    fun `startup phases and stop are idempotent`() = runTest {
        val fixture = fixture(ledAllowed = true)

        fixture.runtimes.startBeforePolling(this)
        fixture.runtimes.startBeforePolling(this)
        fixture.runtimes.startAfterPolling(this)
        fixture.runtimes.startAfterPolling(this)
        fixture.runtimes.stop()

        verify(exactly = 1) { fixture.steering.start(any()) }
        verify(exactly = 1) { fixture.network.start() }
        verify(exactly = 1) { fixture.camera.start(any()) }
        verify(exactly = 1) { fixture.desktop.start(any()) }
        verify(exactly = 1) { fixture.cluster.start(any()) }
        verify(exactly = 1) { fixture.lyrics.start(fixture.cameraActive) }
        verify(exactly = 1) { fixture.lyrics.stop() }
        verify(exactly = 1) { fixture.network.stop() }
    }

    @Test
    fun `LED runtimes follow activation transitions without duplicate starts`() = runTest {
        val fixture = fixture(ledAllowed = false)

        fixture.runtimes.refreshLedAccess(this)
        fixture.ledAllowed.value = true
        fixture.runtimes.refreshLedAccess(this)
        fixture.runtimes.refreshLedAccess(this)
        fixture.ledAllowed.value = false
        fixture.runtimes.refreshLedAccess(this)

        verify(exactly = 1) { fixture.ipixel.start(any()) }
        verify(exactly = 1) { fixture.mafu.start(any()) }
        verify(exactly = 1) { fixture.ipixel.stop() }
        verify(exactly = 1) { fixture.mafu.stop() }
    }

    private fun fixture(ledAllowed: Boolean): Fixture {
        val steering = mockk<SteeringWheelRuntimeCoordinator>(relaxed = true)
        val network = mockk<NetworkAvailableMonitor>(relaxed = true)
        val camera = mockk<CameraRuntimeCoordinator>(relaxed = true)
        val cameraActive = MutableStateFlow(false)
        every { camera.active } returns cameraActive
        val desktop = mockk<DesktopQuickCardRuntime>(relaxed = true)
        val cluster = mockk<ClusterCardRuntime>(relaxed = true)
        val lyrics = mockk<LyricRuntime>(relaxed = true)
        val ipixel = mockk<IpixelFaRuntimeCoordinator>(relaxed = true)
        val mafu = mockk<MafuRuntimeCoordinator>(relaxed = true)
        val activation = mockk<ActivationManager>()
        val allowed = MutableStateFlow(ledAllowed)
        every { activation.canUse(PremiumFeature.LED_LINKAGE) } answers { allowed.value }
        return Fixture(
            runtimes = TrackingFeatureRuntimes(
                steeringWheel = steering,
                networkMonitor = network,
                camera = camera,
                desktopCard = desktop,
                clusterCard = cluster,
                lyrics = lyrics,
                ipixelFa = ipixel,
                mafu = mafu,
                activationManager = activation,
            ),
            steering = steering,
            network = network,
            camera = camera,
            cameraActive = cameraActive,
            desktop = desktop,
            cluster = cluster,
            lyrics = lyrics,
            ipixel = ipixel,
            mafu = mafu,
            ledAllowed = allowed,
        )
    }

    private data class Fixture(
        val runtimes: TrackingFeatureRuntimes,
        val steering: SteeringWheelRuntimeCoordinator,
        val network: NetworkAvailableMonitor,
        val camera: CameraRuntimeCoordinator,
        val cameraActive: MutableStateFlow<Boolean>,
        val desktop: DesktopQuickCardRuntime,
        val cluster: ClusterCardRuntime,
        val lyrics: LyricRuntime,
        val ipixel: IpixelFaRuntimeCoordinator,
        val mafu: MafuRuntimeCoordinator,
        val ledAllowed: MutableStateFlow<Boolean>,
    )
}
