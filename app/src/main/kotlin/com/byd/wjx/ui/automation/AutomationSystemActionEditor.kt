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
internal fun NotificationActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    val silent = action.kind == "notification_silent"
    val title = action.notificationTitle()
    val text = action.notificationText()
    val preview = when {
        title.isNotBlank() && text.isNotBlank() -> "$title — $text"
        title.isNotBlank() -> title
        text.isNotBlank() -> text
        else -> stringResource(R.string.automation_tap_to_configure)
    }

    Row(
        modifier = modifier
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable { editing = true }
            .padding(8.dp, 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (silent) Icons.Outlined.NotificationsOff else Icons.Outlined.Notifications,
            contentDescription = null,
            tint = AccentTeal,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = preview,
            fontSize = 13.sp,
            color = if (title.isBlank() && text.isBlank()) TextMuted else TextPrimary,
            maxLines = 1
        )
    }

    if (editing) {
        NotificationEditDialog(
            initialTitle = title,
            initialText = text,
            silent = silent,
            onDismiss = { editing = false },
            onSave = { newTitle, newText ->
                onUpdate(action.withNotification(newTitle, newText))
                editing = false
            }
        )
    }
}

@Composable
private fun NotificationEditDialog(
    initialTitle: String,
    initialText: String,
    silent: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var titleText by rememberSaveable { mutableStateOf(initialTitle) }
    var bodyText by rememberSaveable { mutableStateOf(initialText) }
    val canSave = titleText.trim().isNotBlank() && titleText.trim().length <= 40

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = AccentGreen,
        unfocusedBorderColor = CardBorder,
        focusedLabelColor = AccentGreen,
        unfocusedLabelColor = TextSecondary,
        cursorColor = AccentGreen
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = {
            Text(
                text = if (silent) stringResource(R.string.automation_action_notification_silent) else stringResource(R.string.automation_action_notification_sound),
                color = TextPrimary,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { if (it.length <= 40) titleText = it },
                    label = { Text(stringResource(R.string.automation_notification_title_label)) },
                    singleLine = true,
                    isError = titleText.isNotEmpty() && !canSave,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bodyText,
                    onValueChange = { if (it.length <= 200) bodyText = it },
                    label = { Text(stringResource(R.string.automation_notification_body_label)) },
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (canSave) onSave(titleText.trim(), bodyText.trim()) }, enabled = canSave) {
                Text(stringResource(R.string.automation_save_button), color = if (canSave) AccentGreen else TextMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.automation_cancel_button), color = TextSecondary)
            }
        }
    )
}

// --- App Launch Action Controls ---

@Composable
internal fun AppLaunchActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    var pendingMinimize by remember(action) { mutableStateOf(action.appLaunchMinimize()) }
    val pkg = action.appLaunchPackageName()
    val label = action.appLaunchLabel()
    val preview = when {
        label.isNotBlank() -> label
        pkg.isNotBlank() -> pkg
        else -> stringResource(R.string.automation_tap_to_pick_app)
    }

    Row(
        modifier = modifier
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable { editing = true }
            .padding(8.dp, 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Apps,
            contentDescription = null,
            tint = AccentTeal,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = preview,
            fontSize = 13.sp,
            color = if (label.isBlank() && pkg.isBlank()) TextMuted else TextPrimary,
            maxLines = 1
        )
    }

    if (editing) {
        AppLaunchPickerDialog(
            currentPackage = pkg,
            showMinimizeToggle = true,
            initialMinimize = pendingMinimize,
            onMinimizeChanged = { pendingMinimize = it },
            onDismiss = {
                pendingMinimize = action.appLaunchMinimize()
                editing = false
            },
            onSelect = { newPkg, newLabel ->
                onUpdate(action.withAppLaunch(newPkg, newLabel, pendingMinimize))
                editing = false
            },
        )
    }
}

// --- Call Action Controls ---

