package com.byd.wjx.service.runtime

import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.camera.blindspot.BlindSpotController
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
        verify(exactly = 1) { fixture.blindSpot.start(fixture.cameraActive) }
        verify(exactly = 1) { fixture.desktop.start(any()) }
        verify(exactly = 1) { fixture.cluster.start(any()) }
        verify(exactly = 1) { fixture.lyrics.start(fixture.cameraActive) }
        verify(exactly = 1) { fixture.lyrics.stop() }
        verify(exactly = 1) { fixture.blindSpot.stop() }
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

    @Test
    fun `wake requests reconnect for both active LED runtimes`() = runTest {
        val fixture = fixture(ledAllowed = true)

        fixture.runtimes.refreshLedAccess(this)
        fixture.runtimes.requestLedReconnect("broadcast:android.intent.action.SCREEN_ON")
        fixture.runtimes.requestLedReconnect("broadcast:android.intent.action.USER_PRESENT")

        verify(exactly = 1) { fixture.ipixel.start(any()) }
        verify(exactly = 1) { fixture.mafu.start(any()) }
        verify(exactly = 1) {
            fixture.ipixel.requestReconnect("broadcast:android.intent.action.SCREEN_ON")
        }
        verify(exactly = 1) {
            fixture.ipixel.requestReconnect("broadcast:android.intent.action.USER_PRESENT")
        }
        verify(exactly = 1) {
            fixture.mafu.requestReconnect("broadcast:android.intent.action.SCREEN_ON")
        }
        verify(exactly = 1) {
            fixture.mafu.requestReconnect("broadcast:android.intent.action.USER_PRESENT")
        }
    }

    @Test
    fun `wake does not request reconnect while LED feature is locked`() = runTest {
        val fixture = fixture(ledAllowed = false)

        fixture.runtimes.refreshLedAccess(this)
        fixture.runtimes.requestLedReconnect("broadcast:android.intent.action.BOOT_COMPLETED")

        verify(exactly = 0) { fixture.ipixel.requestReconnect(any()) }
        verify(exactly = 0) { fixture.mafu.requestReconnect(any()) }
    }

    @Test
    fun `electronic rearview uses its own activation feature`() = runTest {
        val fixture = fixture(ledAllowed = true, rearviewAllowed = false)

        fixture.runtimes.onActionData(mockk(relaxed = true))

        verify(exactly = 1) {
            fixture.activation.canUse(PremiumFeature.ELECTRONIC_REARVIEW)
        }
        verify(exactly = 1) { fixture.blindSpot.onPollSnapshot(any(), accessAllowed = false) }
        verify(exactly = 0) {
            fixture.activation.canUse(PremiumFeature.CLUSTER_PROJECTION)
        }
    }

    private fun fixture(ledAllowed: Boolean, rearviewAllowed: Boolean = true): Fixture {
        val steering = mockk<SteeringWheelRuntimeCoordinator>(relaxed = true)
        val network = mockk<NetworkAvailableMonitor>(relaxed = true)
        val camera = mockk<CameraRuntimeCoordinator>(relaxed = true)
        val blindSpot = mockk<BlindSpotController>(relaxed = true)
        val cameraActive = MutableStateFlow(false)
        every { camera.active } returns cameraActive
        val desktop = mockk<DesktopQuickCardRuntime>(relaxed = true)
        val cluster = mockk<ClusterCardRuntime>(relaxed = true)
        val lyrics = mockk<LyricRuntime>(relaxed = true)
        val ipixel = mockk<IpixelFaRuntimeCoordinator>(relaxed = true)
        val mafu = mockk<MafuRuntimeCoordinator>(relaxed = true)
        val activation = mockk<ActivationManager>()
        val allowed = MutableStateFlow(ledAllowed)
        every { activation.canUse(any()) } answers {
            when (invocation.args.first()) {
                PremiumFeature.LED_LINKAGE -> allowed.value
                PremiumFeature.ELECTRONIC_REARVIEW -> rearviewAllowed
                else -> true
            }
        }
        return Fixture(
            runtimes = TrackingFeatureRuntimes(
                steeringWheel = steering,
                networkMonitor = network,
                camera = camera,
                blindSpot = blindSpot,
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
            blindSpot = blindSpot,
            cameraActive = cameraActive,
            desktop = desktop,
            cluster = cluster,
            lyrics = lyrics,
            ipixel = ipixel,
            mafu = mafu,
            activation = activation,
            ledAllowed = allowed,
        )
    }

    private data class Fixture(
        val runtimes: TrackingFeatureRuntimes,
        val steering: SteeringWheelRuntimeCoordinator,
        val network: NetworkAvailableMonitor,
        val camera: CameraRuntimeCoordinator,
        val blindSpot: BlindSpotController,
        val cameraActive: MutableStateFlow<Boolean>,
        val desktop: DesktopQuickCardRuntime,
        val cluster: ClusterCardRuntime,
        val lyrics: LyricRuntime,
        val ipixel: IpixelFaRuntimeCoordinator,
        val mafu: MafuRuntimeCoordinator,
        val activation: ActivationManager,
        val ledAllowed: MutableStateFlow<Boolean>,
    )
}
