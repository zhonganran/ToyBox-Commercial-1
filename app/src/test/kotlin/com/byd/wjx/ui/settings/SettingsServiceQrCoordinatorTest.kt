package com.byd.wjx.ui.settings

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsServiceQrCoordinatorTest {
    @Test
    fun `metadata refresh emits cached loading state before fresh data`() = runTest {
        val repository = mockk<ServiceQrRepository>()
        val cached = mapOf("wechat" to qrCode("cached"))
        val refreshed = mapOf("wechat" to qrCode("fresh"))
        every { repository.cached() } returns cached
        coEvery { repository.refresh() } returns refreshed

        val updates = SettingsServiceQrCoordinator(repository)
            .refreshUpdates(emptyMap())
            .toList()

        assertEquals(2, updates.size)
        assertTrue(updates[0].loading)
        assertEquals(cached, updates[0].codes)
        assertFalse(updates[1].loading)
        assertEquals(refreshed, updates[1].codes)
        assertEquals("二维码已更新", updates[1].message)
    }

    @Test
    fun `metadata failure keeps cache and only marks empty fallback as error`() = runTest {
        val repository = mockk<ServiceQrRepository>()
        val cached = mapOf("wechat" to qrCode("cached"))
        every { repository.cached() } returns cached
        coEvery { repository.refresh() } throws IllegalStateException("offline")

        val cachedFailure = SettingsServiceQrCoordinator(repository)
            .refreshUpdates(emptyMap())
            .toList()
            .last()

        assertEquals(cached, cachedFailure.codes)
        assertFalse(cachedFailure.messageIsError)
        assertEquals("网络不可用，已显示缓存二维码", cachedFailure.message)

        every { repository.cached() } returns emptyMap()
        val emptyFailure = SettingsServiceQrCoordinator(repository)
            .refreshUpdates(emptyMap())
            .toList()
            .last()
        assertTrue(emptyFailure.messageIsError)
        assertTrue(emptyFailure.message.orEmpty().contains("offline"))
    }

    @Test
    fun `image updates expose cache and retain it when refresh fails`() = runTest {
        val repository = mockk<ServiceQrRepository>()
        val cached = byteArrayOf(1, 2, 3)
        coEvery { repository.cachedImage("wechat", any()) } returns cached
        coEvery { repository.refreshImage("wechat", any()) } throws IllegalStateException("offline")

        val updates = SettingsServiceQrCoordinator(repository)
            .imageUpdates("wechat", "https://api.example.test/qr")
            .toList()

        assertEquals(1, updates.size)
        assertArrayEquals(cached, (updates.single() as ServiceQrImageUpdate.Available).bytes)
    }

    private fun qrCode(title: String) = ServiceQrCode(
        key = "wechat",
        title = title,
        subtitle = "",
        description = "",
        imageUrl = "https://api.example.test/qr",
        expiresAt = "",
        updatedAt = "",
    )
}
