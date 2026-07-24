package com.byd.wjx.electronicmirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElectronicMirrorSceneArbiterTest {
    private val enabled = ElectronicMirrorConfig(enabled = true, reverseEnabled = true)

    @Test
    fun `original vehicle camera always owns the screen first`() {
        val scene = ElectronicMirrorSceneArbiter.resolve(
            enabled,
            ElectronicMirrorSignals(turnSignalFlash = 2, gear = 2, originalCameraActive = true),
            manualPreview = ElectronicMirrorViewpoint.RIGHT,
        )

        assertNull(scene)
    }

    @Test
    fun `auto parking fid blocks mirror before foreground monitor catches up`() {
        val scene = ElectronicMirrorSceneArbiter.resolve(
            enabled,
            ElectronicMirrorSignals(
                turnSignalFlash = 4,
                gear = 4,
                originalCameraActive = false,
                autoParkingActive = true,
            ),
        )

        assertNull(scene)
    }

    @Test
    fun `reverse is above turn signal within electronic mirror`() {
        val scene = ElectronicMirrorSceneArbiter.resolve(
            enabled,
            ElectronicMirrorSignals(turnSignalFlash = 4, gear = 2, originalCameraActive = false),
        )

        assertEquals(ElectronicMirrorViewpoint.REAR, scene?.viewpoint)
        assertEquals(ElectronicMirrorReason.REVERSE, scene?.reason)
    }

    @Test
    fun `stable left and right enums select their matching side views`() {
        val left = ElectronicMirrorSceneArbiter.resolve(
            enabled,
            ElectronicMirrorSignals(turnSignalFlash = 2, gear = 4, originalCameraActive = false),
        )
        val right = ElectronicMirrorSceneArbiter.resolve(
            enabled,
            ElectronicMirrorSignals(turnSignalFlash = 4, gear = 4, originalCameraActive = false),
        )

        assertEquals(ElectronicMirrorViewpoint.LEFT, left?.viewpoint)
        assertEquals(ElectronicMirrorViewpoint.RIGHT, right?.viewpoint)
    }

    @Test
    fun `side windows allow full display geometry`() {
        val scene = ElectronicMirrorSceneArbiter.resolve(
            enabled.copy(
                displayTarget = ElectronicMirrorDisplayTarget.CENTER,
                centerGeometry = ElectronicMirrorGeometry(100, 100, 0, 60),
            ),
            ElectronicMirrorSignals(turnSignalFlash = 2, gear = 4, originalCameraActive = false),
        )

        assertEquals(100, scene?.geometry?.widthPct)
        assertEquals(100, scene?.geometry?.heightPct)
    }

    @Test
    fun `hazard opens both side views from the combined turn signal value`() {
        val scenes = ElectronicMirrorSceneArbiter.resolveAll(
            enabled,
            ElectronicMirrorSignals(turnSignalFlash = 6, gear = 4, originalCameraActive = false),
        )

        assertEquals(
            listOf(ElectronicMirrorReason.LEFT_TURN, ElectronicMirrorReason.RIGHT_TURN),
            scenes.map(ElectronicMirrorScene::reason),
        )
    }

    @Test
    fun `off does not borrow independent lamp fallback data`() {
        assertTrue(
            ElectronicMirrorSceneArbiter.resolveAll(
                enabled,
                ElectronicMirrorSignals(turnSignalFlash = 1, gear = 4, originalCameraActive = false),
            ).isEmpty(),
        )
    }

    @Test
    fun `manual preview works while automation master is disabled`() {
        val scene = ElectronicMirrorSceneArbiter.resolve(
            ElectronicMirrorConfig(enabled = false),
            ElectronicMirrorSignals(turnSignalFlash = null, gear = null, originalCameraActive = false),
            manualPreview = ElectronicMirrorViewpoint.LEFT,
        )

        assertEquals(ElectronicMirrorReason.MANUAL_PREVIEW, scene?.reason)
        assertEquals(ElectronicMirrorViewpoint.LEFT, scene?.viewpoint)
    }

    @Test
    fun `scene carries projection for its selected viewpoint`() {
        val rightProjection = ElectronicMirrorProjectionSpec(horizontalPermille = 120)
        val scene = ElectronicMirrorSceneArbiter.resolve(
            ElectronicMirrorConfig(
                enabled = true,
                rightProjection = rightProjection,
            ),
            ElectronicMirrorSignals(turnSignalFlash = 4, gear = 4, originalCameraActive = false),
        )

        assertEquals(rightProjection, scene?.projection)
    }

    @Test
    fun `every logical viewpoint selects a tile from the same panorama Surface`() {
        val config = ElectronicMirrorConfig(enabled = true, cameraIdOverride = 3)
            .withSource(ElectronicMirrorViewpoint.RIGHT, ElectronicMirrorCameraSource.SOURCE_D)
        val scene = ElectronicMirrorSceneArbiter.resolve(
            config,
            ElectronicMirrorSignals(turnSignalFlash = 4, gear = 4, originalCameraActive = false),
        )

        assertEquals(ElectronicMirrorViewpoint.RIGHT, scene?.viewpoint)
        assertEquals(ElectronicMirrorCameraSource.SOURCE_D, scene?.source)
        assertEquals(3, scene?.tileIndex)
        assertEquals(0, scene?.helperSurfaceIndex)
        assertEquals(4, scene?.surfaceProfile?.tileCount)
        assertEquals("pano_h", scene?.surfaceProfile?.cameraTag)
        assertEquals(3, scene?.surfaceProfile?.cameraIdOverride)
    }

    @Test
    fun `streaming rear uses panorama strip and mapped rear tile`() {
        val scene = ElectronicMirrorSceneArbiter.resolve(
            ElectronicMirrorConfig(
                streamingRearEnabled = true,
                streamingRearDisplayTarget = ElectronicMirrorDisplayTarget.CENTER,
            ),
            ElectronicMirrorSignals(null, 4, originalCameraActive = false),
        )

        assertEquals(ElectronicMirrorReason.STREAMING_REAR, scene?.reason)
        assertEquals(ElectronicMirrorViewpoint.REAR, scene?.viewpoint)
        assertEquals(4, scene?.surfaceProfile?.tileCount)
        assertEquals(ElectronicMirrorCameraSource.SOURCE_C.stripTileIndex, scene?.tileIndex)
        assertEquals("pano_h", scene?.surfaceProfile?.cameraTag)
        assertEquals(0, scene?.helperSurfaceIndex)
        assertEquals(5120, scene?.previewBufferWidth)
        assertEquals(867, scene?.previewBufferHeight)
        assertEquals(ElectronicMirrorDisplayTarget.CENTER, scene?.target)
        assertTrue(scene?.mirrorHorizontally == true)
    }

    @Test
    fun `hidden streaming rear does not create a scene`() {
        val scene = ElectronicMirrorSceneArbiter.resolve(
            ElectronicMirrorConfig(
                streamingRearEnabled = true,
                streamingRearVisible = false,
            ),
            ElectronicMirrorSignals(null, 4, originalCameraActive = false),
        )

        assertNull(scene)
    }

    @Test
    fun `hidden streaming rear temporarily joins an active left turn`() {
        val scenes = ElectronicMirrorSceneArbiter.resolveAll(
            ElectronicMirrorConfig(
                enabled = true,
                streamingRearEnabled = true,
                streamingRearVisible = false,
            ),
            ElectronicMirrorSignals(TURN_LEFT, 4, originalCameraActive = false),
        )

        assertEquals(
            listOf(ElectronicMirrorReason.LEFT_TURN, ElectronicMirrorReason.STREAMING_REAR),
            scenes.map(ElectronicMirrorScene::reason),
        )
    }

    @Test
    fun `turn view changes only the strip tile then streaming rear resumes`() {
        val config = ElectronicMirrorConfig(enabled = true, streamingRearEnabled = true)
        val turn = ElectronicMirrorSceneArbiter.resolve(
            config,
            ElectronicMirrorSignals(TURN_LEFT, 4, originalCameraActive = false),
        )
        val resumed = ElectronicMirrorSceneArbiter.resolve(
            config,
            ElectronicMirrorSignals(null, 4, originalCameraActive = false),
        )

        assertEquals(ElectronicMirrorReason.LEFT_TURN, turn?.reason)
        assertEquals(ElectronicMirrorCameraSource.SOURCE_A.stripTileIndex, turn?.tileIndex)
        assertEquals(0, turn?.helperSurfaceIndex)
        assertFalse(turn?.mirrorHorizontally == true)
        assertEquals(ElectronicMirrorReason.STREAMING_REAR, resumed?.reason)
    }

    @Test
    fun `left turn and streaming rear use two simultaneous outputs`() {
        val scenes = ElectronicMirrorSceneArbiter.resolveAll(
            ElectronicMirrorConfig(enabled = true, streamingRearEnabled = true),
            ElectronicMirrorSignals(TURN_LEFT, 4, originalCameraActive = false),
        )

        assertEquals(
            listOf(ElectronicMirrorReason.LEFT_TURN, ElectronicMirrorReason.STREAMING_REAR),
            scenes.map(ElectronicMirrorScene::reason),
        )
        assertEquals(1, scenes.map(ElectronicMirrorScene::surfaceProfile).distinct().size)
    }

    @Test
    fun `hazard and streaming rear use three simultaneous outputs`() {
        val scenes = ElectronicMirrorSceneArbiter.resolveAll(
            ElectronicMirrorConfig(enabled = true, streamingRearEnabled = true),
            ElectronicMirrorSignals(6, 4, originalCameraActive = false),
        )

        assertEquals(
            listOf(
                ElectronicMirrorReason.LEFT_TURN,
                ElectronicMirrorReason.RIGHT_TURN,
                ElectronicMirrorReason.STREAMING_REAR,
            ),
            scenes.map(ElectronicMirrorScene::reason),
        )
        assertEquals(1, scenes.map(ElectronicMirrorScene::surfaceProfile).distinct().size)
    }

    @Test
    fun `configured quality width is shared by every visible output`() {
        val scenes = ElectronicMirrorSceneArbiter.resolveAll(
            ElectronicMirrorConfig(
                enabled = true,
                streamingRearEnabled = true,
                qualityWidth = 960,
            ),
            ElectronicMirrorSignals(TURN_LEFT, 4, originalCameraActive = false),
        )

        assertEquals(listOf(960, 960), scenes.map { it.surfaceProfile.qualityWidth })
        assertEquals(listOf(3840, 3840), scenes.map(ElectronicMirrorScene::previewBufferWidth))
    }

    private companion object {
        const val TURN_LEFT = ElectronicMirrorSceneArbiter.TURN_LEFT
    }
}
