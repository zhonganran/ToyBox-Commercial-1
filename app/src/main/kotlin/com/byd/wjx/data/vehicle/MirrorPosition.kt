package com.byd.wjx.data.vehicle

/** Absolute physical door-mirror geometry reported by the BYD bodywork device. */
data class MirrorPosition(
    val leftHorizontal: Float,
    val leftVertical: Float,
    val rightHorizontal: Float,
    val rightVertical: Float,
) {
    fun isSane(): Boolean =
        leftHorizontal.isSaneAxis() &&
            leftVertical.isSaneAxis() &&
            rightHorizontal.isSaneAxis() &&
            rightVertical.isSaneAxis()

    fun withOffsets(
        leftEnabled: Boolean,
        rightEnabled: Boolean,
        leftHorizontalOffset: Float,
        leftVerticalOffset: Float,
        rightHorizontalOffset: Float,
        rightVerticalOffset: Float,
    ): MirrorPosition = copy(
        leftHorizontal = if (leftEnabled) {
            (leftHorizontal + leftHorizontalOffset).coerceIn(AXIS_MIN, AXIS_MAX)
        } else {
            leftHorizontal
        },
        leftVertical = if (leftEnabled) {
            (leftVertical + leftVerticalOffset).coerceIn(AXIS_MIN, AXIS_MAX)
        } else {
            leftVertical
        },
        rightHorizontal = if (rightEnabled) {
            (rightHorizontal + rightHorizontalOffset).coerceIn(AXIS_MIN, AXIS_MAX)
        } else {
            rightHorizontal
        },
        rightVertical = if (rightEnabled) {
            (rightVertical + rightVerticalOffset).coerceIn(AXIS_MIN, AXIS_MAX)
        } else {
            rightVertical
        },
    )

    fun restoreSelectedFrom(
        original: MirrorPosition,
        leftEnabled: Boolean,
        rightEnabled: Boolean,
    ): MirrorPosition = copy(
        leftHorizontal = if (leftEnabled) original.leftHorizontal else leftHorizontal,
        leftVertical = if (leftEnabled) original.leftVertical else leftVertical,
        rightHorizontal = if (rightEnabled) original.rightHorizontal else rightHorizontal,
        rightVertical = if (rightEnabled) original.rightVertical else rightVertical,
    )

    companion object {
        const val AXIS_MIN = 0f
        const val AXIS_MAX = 255f

        private fun Float.isSaneAxis(): Boolean = isFinite() && this in AXIS_MIN..AXIS_MAX
    }
}
