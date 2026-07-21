package com.byd.wjx.desktopcard

import com.byd.wjx.R
import com.byd.wjx.ui.automation.ACTION_COMMANDS
import kotlin.math.ceil
import kotlin.math.min

const val DESKTOP_QUICK_CARD_COLUMNS = 4
const val DESKTOP_QUICK_CARD_COLLAPSED_ICON_DP = 82f
const val DESKTOP_QUICK_CARD_EXPANDED_ICON_DP = 76f
const val DESKTOP_QUICK_CARD_LEFT_SLIDING_DOOR = "wjx_auto_door_left"
const val DESKTOP_QUICK_CARD_RIGHT_SLIDING_DOOR = "wjx_auto_door_right"
const val DESKTOP_QUICK_CARD_ICON_TOOLBOX = "builtin_toolbox"
const val DESKTOP_QUICK_CARD_ICON_SMILE = "builtin_smile"

data class DesktopQuickCardIcon(
    val id: String,
    val label: String,
    val drawableRes: Int? = null,
    val filePath: String? = null,
    val custom: Boolean = false,
)

object DesktopQuickCardIconCatalog {
    val builtIns = listOf(
        DesktopQuickCardIcon(
            id = DESKTOP_QUICK_CARD_ICON_TOOLBOX,
            label = "玩具箱",
            drawableRes = R.drawable.desktop_quick_card_icon_toolbox,
        ),
        DesktopQuickCardIcon(
            id = DESKTOP_QUICK_CARD_ICON_SMILE,
            label = "笑脸",
            drawableRes = R.drawable.desktop_quick_card_icon_smile,
        ),
    )
    val default: DesktopQuickCardIcon = builtIns.first()
    val builtInIds: Set<String> = builtIns.mapTo(linkedSetOf(), DesktopQuickCardIcon::id)
}

enum class DesktopQuickCardCommandKind {
    AUTOMATION,
    AUTO_DOOR,
}

data class DesktopQuickCardCommand(
    val command: String,
    val label: String,
    val category: String,
    val kind: DesktopQuickCardCommandKind = DesktopQuickCardCommandKind.AUTOMATION,
    val payload: String = command,
)

object DesktopQuickCardCommandCatalog {
    val all: List<DesktopQuickCardCommand> = buildList {
        addAll(
            ACTION_COMMANDS
                .distinctBy { it.command }
                .map { option ->
                    DesktopQuickCardCommand(
                        command = option.command,
                        label = option.name,
                        category = option.category,
                    )
                },
        )
        add(
            DesktopQuickCardCommand(
                command = DESKTOP_QUICK_CARD_LEFT_SLIDING_DOOR,
                label = "左侧滑门",
                category = "电滑门",
                kind = DesktopQuickCardCommandKind.AUTO_DOOR,
                payload = "slide_l",
            ),
        )
        add(
            DesktopQuickCardCommand(
                command = DESKTOP_QUICK_CARD_RIGHT_SLIDING_DOOR,
                label = "右侧滑门",
                category = "电滑门",
                kind = DesktopQuickCardCommandKind.AUTO_DOOR,
                payload = "slide_r",
            ),
        )
    }

    val byCommand: Map<String, DesktopQuickCardCommand> = all.associateBy { it.command }

}

data class DesktopQuickCardConfig(
    val enabled: Boolean = false,
    val functionCommands: List<String> = emptyList(),
    val soundPaths: List<String> = emptyList(),
    val floatingIconId: String = DESKTOP_QUICK_CARD_ICON_TOOLBOX,
    val x: Int = 0,
    val y: Int = 0,
    val positionInitialized: Boolean = false,
)