@Composable
internal fun CallActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    val phone = action.callPhone()
    val name = action.callName()
    val preview = when {
        name.isNotBlank() && phone.isNotBlank() -> "$name — $phone"
        phone.isNotBlank() -> phone
        else -> stringResource(R.string.automation_tap_to_set_phone)
    }

    Row(
        modifier = modifier
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable { editing = true }
            .padding(8.dp, 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Call,
            contentDescription = null,
            tint = AccentTeal,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = preview,
            fontSize = 13.sp,
            color = if (phone.isBlank()) TextMuted else TextPrimary,
            maxLines = 1
        )
    }

    if (editing) {
        CallEditDialog(
            initialPhone = phone,
            initialName = name,
            initialAutoDial = action.callAutoDial(),
            onDismiss = { editing = false },
            onSave = { newPhone, newName, newAutoDial ->
                onUpdate(action.withCall(newPhone, newName, newAutoDial))
                editing = false
            }
        )
    }
}

@Composable
private fun CallEditDialog(
    initialPhone: String,
    initialName: String,
    initialAutoDial: Boolean,
    onDismiss: () -> Unit,
    onSave: (phone: String, name: String, autoDial: Boolean) -> Unit
) {
    var phoneText by rememberSaveable { mutableStateOf(initialPhone) }
    var nameText by rememberSaveable { mutableStateOf(initialName) }
    var autoDial by remember { mutableStateOf(initialAutoDial) }
    val trimmedPhone = phoneText.trim()
    val canSave = trimmedPhone.isNotBlank() && trimmedPhone.length in 5..20

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = AccentGreen,
        unfocusedBorderColor = CardBorder,
        focusedLabelColor = AccentGreen,
        unfocusedLabelColor = TextSecondary,
        cursorColor = AccentGreen
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = { Text(stringResource(R.string.automation_call_dialog_title), color = TextPrimary, fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text(stringResource(R.string.automation_call_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phoneText,
                    onValueChange = { phoneText = it },
                    label = { Text(stringResource(R.string.automation_call_phone_label)) },
                    singleLine = true,
                    isError = phoneText.isNotBlank() && !canSave,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { autoDial = !autoDial }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = autoDial,
                        onCheckedChange = { autoDial = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AccentGreen,
                            uncheckedColor = TextMuted,
                            checkmarkColor = AccentContent
                        )
                    )
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(stringResource(R.string.automation_call_auto_dial_label), color = TextPrimary, fontSize = 13.sp)
                        Text(
                            stringResource(R.string.automation_call_auto_dial_hint),
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (canSave) onSave(trimmedPhone, nameText.trim(), autoDial) },
                enabled = canSave
            ) {
                Text(stringResource(R.string.automation_save_button), color = if (canSave) AccentGreen else TextMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.automation_cancel_button), color = TextSecondary)
            }
        }
    )
}

// --- Navigate Action Controls ---

@Composable
internal fun NavigateActionControls(
    action: ActionDef,
    places: List<PlaceEntity>,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    val name = action.navigateName()
    val preview = if (name.isNotBlank()) name else stringResource(R.string.automation_tap_to_pick_place)

    Row(
        modifier = modifier
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable { editing = true }
            .padding(8.dp, 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Navigation,
            contentDescription = null,
            tint = AccentTeal,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = preview,
            fontSize = 13.sp,
            color = if (name.isBlank()) TextMuted else TextPrimary,
            maxLines = 1
        )
    }

    if (editing) {
        NavigateEditDialog(
            action = action,
            places = places,
            onDismiss = { editing = false },
            onSave = { lat, lon, placeName ->
                onUpdate(action.withNavigate(lat, lon, placeName))
                editing = false
            }
        )
    }
}

