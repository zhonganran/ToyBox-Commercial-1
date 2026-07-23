package com.byd.wjx.vehicle

import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleProfileModelsTest {
    @Test
    fun `compatibility key is stable after trimming case and repeated spaces`() {
        val first = VehicleIdentityResolver.compatibilityKey(
            listOf("  SONG   PLUS ", "Sa3", " BYD-S3 ", "Song", "PHEV", "57", "57.1.3", "ABC"),
        )
        val second = VehicleIdentityResolver.compatibilityKey(
            listOf("song plus", "sa3", "byd-s3", "song", "phev", "57", "57.1.3", "abc"),
        )

        assertEquals(second, first)
        assertEquals(64, first.length)
    }

    @Test
    fun `identity reader uses all compatibility fields and hashes raw camera sort`() {
        val properties = mapOf(
            "persist.sys.byd.default_name" to "宋 PLUS DM-i",
            "persist.sys.model_variant.model" to "SA3HEV",
            "persist.sys.vehicle_40d_code" to "BYD-S3",
            "ro.build.car.series" to "SONG_PLUS",
            "ro.vehicle.type" to "PHEV",
            "apps.setting.product.outswver" to "57.1.3.2501010.1",
            "vendor.vehicle.config.cam_sort" to "rear,left,right,front",
        )

        val identity = VehicleIdentityResolver.read { properties[it].orEmpty() }

        assertEquals("宋 PLUS DM-i", identity.displayName)
        assertEquals(57, identity.controllerSeries)
        assertEquals("57.1.3", identity.controllerCompat)
        assertEquals(sha256("rear,left,right,front"), identity.cameraSourceFingerprint)
        assertTrue(identity.isUsable)
    }

    @Test
    fun `identity reader uses primary vehicle camera sort property`() {
        val properties = usableProperties().toMutableMap().apply {
            put("vehicle.config.cam_sort", "ims:0;pano_h:1;rear:11001")
        }

        val identity = VehicleIdentityResolver.read { properties[it].orEmpty() }

        assertEquals(sha256("ims:0;pano_h:1;rear:11001"), identity.cameraSourceFingerprint)
        assertTrue(identity.isUsable)
    }

    @Test
    fun `identity camera property order matches runtime discovery`() {
        val properties = usableProperties().toMutableMap().apply {
            put("vehicle.config.cam_sort", "pano_h:1;dms:0")
            put("vendor.vehicle.config.cam_sort", "pano_h:3;dms:1")
        }

        val identity = VehicleIdentityResolver.read { properties[it].orEmpty() }

        assertEquals(sha256("pano_h:1;dms:0"), identity.cameraSourceFingerprint)
    }

    @Test
    fun `identity falls back to vendor property when primary lacks panorama mapping`() {
        val properties = usableProperties().toMutableMap().apply {
            put("vehicle.config.cam_sort", "dms:0")
            put("vendor.vehicle.config.cam_sort", "pano_h:3;dms:1")
        }

        val identity = VehicleIdentityResolver.read { properties[it].orEmpty() }

        assertEquals(sha256("pano_h:3;dms:1"), identity.cameraSourceFingerprint)
    }

    @Test
    fun `profile parser rejects camera ids outside supported zero to three range`() {
        val root = validResponse().apply {
            getJSONObject("profile").put("cameraId", 4)
        }

        val result = runCatching { VehicleProfileJson.parse(root) }

        assertTrue(result.isFailure)
    }

    @Test
    fun `profile parser accepts exact verified mapping`() {
        val result = VehicleProfileJson.parse(validResponse())

        assertEquals("matched", result.status)
        assertEquals("2026款", result.salesModel)
        assertEquals(2, result.profile?.cameraId)
        assertEquals("SOURCE_C", result.profile?.rearSource?.name)
    }

    private fun validResponse() = JSONObject().apply {
        put("success", true)
        put("profileStatus", "matched")
        put("salesModel", "2026款")
        put("profile", JSONObject().apply {
            put("profileId", "mirror-song-plus-57")
            put("version", 1)
            put("compatibilityKey", "a".repeat(64))
            put("cameraId", 2)
            put("surfaceIndex", 0)
            put("leftSource", "SOURCE_A")
            put("rightSource", "SOURCE_B")
            put("rearSource", "SOURCE_C")
            put("updatedAt", "2026-07-23T12:00:00Z")
        })
    }

    private fun usableProperties() = mapOf(
        "persist.sys.byd.default_name" to "宋 PLUS DM-i",
        "persist.sys.model_variant.model" to "SA3HEV",
        "persist.sys.vehicle_40d_code" to "BYD-S3",
        "ro.build.car.series" to "SONG_PLUS",
        "ro.vehicle.type" to "PHEV",
        "apps.setting.product.outswver" to "57.1.3.2501010.1",
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