internal fun sanitizeDesktopQuickCardConfig(
    raw: DesktopQuickCardConfig,
    validCommands: Set<String> = DesktopQuickCardCommandCatalog.byCommand.keys,
    validSoundPaths: Set<String>? = null,
    validIconIds: Set<String> = DesktopQuickCardIconCatalog.builtInIds,
): DesktopQuickCardConfig = raw.copy(
    functionCommands = raw.functionCommands
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .filter(validCommands::contains),
    soundPaths = raw.soundPaths
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .let { paths ->
            if (validSoundPaths == null) paths else paths.filter { it in validSoundPaths }
        },
    floatingIconId = raw.floatingIconId.takeIf(validIconIds::contains)
        ?: DESKTOP_QUICK_CARD_ICON_TOOLBOX,
    x = raw.x.coerceAtLeast(0),
    y = raw.y.coerceAtLeast(0),
)

object DesktopQuickCardGrid {
    fun rowsFor(itemCount: Int): Int = if (itemCount <= 0) {
        0
    } else {
        ceil(itemCount / DESKTOP_QUICK_CARD_COLUMNS.toFloat()).toInt()
    }
}

data class DesktopQuickCardLayoutMetrics(
    val widthDp: Float,
    val cellSizeDp: Float,
    val gridGapDp: Float,
    val contentPaddingDp: Float,
)

object DesktopQuickCardLayout {
    private const val SCREEN_WIDTH_RATIO = 0.42f
    private const val SCREEN_HEIGHT_RATIO = 0.75f
    private const val BASE_GRID_GAP_DP = 7f
    private const val BASE_CONTENT_PADDING_DP = 3f

    fun calculate(
        screenWidthDp: Float,
        screenHeightDp: Float,
    ): DesktopQuickCardLayoutMetrics {
        val width = min(screenWidthDp * SCREEN_WIDTH_RATIO, screenHeightDp * SCREEN_HEIGHT_RATIO)
        val gap = BASE_GRID_GAP_DP
        val padding = BASE_CONTENT_PADDING_DP
        val availableForCells = (width - padding * 2f - gap * (DESKTOP_QUICK_CARD_COLUMNS - 1))
            .coerceAtLeast(DESKTOP_QUICK_CARD_COLUMNS.toFloat())
        return DesktopQuickCardLayoutMetrics(
            widthDp = width,
            cellSizeDp = availableForCells / DESKTOP_QUICK_CARD_COLUMNS,
            gridGapDp = gap,
            contentPaddingDp = padding,
        )
    }

    fun widthForColumns(metrics: DesktopQuickCardLayoutMetrics, columns: Int): Float {
        val visibleColumns = columns.coerceIn(1, DESKTOP_QUICK_CARD_COLUMNS)
        return metrics.contentPaddingDp * 2f +
            metrics.cellSizeDp * visibleColumns +
            metrics.gridGapDp * (visibleColumns - 1)
    }
}

object DesktopQuickCardAnchor {
    fun expandedX(collapsedX: Int, collapsedWidth: Int, expandedWidth: Int): Int =
        collapsedX + collapsedWidth / 2 - expandedWidth / 2

    fun collapsedX(expandedX: Int, expandedWidth: Int, collapsedWidth: Int): Int =
        expandedX + expandedWidth / 2 - collapsedWidth / 2
}

enum class DesktopQuickCardVisibilityReason {
    VISIBLE,
    DISABLED,
}

data class DesktopQuickCardVisibilityDecision(
    val show: Boolean,
    val reason: DesktopQuickCardVisibilityReason,
)

object DesktopQuickCardVisibility {
    fun evaluate(config: DesktopQuickCardConfig): DesktopQuickCardVisibilityDecision =
        if (config.enabled) {
            DesktopQuickCardVisibilityDecision(true, DesktopQuickCardVisibilityReason.VISIBLE)
        } else {
            DesktopQuickCardVisibilityDecision(false, DesktopQuickCardVisibilityReason.DISABLED)
        }

    fun shouldShow(config: DesktopQuickCardConfig): Boolean = config.enabled
}

internal fun shouldStopDesktopQuickCardSound(
    activeSoundPath: String?,
    configuredSoundPaths: List<String>,
    enabled: Boolean,
): Boolean = activeSoundPath != null && (!enabled || activeSoundPath !in configuredSoundPaths)