@Composable
private fun NavigateEditDialog(
    action: ActionDef,
    places: List<PlaceEntity>,
    onDismiss: () -> Unit,
    onSave: (lat: Double, lon: Double, name: String) -> Unit
) {
    // Preselect by name match, then by lat/lon proximity, otherwise null
    val initialPlace = remember(action, places) {
        val actionName = action.navigateName()
        val actionLat = action.navigateLat()
        val actionLon = action.navigateLon()
        places.find { it.name == actionName }
            ?: if (actionLat != null && actionLon != null) {
                places.find { Math.abs(it.lat - actionLat) < 0.0001 && Math.abs(it.lon - actionLon) < 0.0001 }
            } else null
    }
    var selectedPlace by remember { mutableStateOf(initialPlace) }
    var placeExpanded by remember { mutableStateOf(false) }

    val canSave = selectedPlace != null && places.isNotEmpty()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = AccentGreen,
        unfocusedBorderColor = CardBorder,
        focusedLabelColor = AccentGreen,
        unfocusedLabelColor = TextSecondary,
        cursorColor = AccentGreen
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = { Text(stringResource(R.string.automation_navigate_dialog_title), color = TextPrimary, fontSize = 16.sp) },
        text = {
            if (places.isEmpty()) {
                Text(
                    text = stringResource(R.string.automation_navigate_no_places_hint),
                    fontSize = 13.sp,
                    color = TextMuted
                )
            } else {
                Column {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = selectedPlace?.name ?: stringResource(R.string.automation_navigate_pick_place_placeholder),
                            fontSize = 13.sp,
                            color = if (selectedPlace == null) TextMuted else TextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardSurface, RoundedCornerShape(12.dp))
                                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                                .clickable { placeExpanded = true }
                                .padding(12.dp, 10.dp),
                            maxLines = 1
                        )
                        DropdownMenu(expanded = placeExpanded, onDismissRequest = { placeExpanded = false }) {
                            places.forEach { place ->
                                DropdownMenuItem(
                                    text = { Text(place.name, fontSize = 13.sp) },
                                    onClick = {
                                        placeExpanded = false
                                        selectedPlace = place
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val p = selectedPlace
                    if (canSave && p != null) onSave(p.lat, p.lon, p.name)
                },
                enabled = canSave
            ) {
                Text(stringResource(R.string.automation_save_button), color = if (canSave) AccentGreen else TextMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.automation_cancel_button), color = TextSecondary)
            }
        }
    )
}

// --- URL Action Controls ---

@Composable
internal fun UrlActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    val url = action.urlString()
    val preview = if (url.isNotBlank()) url else stringResource(R.string.automation_tap_to_set_url)

    Row(
        modifier = modifier
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable { editing = true }
            .padding(8.dp, 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Link,
            contentDescription = null,
            tint = AccentTeal,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = preview,
            fontSize = 13.sp,
            color = if (url.isBlank()) TextMuted else TextPrimary,
            maxLines = 1
        )
    }

    if (editing) {
        UrlEditDialog(
            initialUrl = url,
            initialMinimize = action.urlMinimize(),
            onDismiss = { editing = false },
            onSave = { newUrl, newMinimize ->
                onUpdate(action.withUrl(newUrl, newMinimize))
                editing = false
            }
        )
    }
}

@Composable
private fun UrlEditDialog(
    initialUrl: String,
    initialMinimize: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit
) {
    var urlText by remember { mutableStateOf(initialUrl) }
    var minimize by remember { mutableStateOf(initialMinimize) }
    val trimmed = urlText.trim()
    val urlValid = trimmed.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*:.+"))
    val canSave = trimmed.isNotBlank() && urlValid

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = AccentGreen,
        unfocusedBorderColor = CardBorder,
        focusedLabelColor = AccentGreen,
        unfocusedLabelColor = TextSecondary,
        cursorColor = AccentGreen,
        errorBorderColor = Color(0xFFEF4444),
        errorLabelColor = Color(0xFFEF4444)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = { Text(stringResource(R.string.automation_url_dialog_title), color = TextPrimary, fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text(stringResource(R.string.automation_url_field_label)) },
                    singleLine = true,
                    isError = urlText.isNotBlank() && !urlValid,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { minimize = !minimize }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = minimize,
                        onCheckedChange = { minimize = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AccentGreen,
                            uncheckedColor = CardBorder,
                            checkmarkColor = TextPrimary
                        )
                    )
                    Text(
                        stringResource(R.string.automation_url_minimize_label),
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (canSave) onSave(trimmed, minimize) }, enabled = canSave) {
                Text(stringResource(R.string.automation_save_button), color = if (canSave) AccentGreen else TextMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.automation_cancel_button), color = TextSecondary)
            }
        }
    )
}


