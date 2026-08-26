package com.byd.wjx.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.byd.wjx.cluster.ClusterEntryPoint
import com.byd.wjx.cluster.ClusterProjectionManager
import com.byd.wjx.cluster.KeyShortcutActionType
import com.byd.wjx.cluster.KeyShortcutPressType
import com.byd.wjx.cluster.KeyShortcutSettings
import com.byd.wjx.cluster.SteeringWheelKeyService
import com.byd.wjx.storage.SoundLibrary
import dagger.hilt.android.EntryPointAccessors

@Composable
internal fun SettingsKeyShortcutCoordinator(
    activationAllowed: Boolean,
    projectionEnabled: Boolean,
    projectionTriggerKey: Int,
) {
    val context = LocalContext.current
    val settings = remember(context) { KeyShortcutSettings(context) }
    val soundLibrary = remember(context) { SoundLibrary(context.applicationContext) }
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ClusterEntryPoint::class.java,
        )
    }
    var bindings by remember { mutableStateOf(settings.load()) }
    var editor by remember { mutableStateOf<KeyShortcutEditorState?>(null) }
    var learning by remember { mutableStateOf(false) }
    var sounds by remember { mutableStateOf(soundLibrary.listSounds()) }
    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        Toast.makeText(
            context,
            if (granted) "麦克风权限已允许" else "未允许麦克风权限，实时喊话无法启动",
            Toast.LENGTH_SHORT,
        ).show()
    }

    KeyShortcutSection(
        bindings = bindings,
        activationAllowed = activationAllowed,
        onAdd = {
            if (!activationAllowed) {
                Toast.makeText(context, "试用已结束，按键快捷操作需要激活后使用", Toast.LENGTH_SHORT).show()
            } else {
                sounds = soundLibrary.listSounds()
                editor = KeyShortcutEditorState()
            }
        },
        onEdit = {
            sounds = soundLibrary.listSounds()
            editor = it.toEditorState()
        },
        onEnabledChanged = { binding, checked ->
            val enabledBinding = if (checked && !activationAllowed) binding.copy(enabled = false)
                else binding.copy(enabled = checked)
            bindings = settings.upsert(enabledBinding)
            if (checked && !activationAllowed) {
                Toast.makeText(context, "试用已结束，按键快捷操作需要激活后使用", Toast.LENGTH_SHORT).show()
            } else {
                SteeringWheelKeyService.resetShortcutRuntimeState()
                ClusterProjectionManager.enableStarControl(
                    entryPoint.helperClient(),
                    entryPoint.helperBootstrap(),
                )
            }
        },
        onDelete = { binding ->
            bindings = settings.delete(binding.id)
            SteeringWheelKeyService.resetShortcutRuntimeState()
        },
    )

    editor?.let { currentEditor ->
        KeyShortcutEditorDialog(
            editor = currentEditor,
            sounds = sounds,
            onEditorChanged = { editor = it },
            onLearnKey = {
                ClusterProjectionManager.enableStarControl(
                    entryPoint.helperClient(),
                    entryPoint.helperBootstrap(),
                )
                learning = true
            },
            onDismiss = { editor = null },
            onSave = { next ->
                val binding = next.toBinding()
                if (binding.enabled && !activationAllowed) {
                    Toast.makeText(context, "试用已结束，按键快捷操作需要激活后使用", Toast.LENGTH_SHORT).show()
                } else {
                    bindings = settings.upsert(binding)
                    SteeringWheelKeyService.resetShortcutRuntimeState()
                    editor = null
                    if (
                        binding.actionType == KeyShortcutActionType.RealtimeHailing &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    ClusterProjectionManager.enableStarControl(
                        entryPoint.helperClient(),
                        entryPoint.helperBootstrap(),
                    )
                    val message = if (projectionEnabled && binding.keyCode == projectionTriggerKey) {
                        "已保存；该按键也用于仪表投射，快捷操作会优先执行"
                    } else {
                        "按键快捷操作已保存"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
    if (learning) {
        LearnButtonDialog(
            onSave = { code ->
                editor = editor?.copy(keyCode = code)
                learning = false
            },
            onDismiss = { learning = false },
            preferLongPress = editor?.pressType == KeyShortcutPressType.Long,
        )
    }
}
