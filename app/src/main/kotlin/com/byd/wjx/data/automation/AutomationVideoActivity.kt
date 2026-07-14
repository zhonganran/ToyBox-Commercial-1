package com.byd.wjx.data.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.byd.wjx.log.WjxFileLogger

class AutomationVideoActivity : AppCompatActivity() {
    private lateinit var videoView: VideoView
    private var savedStreamVolume: Int? = null
    private var stopReceiverRegistered = false
    private var activeOwner: String? = null

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_STOP) return
            val requestedOwner = intent.getStringExtra(EXTRA_OWNER)
            if (requestedOwner == null || requestedOwner == activeOwner) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).hide(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
        )
        videoView = VideoView(this)
        setContentView(
            videoView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        window.decorView.setBackgroundColor(Color.BLACK)
        registerStopReceiver()
        playIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        playIntent(intent)
    }

    private fun playIntent(intent: Intent) {
        val source = intent.getStringExtra(EXTRA_SOURCE).orEmpty()
        if (source.isBlank()) {
            finish()
            return
        }
        val volume = intent.getIntExtra(EXTRA_VOLUME, DEFAULT_VOLUME).coerceIn(0, 100)
        activeOwner = intent.getStringExtra(EXTRA_OWNER)
        AutomationMediaRegistry.videoOwner = activeOwner
        setStreamVolume(volume)
        runCatching {
            videoView.stopPlayback()
            videoView.setOnPreparedListener { player ->
                player.isLooping = false
                videoView.start()
                WjxFileLogger.i(TAG, "video started source=${source.takeLast(80)} volume=$volume")
            }
            videoView.setOnCompletionListener { finish() }
            videoView.setOnErrorListener { _, what, extra ->
                WjxFileLogger.w(TAG, "video error what=$what extra=$extra source=${source.takeLast(80)}")
                finish()
                true
            }
            val uri = Uri.parse(source)
            if (uri.scheme.equals("content", ignoreCase = true)) {
                videoView.setVideoURI(uri)
            } else {
                videoView.setVideoPath(source)
            }
            videoView.requestFocus()
        }.onFailure {
            WjxFileLogger.w(TAG, "video prepare failed: ${it.message}", it)
            finish()
        }
    }

    private fun setStreamVolume(percent: Int) {
        val audio = getSystemService(AudioManager::class.java) ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        if (savedStreamVolume == null) savedStreamVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val target = ((percent * max) / 100f).toInt().coerceIn(0, max)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
    }

    private fun restoreStreamVolume() {
        val saved = savedStreamVolume ?: return
        savedStreamVolume = null
        val audio = getSystemService(AudioManager::class.java) ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, saved.coerceIn(0, max), 0) }
    }

    private fun registerStopReceiver() {
        if (stopReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            stopReceiver,
            IntentFilter(ACTION_STOP),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        stopReceiverRegistered = true
    }

    override fun onDestroy() {
        runCatching { videoView.stopPlayback() }
        restoreStreamVolume()
        if (AutomationMediaRegistry.videoOwner == activeOwner) AutomationMediaRegistry.videoOwner = null
        activeOwner = null
        if (stopReceiverRegistered) {
            runCatching { unregisterReceiver(stopReceiver) }
            stopReceiverRegistered = false
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AutomationVideoActivity"
        private const val DEFAULT_VOLUME = 30
        private const val EXTRA_SOURCE = "video_source"
        private const val EXTRA_VOLUME = "volume"
        private const val EXTRA_OWNER = "owner"
        private const val ACTION_STOP = "com.byd.wjx.action.STOP_AUTOMATION_VIDEO"

        fun play(context: Context, source: String, volume: Int, owner: String?) {
            context.startActivity(
                Intent(context, AutomationVideoActivity::class.java)
                    .putExtra(EXTRA_SOURCE, source)
                    .putExtra(EXTRA_VOLUME, volume.coerceIn(0, 100))
                    .putExtra(EXTRA_OWNER, owner)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
            )
        }

        fun stop(context: Context, owner: String? = null) {
            context.sendBroadcast(
                Intent(ACTION_STOP)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_OWNER, owner)
            )
        }
    }
}
