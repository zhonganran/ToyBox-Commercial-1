package com.byd.wjx.audio

enum class ExteriorChannel(val label: String, val shortLabel: String, val streamType: Int) {
    Channel0("车外通道 A", "通道 A", 15),
    Channel1("车外通道 B", "通道 B", 3);

    companion object {
        fun fromName(value: String?): ExteriorChannel =
            entries.firstOrNull { it.name == value } ?: Channel1
    }
}
