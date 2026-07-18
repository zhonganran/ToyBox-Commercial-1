package com.byd.wjx.desktopcard

import com.byd.wjx.data.foreground.ForegroundApp
import com.byd.wjx.ui.overlay.OverlayDragRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopQuickCardModelsTest {
    @Test
    fun `fresh install is disabled and starts with empty sections`() {
        val config = DesktopQuickCardConfig()

        assertFalse(config.enabled)
        assertTrue(config.functionCommands.isEmpty())
        assertTrue(config.soundPaths.isEmpty())
        assertEquals(DESKTOP_QUICK_CARD_DEFAULT_SECTION_GAP_DP, config.sectionGapDp)
    }

    @Test
    fun `vehicle control catalog includes both electric sliding doors`() {
        val left = DesktopQuickCardCommandCatalog.byCommand.getValue(DESKTOP_QUICK_CARD_LEFT_SLIDING_DOOR)
        val right = DesktopQuickCardCommandCatalog.byCommand.getValue(DESKTOP_QUICK_CARD_RIGHT_SLIDING_DOOR)

        assertEquals(DesktopQuickCardCommandKind.AUTO_DOOR, left.kind)
        assertEquals("slide_l", left.payload)
        assertEquals("电滑门", left.category)
        assertEquals(DesktopQuickCardCommandKind.AUTO_DOOR, right.kind)
        assertEquals("slide_r", right.payload)
    }

    @Test
    fun `sanitize removes duplicates invalid entries and caps both sections`() {
        val raw = DesktopQuickCardConfig(
            functionCommands = listOf("a", "a", "bad", "b", "c", "d", "e", "f", "g", "h", "i"),
            soundPaths = listOf("/1", "/1", "/bad", "/2", "/3", "/4", "/5", "/6", "/7", "/8", "/9"),
            scale = 3f,
            sectionGapDp = -4f,
            x = -1,
            y = -2,
        )

        val result = sanitizeDesktopQuickCardConfig(
            raw = raw,
            validCommands = ('a'..'i').map(Char::toString).toSet(),
            validSoundPaths = (1..9).map { "/$it" }.toSet(),
        )

        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h"), result.functionCommands)
        assertEquals(listOf("/1", "/2", "/3", "/4", "/5", "/6", "/7", "/8"), result.soundPaths)
        assertEquals(DESKTOP_QUICK_CARD_MAX_SCALE, result.scale)
        assertEquals(DESKTOP_QUICK_CARD_MIN_SECTION_GAP_DP, result.sectionGapDp)
        assertEquals(0, result.x)
        assertEquals(0, result.y)
    }

    @Test
    fun `grid uses four columns and at most two rows`() {
        assertEquals(0, DesktopQuickCardGrid.rowsFor(0))
        assertEquals(1, DesktopQuickCardGrid.rowsFor(1))
        assertEquals(1, DesktopQuickCardGrid.rowsFor(4))
        assertEquals(2, DesktopQuickCardGrid.rowsFor(5))
        assertEquals(2, DesktopQuickCardGrid.rowsFor(20))
    }

    @Test
    fun `visibility is home only by default and always yields to camera and own app`() {
        val base = DesktopQuickCardConfig(enabled = true, functionCommands = listOf("a"))
        val homes = setOf("com.vendor.home")

        assertTrue(
            DesktopQuickCardVisibility.shouldShow(
                config = base,
                foreground = ForegroundApp("com.vendor.home"),
                homePackages = homes,
                ownPackage = "com.byd.wjx",
                cameraActive = false,
            )
        )
        assertFalse(
            DesktopQuickCardVisibility.shouldShow(
                config = base,
                foreground = ForegroundApp("com.other.app"),
                homePackages = homes,
                ownPackage = "com.byd.wjx",
                cameraActive = false,
            )
        )
        assertTrue(
            DesktopQuickCardVisibility.shouldShow(
                config = base.copy(allowOverOtherApps = true),
                foreground = ForegroundApp("com.other.app"),
                homePackages = homes,
                ownPackage = "com.byd.wjx",
                cameraActive = false,
            )
        )
        assertFalse(
            DesktopQuickCardVisibility.shouldShow(
                config = base.copy(allowOverOtherApps = true),
                foreground = ForegroundApp("com.byd.wjx"),
                homePackages = homes,
                ownPackage = "com.byd.wjx",
                cameraActive = false,
            )
        )
        assertFalse(
            DesktopQuickCardVisibility.shouldShow(
                config = base,
                foreground = ForegroundApp("com.vendor.home"),
                homePackages = homes,
                ownPackage = "com.byd.wjx",
                cameraActive = true,
            )
        )
    }

    @Test
    fun `enabled empty configuration still shows setup surface on desktop`() {
        assertTrue(
            DesktopQuickCardVisibility.shouldShow(
                config = DesktopQuickCardConfig(
                    enabled = true,
                    functionCommands = emptyList(),
                    soundPaths = emptyList(),
                ),
                foreground = ForegroundApp("com.vendor.home"),
                homePackages = setOf("com.vendor.home"),
                ownPackage = "com.byd.wjx",
                cameraActive = false,
            )
        )
    }

    @Test
    fun `launcher subpage is hidden but wallpaper desktop remains visible`() {
        val config = DesktopQuickCardConfig(enabled = true, functionCommands = listOf("a"))
        val homes = setOf("com.byd.launcher")

        assertTrue(
            DesktopQuickCardVisibility.shouldShow(
                config,
                ForegroundApp("com.byd.launcher", "com.byd.launcher.Launcher"),
                homes,
                "com.byd.wjx",
                false,
            )
        )
        assertFalse(
            DesktopQuickCardVisibility.shouldShow(
                config,
                ForegroundApp("com.byd.launcher", "com.byd.launcher.AppCenterActivity"),
                homes,
                "com.byd.wjx",
                false,
            )
        )
    }

    @Test
    fun `responsive layout follows screen bound and scales all spacing`() {
        val normal = DesktopQuickCardLayout.calculate(1280f, 720f, 1f, 28f)
        val enlarged = DesktopQuickCardLayout.calculate(1280f, 720f, 1.5f, 28f)

        assertEquals(364.8f, normal.widthDp, 0.01f)
        assertEquals(normal.widthDp * 1.5f, enlarged.widthDp, 0.01f)
        assertEquals(5f, normal.gridGapDp, 0.01f)
        assertEquals(7.5f, enlarged.gridGapDp, 0.01f)
        assertEquals(28f, normal.sectionGapDp, 0.01f)
        assertEquals(42f, enlarged.sectionGapDp, 0.01f)
    }

    @Test
    fun `drag threshold is deterministic`() {
        assertFalse(OverlayDragRules.exceededSlop(4f, 4f, 8f))
        assertTrue(OverlayDragRules.exceededSlop(8f, 8f, 8f))
    }

    @Test
    fun `active sound stops when removed or desktop card disabled`() {
        assertFalse(shouldStopDesktopQuickCardSound("/a.mp3", listOf("/a.mp3"), enabled = true))
        assertTrue(shouldStopDesktopQuickCardSound("/a.mp3", emptyList(), enabled = true))
        assertTrue(shouldStopDesktopQuickCardSound("/a.mp3", listOf("/a.mp3"), enabled = false))
        assertFalse(shouldStopDesktopQuickCardSound(null, emptyList(), enabled = false))
    }
}
