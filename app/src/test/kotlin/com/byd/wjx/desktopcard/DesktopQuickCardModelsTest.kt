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
    fun `sanitize removes duplicates and invalid entries without limiting item count`() {
        val raw = DesktopQuickCardConfig(
            functionCommands = listOf("a", "a", "bad", "b", "c", "d", "e", "f", "g", "h", "i"),
            soundPaths = listOf("/1", "/1", "/bad", "/2", "/3", "/4", "/5", "/6", "/7", "/8", "/9"),
            floatingIconId = "missing_icon",
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
        assertEquals(0, result.x)
        assertEquals(0, result.y)
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
