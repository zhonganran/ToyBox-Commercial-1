package com.byd.wjx.ui.map

import com.byd.wjx.data.local.entity.TripPointEntity

internal fun decimateTripPoints(
    points: List<TripPointEntity>,
    maxPoints: Int
): List<TripPointEntity> {
    if (points.size <= maxPoints || maxPoints < 3) return points

    val lastIndex = points.lastIndex
    val step = lastIndex.toDouble() / (maxPoints - 1).toDouble()
    val result = ArrayList<TripPointEntity>(maxPoints)
    var previousIndex = -1

    for (i in 0 until maxPoints) {
        val index = (i * step).toInt().coerceIn(0, lastIndex)
        if (index != previousIndex) {
            result.add(points[index])
            previousIndex = index
        }
    }
    if (result.lastOrNull() != points.last()) {
        result.add(points.last())
    }
    return result
}
