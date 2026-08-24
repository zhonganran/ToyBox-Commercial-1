package com.byd.wjx.desktopcard

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
        assertEquals(DESKTOP_QUICK_CARD_ICON_TOOLBOX, config.floatingIconId)
        assertEquals(DESKTOP_QUICK_CARD_DEFAULT_ICON_DP, config.floatingIconSizeDp, 0f)
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
    fun `realtime hailing is a dedicated vehicle control command`() {
        val item = DesktopQuickCardCommandCatalog.byCommand.getValue(DESKTOP_QUICK_CARD_REALTIME_HAILING)

        assertEquals("实时喊话", item.label)
        assertEquals("实时喊话", item.category)
        assertEquals(DesktopQuickCardCommandKind.REALTIME_HAILING, item.kind)
    }

    @Test
    fun `cluster lyrics toggle is available as a desktop card command`() {
        val item = DesktopQuickCardCommandCatalog.byCommand.getValue(DESKTOP_QUICK_CARD_CLUSTER_LYRICS)

        assertEquals("仪表歌词", item.label)
        assertEquals("桌面组件", item.category)
        assertEquals(DesktopQuickCardCommandKind.CLUSTER_LYRICS, item.kind)
    }

    @Test
    fun `cluster card and desktop lyrics toggles are available as desktop card commands`() {
        val clusterCard = DesktopQuickCardCommandCatalog.byCommand.getValue(DESKTOP_QUICK_CARD_CLUSTER_CARD)
        val clusterCard2 = DesktopQuickCardCommandCatalog.byCommand.getValue(DESKTOP_QUICK_CARD_CLUSTER_CARD_2)
        val desktopLyrics = DesktopQuickCardCommandCatalog.byCommand.getValue(DESKTOP_QUICK_CARD_DESKTOP_LYRICS)

        assertEquals("仪表卡片", clusterCard.label)
        assertEquals("桌面组件", clusterCard.category)
        assertEquals(DesktopQuickCardCommandKind.CLUSTER_CARD, clusterCard.kind)
        assertEquals("primary", clusterCard.payload)
        assertEquals("仪表卡片2", clusterCard2.label)
        assertEquals("桌面组件", clusterCard2.category)
        assertEquals(DesktopQuickCardCommandKind.CLUSTER_CARD, clusterCard2.kind)
        assertEquals("secondary", clusterCard2.payload)
        assertEquals("桌面歌词", desktopLyrics.label)
        assertEquals("桌面组件", desktopLyrics.category)
        assertEquals(DesktopQuickCardCommandKind.DESKTOP_LYRICS, desktopLyrics.kind)
    }

    @Test
    fun `sanitize removes duplicates and invalid entries without limiting item count`() {
        val raw = DesktopQuickCardConfig(
            functionCommands = listOf("a", "a", "bad", "b", "c", "d", "e", "f", "g", "h", "i"),
            soundPaths = listOf("/1", "/1", "/bad", "/2", "/3", "/4", "/5", "/6", "/7", "/8", "/9"),
            floatingIconId = "missing_icon",
            floatingIconSizeDp = DESKTOP_QUICK_CARD_MAX_ICON_DP + 20f,
            x = -1,
            y = -2,
        )

        val result = sanitizeDesktopQuickCardConfig(
            raw = raw,
            validCommands = ('a'..'i').map(Char::toString).toSet(),
            validSoundPaths = (1..9).map { "/$it" }.toSet(),
        )

        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h", "i"), result.functionCommands)
        assertEquals(listOf("/1", "/2", "/3", "/4", "/5", "/6", "/7", "/8", "/9"), result.soundPaths)
        assertEquals(DESKTOP_QUICK_CARD_ICON_TOOLBOX, result.floatingIconId)
        assertEquals(DESKTOP_QUICK_CARD_MAX_ICON_DP, result.floatingIconSizeDp, 0f)
        assertEquals(0, result.x)
        assertEquals(0, result.y)
    }

    @Test
    fun `icon size sanitizes invalid and out of range values`() {
        val tooSmall = sanitizeDesktopQuickCardConfig(
            DesktopQuickCardConfig(floatingIconSizeDp = DESKTOP_QUICK_CARD_MIN_ICON_DP - 1f),
        )
        val largerThanScreen = sanitizeDesktopQuickCardConfig(
            raw = DesktopQuickCardConfig(floatingIconSizeDp = 900f),
            maxIconSizeDp = 720f,
        )
        val invalid = sanitizeDesktopQuickCardConfig(
            DesktopQuickCardConfig(floatingIconSizeDp = Float.NaN),
        )

        assertEquals(DESKTOP_QUICK_CARD_MIN_ICON_DP, tooSmall.floatingIconSizeDp, 0f)
        assertEquals(720f, largerThanScreen.floatingIconSizeDp, 0f)
        assertEquals(DESKTOP_QUICK_CARD_DEFAULT_ICON_DP, invalid.floatingIconSizeDp, 0f)
        assertEquals(540f, desktopQuickCardMaxIconSizeDp(1920f, 1080f), 0f)
        assertEquals(360f, desktopQuickCardMaxIconSizeDp(720f, 1280f), 0f)
        assertEquals(
            76f,
            expandedDesktopQuickCardIconSizeDp(DESKTOP_QUICK_CARD_DEFAULT_ICON_DP),
            0.01f,
        )
        assertTrue(
            expandedDesktopQuickCardIconSizeDp(DESKTOP_QUICK_CARD_MAX_ICON_DP) < 160f,
        )
    }

    @Test
    fun `grid uses four columns and supports unlimited rows`() {
        assertEquals(0, DesktopQuickCardGrid.rowsFor(0))
        assertEquals(1, DesktopQuickCardGrid.rowsFor(1))
        assertEquals(1, DesktopQuickCardGrid.rowsFor(4))
        assertEquals(2, DesktopQuickCardGrid.rowsFor(5))
        assertEquals(5, DesktopQuickCardGrid.rowsFor(20))
    }

    @Test
    fun `visibility depends only on whether the overlay is enabled`() {
        val enabled = DesktopQuickCardVisibility.evaluate(DesktopQuickCardConfig(enabled = true))
        val disabled = DesktopQuickCardVisibility.evaluate(DesktopQuickCardConfig(enabled = false))

        assertTrue(enabled.show)
        assertEquals(DesktopQuickCardVisibilityReason.VISIBLE, enabled.reason)
        assertFalse(disabled.show)
        assertEquals(DesktopQuickCardVisibilityReason.DISABLED, disabled.reason)
    }

    @Test
    fun `enabled empty configuration still shows setup surface on desktop`() {
        assertTrue(
            DesktopQuickCardVisibility.shouldShow(
                DesktopQuickCardConfig(
                    enabled = true,
                    functionCommands = emptyList(),
                    soundPaths = emptyList(),
                ),
            )
        )
    }

    @Test
    fun `responsive layout follows screen bounds`() {
        val normal = DesktopQuickCardLayout.calculate(1280f, 720f)

        assertEquals(537.6f, normal.widthDp, 0.01f)
        assertEquals(7f, normal.gridGapDp, 0.01f)
        assertTrue(DesktopQuickCardLayout.widthForColumns(normal, 1) < normal.widthDp)
        assertEquals(normal.widthDp, DesktopQuickCardLayout.widthForColumns(normal, 4), 0.01f)
    }

    @Test
    fun `card anchors icon to the nearest horizontal edge`() {
        assertEquals(
            DesktopQuickCardHorizontalAnchor.START,
            DesktopQuickCardAnchor.edgeFor(collapsedX = 140, collapsedWidth = 82, screenWidth = 1280),
        )
        assertEquals(
            DesktopQuickCardHorizontalAnchor.END,
            DesktopQuickCardAnchor.edgeFor(collapsedX = 1100, collapsedWidth = 82, screenWidth = 1280),
        )
    }

    @Test
    fun `expanded and collapsed card preserve the selected icon edge`() {
        val startExpandedX = DesktopQuickCardAnchor.expandedX(
            collapsedX = 140,
            collapsedWidth = 82,
            expandedWidth = 360,
            anchor = DesktopQuickCardHorizontalAnchor.START,
        )
        val endExpandedX = DesktopQuickCardAnchor.expandedX(
            collapsedX = 1100,
            collapsedWidth = 82,
            expandedWidth = 360,
            anchor = DesktopQuickCardHorizontalAnchor.END,
        )

        assertEquals(140, startExpandedX)
        assertEquals(822, endExpandedX)
        assertEquals(
            140,
            DesktopQuickCardAnchor.collapsedX(
                expandedX = startExpandedX,
                expandedWidth = 360,
                collapsedWidth = 82,
                anchor = DesktopQuickCardHorizontalAnchor.START,
            ),
        )
        assertEquals(
            1100,
            DesktopQuickCardAnchor.collapsedX(
                expandedX = endExpandedX,
                expandedWidth = 360,
                collapsedWidth = 82,
                anchor = DesktopQuickCardHorizontalAnchor.END,
            ),
        )
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
