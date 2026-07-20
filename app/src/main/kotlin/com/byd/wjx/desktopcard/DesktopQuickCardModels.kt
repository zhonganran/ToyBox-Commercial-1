package com.byd.wjx.desktopcard

import com.byd.wjx.data.foreground.ForegroundApp
import com.byd.wjx.ui.automation.ACTION_COMMANDS
import com.byd.wjx.util.HomePackageResolver
import kotlin.math.ceil
import kotlin.math.min

const val DESKTOP_QUICK_CARD_MAX_ITEMS = 8
const val DESKTOP_QUICK_CARD_COLUMNS = 4
const val DESKTOP_QUICK_CARD_MIN_SCALE = 0.70f
const val DESKTOP_QUICK_CARD_MAX_SCALE = 1.50f
const val DESKTOP_QUICK_CARD_MIN_SECTION_GAP_DP = 0f
const val DESKTOP_QUICK_CARD_MAX_SECTION_GAP_DP = 72f
const val DESKTOP_QUICK_CARD_DEFAULT_SECTION_GAP_DP = 28f
const val DESKTOP_QUICK_CARD_LEFT_SLIDING_DOOR = "wjx_auto_door_left"
const val DESKTOP_QUICK_CARD_RIGHT_SLIDING_DOOR = "wjx_auto_door_right"

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
    val allowOverOtherApps: Boolean = false,
    val functionCommands: List<String> = emptyList(),
    val soundPaths: List<String> = emptyList(),
    val scale: Float = 1f,
    val sectionGapDp: Float = DESKTOP_QUICK_CARD_DEFAULT_SECTION_GAP_DP,
    val dividerEnabled: Boolean = true,
    val x: Int = 0,
    val y: Int = 0,
    val positionInitialized: Boolean = false,
)

internal fun sanitizeDesktopQuickCardConfig(
    raw: DesktopQuickCardConfig,
    validCommands: Set<String> = DesktopQuickCardCommandCatalog.byCommand.keys,
    validSoundPaths: Set<String>? = null,
): DesktopQuickCardConfig = raw.copy(
    functionCommands = raw.functionCommands
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .filter(validCommands::contains)
        .take(DESKTOP_QUICK_CARD_MAX_ITEMS),
    soundPaths = raw.soundPaths
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .let { paths ->
            if (validSoundPaths == null) paths else paths.filter { it in validSoundPaths }
        }
        .take(DESKTOP_QUICK_CARD_MAX_ITEMS),
    scale = raw.scale.coerceIn(DESKTOP_QUICK_CARD_MIN_SCALE, DESKTOP_QUICK_CARD_MAX_SCALE),
    sectionGapDp = raw.sectionGapDp.coerceIn(
        DESKTOP_QUICK_CARD_MIN_SECTION_GAP_DP,
        DESKTOP_QUICK_CARD_MAX_SECTION_GAP_DP,
    ),
    x = raw.x.coerceAtLeast(0),
    y = raw.y.coerceAtLeast(0),
)

object DesktopQuickCardGrid {
    fun rowsFor(itemCount: Int): Int = if (itemCount <= 0) {
        0
    } else {
        ceil(itemCount.coerceAtMost(DESKTOP_QUICK_CARD_MAX_ITEMS) / DESKTOP_QUICK_CARD_COLUMNS.toFloat())
            .toInt()
    }
}

data class DesktopQuickCardLayoutMetrics(
    val widthDp: Float,
    val cellSizeDp: Float,
    val gridGapDp: Float,
    val contentPaddingDp: Float,
    val sectionGapDp: Float,
)

object DesktopQuickCardLayout {
    private const val SCREEN_WIDTH_RATIO = 0.285f
    private const val SCREEN_HEIGHT_RATIO = 0.58f
    private const val BASE_GRID_GAP_DP = 5f
    private const val BASE_CONTENT_PADDING_DP = 6f

