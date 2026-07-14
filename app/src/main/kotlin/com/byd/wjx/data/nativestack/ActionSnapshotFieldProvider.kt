package com.byd.wjx.data.nativestack

/** Supplies optional vehicle fields required by currently enabled fast-path features. */
fun interface ActionSnapshotFieldProvider {
    suspend fun additionalFields(): Set<String>
}
