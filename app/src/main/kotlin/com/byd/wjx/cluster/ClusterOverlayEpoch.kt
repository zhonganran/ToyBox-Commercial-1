package com.byd.wjx.cluster

import java.util.concurrent.atomic.AtomicLong

/** Monotonic marker used by the camera overlay to recover its topmost instrument z-order. */
internal object ClusterOverlayEpoch {
    private val value = AtomicLong(0L)

    fun current(): Long = value.get()

    fun markChanged(): Long = value.incrementAndGet()
}
