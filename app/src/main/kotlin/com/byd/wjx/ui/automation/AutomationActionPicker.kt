package com.byd.wjx.ui.automation

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentTeal
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

/** Category-aware selector shared by vehicle command editors. */
@Composable
internal fun CatalogDropdown(
    selected: String,
    items: List<String>,
    categories: List<String>,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Text(
            selected,
            fontSize = 13.sp,
            color = TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurface, RoundedCornerShape(10.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                .clickable { expanded = true }
                .padding(8.dp, 7.dp),
            maxLines = 1,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxHeight(0.5f),
        ) {
            var lastCategory = ""
            items.forEachIndexed { index, item ->
                val category = categories[index]
                if (category != lastCategory) {
                    lastCategory = category
                    DropdownMenuItem(
                        text = {
                            Text(category, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        },
                        onClick = {},
                        enabled = false,
                    )
                }
                DropdownMenuItem(
                    text = { Text(item, fontSize = 13.sp) },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}

/** Entry point for adding an action, grouped by the capability it invokes. */
@Composable
internal fun AddActionButton(
    onAdd: (ActionDef) -> Unit,
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showOverlayPrompt by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurfaceElevated.copy(alpha = 0.62f), RoundedCornerShape(14.dp))
                .border(1.dp, AccentTeal.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                .clickable { menuExpanded = true }
                .padding(12.dp, 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.automation_add_action_button), fontSize = 12.sp, color = TextMuted)
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.fillMaxHeight(0.72f),
        ) {
            AutomationMenuGroupHeader(
                stringResource(R.string.automation_action_group_vehicle),
                AccentTeal,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_dplus_command), fontSize = 13.sp) },
                onClick = {
                    menuExpanded = false
                    val action = ACTION_COMMANDS.first()
                    onAdd(ActionDef(action.command, action.name))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_sentry_mode), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAdd(newSentryModeAction()) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_auto_door), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAdd(newAutoDoorAction()) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_mirror_tilt), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAdd(newMirrorTiltAction()) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_mirror_restore), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAdd(newMirrorRestoreAction()) },
            )

            AutomationMenuGroupHeader(
                stringResource(R.string.automation_action_group_media),
                AccentTeal,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_inside_sound), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAdd(newInsideSoundAction(context)) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_stop_inside_sound), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAdd(newStopInsideSoundAction()) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_outside_sound), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAdd(newOutsideSoundAction(context)) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_stop_outside_sound), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAdd(newStopOutsideSoundAction()) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_play_music), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAdd(newPlayMusicAction()) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_stop_music), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAdd(newStopMusicAction()) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_play_video), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAdd(newPlayVideoAction()) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_stop_video), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAdd(newStopVideoAction()) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_media_volume), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAdd(newMediaVolumeAction()) },
            )

            AutomationMenuGroupHeader(
                stringResource(R.string.automation_action_group_apps),
                AccentTeal,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_app_launch), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAdd(newAppLaunchAction(context)) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_go_home), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAdd(newGoHomeAction()) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_delay), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAdd(newDelayAction()) },
            )

            AutomationMenuGroupHeader(
                stringResource(R.string.automation_action_group_notifications),
                AccentTeal,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_notification_silent), fontSize = 13.sp) },
                onClick = {
                    menuExpanded = false
                    onAdd(newNotificationAction(silent = true, context = context))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_notification_sound), fontSize = 13.sp) },
                onClick = {
                    menuExpanded = false
                    onAdd(newNotificationAction(silent = false, context = context))
                    if (!Settings.canDrawOverlays(context)) showOverlayPrompt = true
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_call), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAdd(newCallAction(context)) },
            )

            AutomationMenuGroupHeader(
                stringResource(R.string.automation_action_group_network),
                AccentTeal,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_http), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAdd(newHttpAction()) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_url), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAdd(newUrlAction(context)) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_navigate), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAdd(newNavigateAction(context)) },
            )
        }
    }

    if (showOverlayPrompt) {
        AlertDialog(
            onDismissRequest = { showOverlayPrompt = false },
            containerColor = CardSurface,
            title = {
                Text(
                    stringResource(R.string.automation_overlay_permission_title),
                    color = TextPrimary,
                    fontSize = 16.sp,
                )
            },
            text = {
                Text(
                    stringResource(R.string.automation_overlay_permission_text),
                    fontSize = 13.sp,
                    color = TextPrimary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOverlayPrompt = false
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                ) {
                    Text(stringResource(R.string.automation_overlay_open_settings), color = AccentGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPrompt = false }) {
                    Text(stringResource(R.string.automation_overlay_later), color = TextSecondary)
                }
            },
        )
    }
}

internal fun newDelayAction(): ActionDef = ActionDef(
    command = "delay_1000",
    displayName = "延迟 1 秒",
    kind = "delay",
    payload = "1000",
)

internal fun newMediaVolumeAction(): ActionDef = ActionDef(
    command = "media_volume",
    displayName = "媒体音量：2",
    kind = "media_volume",
    payload = "2",
)
