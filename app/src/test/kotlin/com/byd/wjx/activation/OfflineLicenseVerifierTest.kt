package com.byd.wjx.activation

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class OfflineLicenseVerifierTest {
    private lateinit var keyPair: KeyPair
    private lateinit var verifier: OfflineLicenseVerifier

    @Before
    fun setUp() {
        keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        verifier = OfflineLicenseVerifier(
            packageName = PACKAGE_NAME,
            publicKeyDer = Base64.getEncoder().encodeToString(keyPair.public.encoded),
            currentTimeMillis = { NOW },
        )
    }

    @Test
    fun `accepts signed license for matching device alias`() {
        val license = signedLicense()

        val match = verifier.verify(license, listOf("$VIN ".lowercase()))

        assertEquals(VIN, match?.deviceCode)
    }

    @Test
    fun `rejects license after signed fields are changed`() {
        val license = signedLicense().put("features", JSONArray().put("cluster_projection"))

        assertNull(verifier.verify(license, listOf(VIN)))
    }

    @Test
    fun `rejects expired license`() {
        val license = signedLicense(expiresAt = NOW)

        assertNull(verifier.verify(license, listOf(VIN)))
    }

    @Test
    fun `rejects license for another package or vehicle`() {
        val otherPackage = signedLicense(packageName = "com.example.other")
        val otherVehicle = signedLicense()

        assertNull(verifier.verify(otherPackage, listOf(VIN)))
        assertNull(verifier.verify(otherVehicle, listOf("L0000000000000000")))
    }

    private fun signedLicense(
        packageName: String = PACKAGE_NAME,
        expiresAt: Long = NOW + 60_000L,
    ): JSONObject {
        val license = JSONObject()
            .put("version", 1)
            .put("signatureAlg", "RS256")
            .put("deviceCodes", JSONArray().put(VIN).put(VIN.lowercase()))
            .put("activated", true)
            .put("activatedAt", NOW - 10_000L)
            .put("issuedAt", NOW - 10_000L)
            .put("expiresAt", expiresAt)
            .put("packageName", packageName)
            .put("licenseCodeMasked", "STB-TEST-****-TEST")
            .put("features", JSONArray().put("led_linkage").put("desktop_lyrics"))
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(verifier.canonicalForSigning(license).toByteArray(Charsets.UTF_8))
        return license.put(
            "signature",
            Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()),
        )
    }

    companion object {
        private const val PACKAGE_NAME = "com.byd.wjx"
        private const val VIN = "L123456789ABCDEFG"
        private const val NOW = 1_800_000_000_000L
    }
}
