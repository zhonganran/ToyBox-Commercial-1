package com.byd.wjx.domain.vehicle

/**
 * Single parser/formatter for the numeric vehicle commands shared by automation UI and dispatch.
 * Keeping the syntax here prevents the editor and the write translator from accepting different
 * command shapes or value ranges.
 */
object VehicleCommandSyntax {
    sealed interface NumericCommand {
        val value: Int
        fun commandFor(value: Int): String
    }

    enum class WindowTarget(val token: String) {
        DRIVER("主驾"),
        PASSENGER("副驾"),
        REAR_LEFT("后左"),
        REAR_RIGHT("后右"),
        ALL("车窗"),
        FRONT("前排车窗"),
        REAR("后排车窗"),
    }

    data class WindowPosition(
        val target: WindowTarget,
        override val value: Int,
    ) : NumericCommand {
        override fun commandFor(value: Int): String =
            "${target.token}打开${value.coerceIn(0, 100)}"
    }

    data class Temperature(
        val passenger: Boolean,
        override val value: Int,
    ) : NumericCommand {
        override fun commandFor(value: Int): String =
            "${if (passenger) "副驾温度" else "设置温度"}${value.coerceIn(16, 30)}"
    }

    data class FanLevel(override val value: Int) : NumericCommand {
        override fun commandFor(value: Int): String = "风量${value.coerceIn(0, 7)}档"
    }

    data class SunshadePosition(override val value: Int) : NumericCommand {
        override fun commandFor(value: Int): String = "遮阳帘位置${value.coerceIn(0, 100)}"
    }

    data class AmbientBrightness(override val value: Int) : NumericCommand {
        override fun commandFor(value: Int): String = "氛围灯亮度${value.coerceIn(1, 4)}档"
    }

    data class HeadlightHeight(override val value: Int) : NumericCommand {
        override fun commandFor(value: Int): String = "前照灯高度${value.coerceIn(0, 5)}档"
    }

    enum class MassageSeat(val token: String, val actionPrefix: String) {
        DRIVER("主驾", "driver"),
        PASSENGER("副驾", "passenger"),
    }

    enum class MassageMode(val token: String, val actionSuffix: String, val rawValue: Int) {
        WAVE("波浪", "wave", 2),
        PULSE("脉冲", "pulse", 3),
        STRETCH("拉伸", "stretch", 4),
        WAIST("腰部", "waist", 5),
        SHOULDER("肩部", "shoulder", 6),
    }

    data class Massage(
        val seat: MassageSeat,
        val mode: MassageMode,
        override val value: Int,
    ) : NumericCommand {
        override fun commandFor(value: Int): String =
            "${seat.token}按摩${mode.token}${value.coerceIn(1, 3)}档"
    }

    fun parseNumeric(command: String): NumericCommand? {
        INDIVIDUAL_WINDOW.matchEntire(command)?.let { match ->
            val target = when (match.groupValues[1]) {
                "主驾" -> WindowTarget.DRIVER
                "副驾" -> WindowTarget.PASSENGER
                "后左" -> WindowTarget.REAR_LEFT
                "后右" -> WindowTarget.REAR_RIGHT
                else -> return null
            }
            return WindowPosition(target, match.groupValues[2].toInt().coerceIn(0, 100))
        }
        GROUP_WINDOW.matchEntire(command)?.let { match ->
            val target = when (match.groupValues[1]) {
                "车窗" -> WindowTarget.ALL
                "前排车窗" -> WindowTarget.FRONT
                "后排车窗" -> WindowTarget.REAR
                else -> return null
            }
            return WindowPosition(target, match.groupValues[2].toInt().coerceIn(0, 100))
        }
        DRIVER_TEMPERATURE.matchEntire(command)?.let { match ->
            return Temperature(passenger = false, match.groupValues[1].toInt().coerceIn(16, 30))
        }
        PASSENGER_TEMPERATURE.matchEntire(command)?.let { match ->
            return Temperature(passenger = true, match.groupValues[1].toInt().coerceIn(16, 30))
        }
        FAN_LEVEL.matchEntire(command)?.let { match ->
            return FanLevel(match.groupValues[1].toInt().coerceIn(0, 7))
        }
        SUNSHADE_POSITION.matchEntire(command)?.let { match ->
            return SunshadePosition(match.groupValues[1].toInt().coerceIn(0, 100))
        }
        AMBIENT_BRIGHTNESS.matchEntire(command)?.let { match ->
            return AmbientBrightness(match.groupValues[1].toInt().coerceIn(1, 4))
        }
        HEADLIGHT_HEIGHT.matchEntire(command)?.let { match ->
            return HeadlightHeight(match.groupValues[1].toInt().coerceIn(0, 5))
        }
        MASSAGE.matchEntire(command)?.let { match ->
            val seat = if (match.groupValues[1] == "主驾") MassageSeat.DRIVER else MassageSeat.PASSENGER
            val mode = MassageMode.entries.firstOrNull { it.token == match.groupValues[2] } ?: return null
            return Massage(seat, mode, match.groupValues[3].toInt().coerceIn(1, 3))
        }
        return null
    }

    private val INDIVIDUAL_WINDOW = Regex("""(主驾|副驾|后左|后右)打开(\d+)""")
    private val GROUP_WINDOW = Regex("""(车窗|前排车窗|后排车窗)打开(\d+)""")
    private val DRIVER_TEMPERATURE = Regex("""设置温度(\d+)""")
    private val PASSENGER_TEMPERATURE = Regex("""副驾温度(\d+)""")
    private val FAN_LEVEL = Regex("""风量(\d+)档""")
    private val SUNSHADE_POSITION = Regex("""遮阳帘位置(\d+)""")
    private val AMBIENT_BRIGHTNESS = Regex("""氛围灯亮度(\d+)档""")
    private val HEADLIGHT_HEIGHT = Regex("""前照灯高度([0-5])档""")
    private val MASSAGE = Regex("""(主驾|副驾)按摩(波浪|脉冲|拉伸|腰部|肩部)([123])档""")
}
