package com.byd.wjx.audio

import android.media.AudioAttributes
import android.util.Log
import com.byd.wjx.log.WjxFileLogger

object WjxAudioAttributes {
    fun forOutside(channel: ExteriorChannel): AudioAttributes {
        val builder = AudioAttributes.Builder().setLegacyStreamType(channel.streamType)
        if (channel == ExteriorChannel.Channel0) {
            builder.setFlags(CHANNEL0_BYPASS_FLAG)
        } else {
            builder
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            runCatching {
                builder.javaClass.getMethod("addTag", String::class.java)
                    .invoke(builder, "CAR_LOCK_SOUND")
            }.onFailure {
                Log.w(TAG, "addTag(CAR_LOCK_SOUND) failed: ${it.message}")
                WjxFileLogger.w(TAG, "addTag(CAR_LOCK_SOUND) failed: ${it.message}")
            }
        }
        return builder.build()
    }

    private const val CHANNEL0_BYPASS_FLAG = 131072
    private const val TAG = "WjxAudioAttributes"
}
