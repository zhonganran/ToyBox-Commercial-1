package com.byd.wjx.data.vehicle

import android.content.SharedPreferences

enum class SeatChannel {
    UNKNOWN,
    PRIMARY,
    FALLBACK,
}

interface SeatChannelStore {
    fun winner(): SeatChannel
    fun setWinner(channel: SeatChannel)
}

class SeatChannelStorePrefs(private val prefs: SharedPreferences) : SeatChannelStore {
    override fun winner(): SeatChannel {
        if (prefs.getInt(KEY_VERSION, -1) != SCHEMA_VERSION) return SeatChannel.UNKNOWN
        return runCatching {
            SeatChannel.valueOf(prefs.getString(KEY_WINNER, null) ?: return SeatChannel.UNKNOWN)
        }.getOrDefault(SeatChannel.UNKNOWN)
    }

    override fun setWinner(channel: SeatChannel) {
        prefs.edit()
            .putInt(KEY_VERSION, SCHEMA_VERSION)
            .putString(KEY_WINNER, channel.name)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "wjx_seat_channel"
        const val SCHEMA_VERSION = 1
        private const val KEY_VERSION = "seat_channel_schema_version"
        private const val KEY_WINNER = "seat_channel_winner"
    }
}

class InMemorySeatChannelStore : SeatChannelStore {
    private var channel: SeatChannel = SeatChannel.UNKNOWN
    override fun winner(): SeatChannel = channel
    override fun setWinner(channel: SeatChannel) {
        this.channel = channel
    }
}
