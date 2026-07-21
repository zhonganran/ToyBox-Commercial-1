package com.byd.wjx.audio

data class ExteriorRouteWrite(
    val dev: Int,
    val fid: Int,
    val value: Int,
)

object ExteriorRoutePlan {

    fun armWrites(channel: ExteriorChannel): List<ExteriorRouteWrite> =
        when (channel) {
            ExteriorChannel.Channel1 -> listOf(
                ExteriorRouteWrite(DEV_AUDIO, LOCK_CAR_SOUND_PLAYBACK_STATUS_SET, LOCK_CAR_SOUND_PLAYBACK_ON),
                ExteriorRouteWrite(DEV_AUDIO_CONTROLLER, CONTROLLER_EXTERIOR_PLAYBACK_SET, CONTROLLER_EXTERIOR_PLAYBACK_ON),
                ExteriorRouteWrite(DEV_AUDIO, EXTERIOR_SWITCH_SET, SWITCH_ON),
                ExteriorRouteWrite(DEV_AUDIO, AVAS_ROUTE_SET, AVAS_ROUTE_ON),
                ExteriorRouteWrite(DEV_AUDIO, AUDIO_KARAOKE_MODE_SET, KARAOKE_MODE_EXTERIOR),
            )
            ExteriorChannel.Channel0 -> listOf(
                ExteriorRouteWrite(DEV_ADAS, ADAS_UE_BROADCAST_POSITION_SET, ADAS_BROADCAST_POSITION_ON),
            )
        }

    fun disarmWrites(channel: ExteriorChannel): List<ExteriorRouteWrite> =
        when (channel) {
            ExteriorChannel.Channel1 -> listOf(
                ExteriorRouteWrite(DEV_AUDIO, LOCK_CAR_SOUND_PLAYBACK_STATUS_SET, LOCK_CAR_SOUND_PLAYBACK_OFF),
                ExteriorRouteWrite(DEV_AUDIO_CONTROLLER, CONTROLLER_EXTERIOR_PLAYBACK_SET, CONTROLLER_EXTERIOR_PLAYBACK_OFF),
                ExteriorRouteWrite(DEV_AUDIO, EXTERIOR_SWITCH_SET, SWITCH_OFF),
                ExteriorRouteWrite(DEV_AUDIO, AVAS_ROUTE_SET, AVAS_ROUTE_OFF),
            )
            ExteriorChannel.Channel0 -> listOf(
                ExteriorRouteWrite(DEV_ADAS, ADAS_UE_BROADCAST_POSITION_SET, ADAS_BROADCAST_POSITION_OFF),
            )
        }

    fun isStronglyConfirmed(
        channel: ExteriorChannel,
        writeResults: Map<ExteriorRouteWrite, Boolean>,
        speakerOk: Boolean,
        lockStateOk: Boolean,
        focusOk: Boolean,
    ): Boolean {
        val writes = armWrites(channel)
        return when (channel) {
            ExteriorChannel.Channel0 ->
                speakerOk &&
                    focusOk &&
                    writeResults[writes[0]] == true

            ExteriorChannel.Channel1 -> {
                // 34/57 controllers expose different exterior-playback controls. The karaoke
                // route alone is not proof of exterior output and can leave audio in the cabin.
                val exteriorControlOk =
                    writeResults[writes[0]] == true ||
                        writeResults[writes[1]] == true ||
                        lockStateOk
                val supportedRouteOk = writeResults.values.any { it }
                exteriorControlOk &&
                    speakerOk &&
                    focusOk &&
                    supportedRouteOk
            }
        }
    }

    private const val DEV_AUDIO = 1000
    private const val DEV_AUDIO_CONTROLLER = 1002
    private const val DEV_ADAS = 3
    private const val EXTERIOR_SWITCH_SET = 891293750
    private const val AVAS_ROUTE_SET = 891293760
    private const val AUDIO_KARAOKE_MODE_SET = 372244513
    private const val SWITCH_ON = 1339031619
    private const val SWITCH_OFF = 3
    private const val AVAS_ROUTE_ON = 2
    private const val AVAS_ROUTE_OFF = 1
    private const val KARAOKE_MODE_EXTERIOR = 2
    private const val LOCK_CAR_SOUND_PLAYBACK_STATUS_SET = 13
    private const val LOCK_CAR_SOUND_PLAYBACK_ON = -1442839775
    private const val LOCK_CAR_SOUND_PLAYBACK_OFF = 1
    private const val CONTROLLER_EXTERIOR_PLAYBACK_SET = -1442839775
    private const val CONTROLLER_EXTERIOR_PLAYBACK_ON = 1
    private const val CONTROLLER_EXTERIOR_PLAYBACK_OFF = 0
    private const val ADAS_UE_BROADCAST_POSITION_SET = -1442839934
    private const val ADAS_BROADCAST_POSITION_ON = 1
    private const val ADAS_BROADCAST_POSITION_OFF = 0
}
