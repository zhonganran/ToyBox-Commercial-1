package com.byd.wjx.ui.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.byd.wjx.R
import com.byd.wjx.cluster.KeyShortcutActionType
import com.byd.wjx.cluster.KeyShortcutBinding
import com.byd.wjx.cluster.KeyShortcutPressType
import com.byd.wjx.cluster.knownButtonNameRes
import java.util.UUID

internal data class KeyShortcutEditorState(
    val id: String = UUID.randomUUID().toString(),
    val keyCode: Int = 0,
    val pressType: KeyShortcutPressType = KeyShortcutPressType.Short,
    val actionType: KeyShortcutActionType = KeyShortcutActionType.PlaySound,
    val soundPath: String = "",
    val soundName: String = "",
    val enabled: Boolean = true,
)

internal fun KeyShortcutBinding.toEditorState(): KeyShortcutEditorState =
    KeyShortcutEditorState(
        id = id,
        keyCode = keyCode,
        pressType = pressType,
        actionType = actionType,
        soundPath = soundPath,
        soundName = soundName,
        enabled = enabled,
    )

internal fun KeyShortcutEditorState.toBinding(): KeyShortcutBinding =
    KeyShortcutBinding(
        id = id,
        keyCode = keyCode,
        pressType = pressType,
        actionType = actionType,
        soundPath = soundPath,
        soundName = soundName,
        enabled = enabled,
    )

internal fun resolveAppLabel(context: Context, packageName: String): String = runCatching {
    val packageManager = context.packageManager
    packageManager.getApplicationLabel(
        packageManager.getApplicationInfo(packageName, 0),
    ).toString()
}.getOrDefault(packageName)

@Composable
internal fun steeringButtonLabel(keyCode: Int): String {
    val nameResource = knownButtonNameRes(keyCode)
    return if (nameResource != 0) {
        stringResource(nameResource)
    } else {
        stringResource(R.string.steering_button_unknown, keyCode)
    }
}
