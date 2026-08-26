package com.byd.wjx.audio

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.io.File

internal sealed interface OutsidePlaybackCommand {
    data class Play(
        val file: File,
        val channel: ExteriorChannel,
        val loop: Boolean,
        val owner: String?,
        val reverseActive: Boolean,
        val toggle: Boolean,
    ) : OutsidePlaybackCommand

    data class Stop(val owner: String?) : OutsidePlaybackCommand
    data object UpdateVolume : OutsidePlaybackCommand
    data object Ignore : OutsidePlaybackCommand
}

internal object OutsidePlaybackContract {
    const val ACTION_PLAY = "com.byd.wjx.action.PLAY_OUTSIDE"
    const val ACTION_TOGGLE = "com.byd.wjx.action.TOGGLE_OUTSIDE"
    const val ACTION_STOP = "com.byd.wjx.action.STOP_OUTSIDE"
    const val ACTION_UPDATE_VOLUME = "com.byd.wjx.action.UPDATE_OUTSIDE_VOLUME"
    const val ACTION_PLAYBACK_STATE = "com.byd.wjx.action.OUTSIDE_PLAYBACK_STATE"
    const val EXTRA_PATH = "path"
    const val EXTRA_CHANNEL = "channel"
    const val EXTRA_LOOP = "loop"
    const val EXTRA_REVERSE_ACTIVE = "reverse_active"
    const val EXTRA_OWNER = "owner"
    const val EXTRA_STATE = "state"
    const val EXTRA_STATE_PATH = "state_path"
    const val STATE_STARTED = "started"
    const val STATE_STOPPED = "stopped"
    const val STATE_COMPLETED = "completed"
    const val STATE_ERROR = "error"

    fun parse(intent: Intent?): OutsidePlaybackCommand = when (intent?.action) {
        ACTION_PLAY, ACTION_TOGGLE -> OutsidePlaybackCommand.Play(
            file = File(intent.getStringExtra(EXTRA_PATH).orEmpty()),
            channel = ExteriorChannel.fromName(intent.getStringExtra(EXTRA_CHANNEL)),
            loop = intent.getBooleanExtra(EXTRA_LOOP, false),
            owner = intent.getStringExtra(EXTRA_OWNER)?.takeIf { it.isNotBlank() },
            reverseActive = intent.getBooleanExtra(EXTRA_REVERSE_ACTIVE, false),
            toggle = intent.action == ACTION_TOGGLE,
        )
        ACTION_STOP -> OutsidePlaybackCommand.Stop(
            intent.getStringExtra(EXTRA_OWNER)?.takeIf { it.isNotBlank() },
        )
        ACTION_UPDATE_VOLUME -> OutsidePlaybackCommand.UpdateVolume
        else -> OutsidePlaybackCommand.Ignore
    }
}

internal object OutsidePlaybackStarter {
    fun play(
        context: Context,
        file: File,
        channel: ExteriorChannel,
        loop: Boolean,
        owner: String?,
        reverseActive: Boolean,
        toggle: Boolean,
    ) {
        val action = if (toggle) OutsidePlaybackContract.ACTION_TOGGLE else OutsidePlaybackContract.ACTION_PLAY
        val intent = Intent(context, WjxOutsidePlayService::class.java)
            .setAction(action)
            .putExtra(OutsidePlaybackContract.EXTRA_PATH, file.absolutePath)
            .putExtra(OutsidePlaybackContract.EXTRA_CHANNEL, channel.name)
            .putExtra(OutsidePlaybackContract.EXTRA_LOOP, loop)
            .putExtra(OutsidePlaybackContract.EXTRA_OWNER, owner.orEmpty())
            .putExtra(OutsidePlaybackContract.EXTRA_REVERSE_ACTIVE, reverseActive)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context, owner: String?) {
        val intent = Intent(context, WjxOutsidePlayService::class.java)
            .setAction(OutsidePlaybackContract.ACTION_STOP)
            .putExtra(OutsidePlaybackContract.EXTRA_OWNER, owner.orEmpty())
        ContextCompat.startForegroundService(context, intent)
    }

    fun updateVolume(context: Context) {
        val intent = Intent(context, WjxOutsidePlayService::class.java)
            .setAction(OutsidePlaybackContract.ACTION_UPDATE_VOLUME)
        ContextCompat.startForegroundService(context, intent)
    }
}
