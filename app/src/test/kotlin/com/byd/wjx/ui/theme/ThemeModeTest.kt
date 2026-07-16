package com.byd.wjx.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `stored theme values parse with system fallback`() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStored("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStored("dark"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("system"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("unknown"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
    }

    @Test
    fun `stored wallpaper values parse with default fallback`() {
        AppWallpaper.entries.forEach { wallpaper ->
            assertEquals(wallpaper, AppWallpaper.fromStored(wallpaper.storedValue))
        }
        assertEquals(AppWallpaper.Default, AppWallpaper.fromStored("unknown"))
        assertEquals(AppWallpaper.Default, AppWallpaper.fromStored(null))
    }
}
