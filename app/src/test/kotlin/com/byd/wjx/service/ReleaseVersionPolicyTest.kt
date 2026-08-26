package com.byd.wjx.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionPolicyTest {
    @Test
    fun `release versions are semantic and filename safe`() {
        assertTrue(isValidReleaseVersion("2.1.20"))
        assertTrue(isValidReleaseVersion("99.0-test"))
        assertTrue(isValidReleaseVersion("3.0.0-rc.1"))

        assertFalse(isValidReleaseVersion("2"))
        assertFalse(isValidReleaseVersion("v2.1.20"))
        assertFalse(isValidReleaseVersion("../../payload"))
        assertFalse(isValidReleaseVersion("2.1.20/evil"))
        assertFalse(isValidReleaseVersion("2.1.20 beta"))
        assertFalse(isValidReleaseVersion("2..20"))
    }

    @Test
    fun `sha256 values must already be exactly 64 hexadecimal characters`() {
        assertEquals("a".repeat(64), normalizedSha256OrNull("A".repeat(64)))
        assertNull(normalizedSha256OrNull(" a".repeat(32)))
        assertNull(normalizedSha256OrNull("a".repeat(63)))
        assertNull(normalizedSha256OrNull("g".repeat(64)))
        assertNull(normalizedSha256OrNull("sha256:" + "a".repeat(64)))
    }
}
