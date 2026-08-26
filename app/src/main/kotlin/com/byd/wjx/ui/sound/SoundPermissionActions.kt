package com.byd.wjx.ui.sound

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
internal fun rememberRealtimeHailingToggleAction(
    isActive: Boolean,
    onToggle: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnToggle by rememberUpdatedState(onToggle)
    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) currentOnToggle()
    }
    return {
        if (isActive || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            currentOnToggle()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
