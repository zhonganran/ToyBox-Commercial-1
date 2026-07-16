package com.byd.wjx.data.repository

import com.byd.wjx.data.local.dao.SettingsDao
import com.byd.wjx.data.local.entity.SettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppWallpaperPreferenceTest {

    private class FakeSettingsDao(initial: Map<String, String> = emptyMap()) : SettingsDao {
        private val values = initial.toMutableMap()
        private val streams = mutableMapOf<String, MutableStateFlow<String?>>()

        override suspend fun get(key: String): String? = values[key]

        override fun observe(key: String): Flow<String?> =
            streams.getOrPut(key) { MutableStateFlow(values[key]) }

        override suspend fun set(entity: SettingEntity) {
            values[entity.key] = entity.value ?: ""
            streams.getOrPut(entity.key) { MutableStateFlow(null) }.value = entity.value
        }

        override suspend fun setAll(settings: List<SettingEntity>) {
            settings.forEach { set(it) }
        }

        override fun getAll(): Flow<List<SettingEntity>> = MutableStateFlow(emptyList())
    }

    @Test
    fun `wallpaper defaults and invalid values stay inside built in catalog`() = runTest {
        val emptyRepository = SettingsRepository(FakeSettingsDao())
        assertEquals(SettingsRepository.DEFAULT_APP_WALLPAPER, emptyRepository.getAppWallpaper())

        val invalidRepository = SettingsRepository(
            FakeSettingsDao(mapOf(SettingsRepository.KEY_APP_WALLPAPER to "missing")),
        )
        assertEquals(SettingsRepository.DEFAULT_APP_WALLPAPER, invalidRepository.getAppWallpaper())
    }

    @Test
    fun `selected wallpaper persists`() = runTest {
        val repository = SettingsRepository(FakeSettingsDao())

        repository.setAppWallpaper("ink_mountains")

        assertEquals("ink_mountains", repository.getAppWallpaper())
    }
}
