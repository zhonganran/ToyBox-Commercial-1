package com.byd.wjx.camera.blindspot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BlindSpotRuntimeStateTest {
    @Test
    fun `settings changes publish immediately to the active calibration preview`() {
        BlindSpotRuntimeState.stopCalibration()
        val config = BlindSpotPreferences.defaultSideConfig(BlindSpotSide.LEFT).copy(
            calibration = BlindSpotPreferences.defaultCalibration().copy(
                dewarpEnabled = true,
                rotationDegrees = 15,
            ),
        )

        BlindSpotRuntimeState.requestCalibration(
            BlindSpotSide.LEFT,
            10,
            20,
            800,
            500,
            editingCrop = true,
        )
        BlindSpotRuntimeState.publishCalibrationPreview(config)

        assertEquals(config, BlindSpotRuntimeState.calibrationPreviewUpdate.value?.config)
        assertTrue(BlindSpotRuntimeState.calibrationViewport.value.editingCrop)
        BlindSpotRuntimeState.stopCalibration()
        assertNull(BlindSpotRuntimeState.calibrationPreviewUpdate.value)
    }

    @Test
    fun `stopping calibration clears the last crop gesture`() {
        BlindSpotRuntimeState.requestCalibration(
            BlindSpotSide.RIGHT,
            0,
            0,
            640,
            400,
            editingCrop = true,
        )
        BlindSpotRuntimeState.publishCalibrationCropEdit(
            side = BlindSpotSide.RIGHT,
            corrected = false,
            crop = BlindSpotCrop(10, 15, 70, 60),
            committed = true,
        )

        BlindSpotRuntimeState.stopCalibration()

        assertNull(BlindSpotRuntimeState.calibrationCropEdit.value)
    }

    @Test
    fun `active calibration draft replaces only the side being edited`() {
        val snapshot = defaultSnapshot()
        val draft = snapshot.right.copy(
            calibration = snapshot.right.calibration.copy(
                cropLeftPct = 12,
                cropTopPct = 18,
            ),
        )
        val viewport = BlindSpotCalibrationViewport(
            side = BlindSpotSide.RIGHT,
            width = 800,
            height = 500,
            editingCrop = true,
        )

        val result = blindSpotSnapshotWithCalibrationDraft(snapshot, viewport, draft)

        assertSame(snapshot.left, result.left)
        assertEquals(draft, result.right)
        assertSame(snapshot.rear, result.rear)
    }

    @Test
    fun `draft from another side cannot replace the active preview`() {
        val snapshot = defaultSnapshot()
        val viewport = BlindSpotCalibrationViewport(
            side = BlindSpotSide.LEFT,
            width = 800,
            height = 500,
        )

        assertSame(
            snapshot,
            blindSpotSnapshotWithCalibrationDraft(snapshot, viewport, snapshot.right),
        )
    }

    private fun defaultSnapshot() = BlindSpotPreferenceSnapshot(
        enabled = true,
        minSpeedKmh = BlindSpotPreferences.DEFAULT_MIN_SPEED_KMH,
        maxSpeedKmh = BlindSpotPreferences.DEFAULT_MAX_SPEED_KMH,
        bsdGlow = true,
        bsdOnly = false,
        bsdAutoPopup = false,
        bsdAutoCooldownSeconds = BlindSpotPreferences.DEFAULT_BSD_AUTO_COOLDOWN_SECONDS,
        sharpTurnEnabled = false,
        sharpTurnAngleDeg = BlindSpotPreferences.DEFAULT_SHARP_TURN_ANGLE_DEG,
        rearStreamEnabled = false,
        left = BlindSpotPreferences.defaultSideConfig(BlindSpotSide.LEFT),
        right = BlindSpotPreferences.defaultSideConfig(BlindSpotSide.RIGHT),
        rear = BlindSpotPreferences.defaultSideConfig(BlindSpotSide.REAR),
    )
}
