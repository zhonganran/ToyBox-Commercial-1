package com.byd.wjx.ui.automation

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.PlaceEntity
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.ui.components.AppLaunchPickerDialog
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.*
import org.json.JSONObject

@Composable
internal fun MediaVolumeActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Max volume is device-dependent; read it once from the head unit's AudioManager.
    val maxVolume = remember {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        (am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
    }
    val current = (action.payload?.toIntOrNull() ?: 2).coerceIn(0, maxVolume)
    val label = stringResource(R.string.automation_action_media_volume_label)
    val namePrefix = stringResource(R.string.automation_action_media_volume)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = null,
            tint = AccentTeal,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, color = TextMuted)
        Spacer(Modifier.width(8.dp))
        Slider(
            value = current.toFloat(),
            onValueChange = { v ->
                val lvl = v.toInt().coerceIn(0, maxVolume)
                onUpdate(action.copy(payload = lvl.toString(), displayName = "$namePrefix: $lvl"))
            },
            valueRange = 0f..maxVolume.toFloat(),
            steps = (maxVolume - 1).coerceAtLeast(0),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "$current/$maxVolume",
            fontSize = 13.sp,
            color = AccentTeal,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp)
        )
    }
}

@Composable
internal fun OutsideSoundActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sounds = remember { SoundLibrary(context.applicationContext).listSounds() }
    val selectedPath = action.outsideSoundPath()
    val selected = sounds.firstOrNull { it.file.absolutePath == selectedPath }
    val selectedName = selected?.name
        ?: action.outsideSoundName().takeIf(String::isNotBlank)
        ?: stringResource(R.string.automation_action_outside_sound_empty)
    val isMissing = selectedPath.isNotBlank() && selected == null
    val loop = action.outsideSoundLoop()
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Outlined.VolumeUp,
                contentDescription = null,
                tint = AccentTeal,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.automation_action_outside_sound_label), fontSize = 13.sp, color = TextMuted)
            Spacer(Modifier.width(6.dp))
            Box(modifier = Modifier.weight(1f)) {
                Text(
                    selectedName,
                    fontSize = 13.sp,
                    color = if (isMissing || sounds.isEmpty()) Color(0xFFEF4444) else AccentTeal,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSurface, RoundedCornerShape(10.dp))
                        .border(1.dp, if (isMissing) Color(0xFFEF4444).copy(alpha = 0.45f) else CardBorder, RoundedCornerShape(10.dp))
                        .clickable { if (sounds.isNotEmpty()) expanded = true }
                        .padding(8.dp, 7.dp),
                    maxLines = 1
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxHeight(0.45f)
                ) {
                    sounds.forEach { sound ->
                        DropdownMenuItem(
                            text = { Text("${sound.name} · ${sound.sizeLabel}", fontSize = 13.sp) },
                            onClick = {
                                expanded = false
                                onUpdate(action.withOutsideSound(sound))
                            }
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CardSurface.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("循环播放", color = TextSecondary, fontSize = 12.sp)
            Switch(
                checked = loop,
                onCheckedChange = { onUpdate(action.withOutsideSoundLoop(it)) },
                colors = bydSwitchColors(),
                modifier = Modifier.size(width = 44.dp, height = 24.dp)
            )
        }
    }
}

@Composable
internal fun AutomationMediaActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isVideo = action.kind == "play_video"
    val selectedName = action.automationMediaFileName().ifBlank {
        if (isVideo) "选择视频" else "选择音乐"
    }
    val volume = action.automationMediaVolume()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val fileName = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: uri.lastPathSegment.orEmpty().substringAfterLast('/').ifBlank {
                if (isVideo) "已选视频" else "已选音乐"
            }
        onUpdate(action.withAutomationMedia(uri.toString(), fileName))
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (isVideo) "视频" else "音乐", fontSize = 13.sp, color = TextMuted)
            Spacer(Modifier.width(6.dp))
            Text(
                selectedName,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .clickable { launcher.launch(arrayOf(if (isVideo) "video/*" else "audio/*")) }
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                color = if (action.automationMediaPath().isBlank()) TextMuted else AccentTeal,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("音量", color = TextSecondary, fontSize = 12.sp)
            Slider(
                value = volume.toFloat(),
                onValueChange = { onUpdate(action.withAutomationMediaVolume(it.toInt())) },
                valueRange = 0f..100f,
                steps = 19,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("$volume%", color = AccentTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp))
        }
        if (!isVideo) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("循环播放", color = TextSecondary, fontSize = 12.sp)
                Switch(
                    checked = action.automationMediaLoop(),
                    onCheckedChange = { onUpdate(action.withAutomationMediaLoop(it)) },
                    colors = bydSwitchColors(),
                )
            }
        }
    }
}

