package com.byd.wjx.cluster

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.core.content.ContextCompat

class ClusterMirrorConsentActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.startForegroundService(
            this,
            Intent(this, ClusterMirrorService::class.java).setAction(ClusterMirrorService.ACTION_PREPARE),
        )
        val manager = getSystemService(MediaProjectionManager::class.java) ?: run {
            ContextCompat.startForegroundService(
                this,
                Intent(this, ClusterMirrorService::class.java)
                    .setAction(ClusterMirrorService.ACTION_SET_PROJECTION)
                    .putExtra(ClusterMirrorService.EXTRA_RESULT_CODE, RESULT_CANCELED),
            )
            finish()
            return
        }
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE) {
            val serviceIntent = Intent(this, ClusterMirrorService::class.java)
                .setAction(ClusterMirrorService.ACTION_SET_PROJECTION)
                .putExtra(ClusterMirrorService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(ClusterMirrorService.EXTRA_RESULT_DATA, data)
            ContextCompat.startForegroundService(this, serviceIntent)
            finish()
        }
    }

    companion object {
        private const val REQUEST_CAPTURE = 7301
    }
}
