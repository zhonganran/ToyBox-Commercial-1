package com.byd.wjx.ui.settings

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
internal fun rememberTripGpsToggleAction(
    onEnabledChange: (Boolean) -> Unit,
): (Boolean) -> Unit {
    val context = LocalContext.current
    val currentOnEnabledChange by rememberUpdatedState(onEnabledChange)
    val backgroundPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> currentOnEnabledChange(granted) }
    val finePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            currentOnEnabledChange(false)
        } else if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            backgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            currentOnEnabledChange(true)
        }
    }
    return { enabled ->
        when {
            !enabled -> currentOnEnabledChange(false)
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED -> {
                finePermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                ) != PackageManager.PERMISSION_GRANTED -> {
                backgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            else -> currentOnEnabledChange(true)
        }
    }
}
