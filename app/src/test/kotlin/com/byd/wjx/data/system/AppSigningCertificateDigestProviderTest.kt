package com.byd.wjx.data.system

import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppSigningCertificateDigestProviderTest {
    @Test
    fun `digest singleCertificate usesSha256`() {
        val certificate = "release-certificate".toByteArray()

        assertArrayEquals(
            MessageDigest.getInstance("SHA-256").digest(certificate),
            AppSigningCertificateDigestProvider.digest(listOf(certificate)),
        )
    }

    @Test
    fun `digest multipleCertificates isOrderIndependentAndComplete`() {
        val first = "certificate-a".toByteArray()
        val second = "certificate-b".toByteArray()
        val combined = AppSigningCertificateDigestProvider.digest(listOf(first, second))

        assertArrayEquals(
            combined,
            AppSigningCertificateDigestProvider.digest(listOf(second, first)),
        )
        assertFalse(combined.contentEquals(AppSigningCertificateDigestProvider.digest(listOf(first))))
    }
}