    fun calculate(
        screenWidthDp: Float,
        screenHeightDp: Float,
        scale: Float,
        sectionGapDp: Float,
    ): DesktopQuickCardLayoutMetrics {
        val safeScale = scale.coerceIn(DESKTOP_QUICK_CARD_MIN_SCALE, DESKTOP_QUICK_CARD_MAX_SCALE)
        val width = min(screenWidthDp * SCREEN_WIDTH_RATIO, screenHeightDp * SCREEN_HEIGHT_RATIO) * safeScale
        val gap = BASE_GRID_GAP_DP * safeScale
        val padding = BASE_CONTENT_PADDING_DP * safeScale
        val availableForCells = (width - padding * 2f - gap * (DESKTOP_QUICK_CARD_COLUMNS - 1))
            .coerceAtLeast(DESKTOP_QUICK_CARD_COLUMNS.toFloat())
        return DesktopQuickCardLayoutMetrics(
            widthDp = width,
            cellSizeDp = availableForCells / DESKTOP_QUICK_CARD_COLUMNS,
            gridGapDp = gap,
            contentPaddingDp = padding,
            sectionGapDp = sectionGapDp
                .coerceIn(DESKTOP_QUICK_CARD_MIN_SECTION_GAP_DP, DESKTOP_QUICK_CARD_MAX_SECTION_GAP_DP) * safeScale,
        )
    }
}

enum class DesktopQuickCardVisibilityReason {
    VISIBLE_HOME,
    VISIBLE_OTHER_APP,
    DISABLED,
    CAMERA,
    OWN_APP,
    UNKNOWN_FOREGROUND,
    LAUNCHER_SUBPAGE,
    NOT_HOME,
}

data class DesktopQuickCardVisibilityDecision(
    val show: Boolean,
    val reason: DesktopQuickCardVisibilityReason,
)

object DesktopQuickCardVisibility {
    fun evaluate(
        config: DesktopQuickCardConfig,
        foreground: ForegroundApp,
        homePackages: Set<String>,
        ownPackage: String,
        cameraActive: Boolean,
    ): DesktopQuickCardVisibilityDecision {
        if (!config.enabled) return hidden(DesktopQuickCardVisibilityReason.DISABLED)
        if (cameraActive) return hidden(DesktopQuickCardVisibilityReason.CAMERA)
        val foregroundPackage = foreground.packageName?.takeIf(String::isNotBlank)
            ?: return hidden(DesktopQuickCardVisibilityReason.UNKNOWN_FOREGROUND)
        if (foregroundPackage == ownPackage) return hidden(DesktopQuickCardVisibilityReason.OWN_APP)

        if (config.allowOverOtherApps) {
            return DesktopQuickCardVisibilityDecision(true, DesktopQuickCardVisibilityReason.VISIBLE_OTHER_APP)
        }
        if (!HomePackageResolver.isHomePackage(foregroundPackage, homePackages)) {
            return hidden(DesktopQuickCardVisibilityReason.NOT_HOME)
        }
        if (HomePackageResolver.isLauncherSubpage(foreground.className)) {
            return hidden(DesktopQuickCardVisibilityReason.LAUNCHER_SUBPAGE)
        }
        return DesktopQuickCardVisibilityDecision(true, DesktopQuickCardVisibilityReason.VISIBLE_HOME)
    }

    fun shouldShow(
        config: DesktopQuickCardConfig,
        foreground: ForegroundApp,
        homePackages: Set<String>,
        ownPackage: String,
        cameraActive: Boolean,
    ): Boolean = evaluate(config, foreground, homePackages, ownPackage, cameraActive).show

    private fun hidden(reason: DesktopQuickCardVisibilityReason) =
        DesktopQuickCardVisibilityDecision(false, reason)
}

internal fun shouldStopDesktopQuickCardSound(
    activeSoundPath: String?,
    configuredSoundPaths: List<String>,
    enabled: Boolean,
): Boolean = activeSoundPath != null && (!enabled || activeSoundPath !in configuredSoundPaths)
