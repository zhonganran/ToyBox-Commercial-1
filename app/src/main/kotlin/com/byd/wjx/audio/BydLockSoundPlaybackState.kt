package com.byd.wjx.audio

object BydLockSoundPlaybackState {

    fun setEnabled(enabled: Boolean): Boolean {
        val value = if (enabled) 1 else 0
        for (name in AUDIO_MANAGER_CLASSES) {
            val ok = runCatching {
                val cls = Class.forName(name)
                val target = runCatching { cls.getDeclaredConstructor().newInstance() }.getOrNull()
                val method = cls.methods.firstOrNull {
                    it.name == "setCarLockSoundPlayState" && it.parameterTypes.size == 1
                } ?: return@runCatching false
                method.isAccessible = true
                method.invoke(target, value)
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    private val AUDIO_MANAGER_CLASSES = listOf(
        "android.media.BydAudioManager",
        "com.byd.audio.BYDAudioManager",
        "android.media.byd.BYDAudioManager",
        "com.byd.car.audio.BydAudioManager",
        "com.byd.mediacenter.audio.BydAudioManager",
        "com.byd.ivi.audio.BydAudioManager",
    )
}
