package com.byd.wjx.ui.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentTeal
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun NavigateActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    val name = action.navigateName()
    val preview = if (name.isNotBlank()) name else stringResource(R.string.automation_tap_to_set_destination)

    Row(
        modifier = modifier
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable { editing = true }
            .padding(8.dp, 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Navigation,
            contentDescription = null,
            tint = AccentTeal,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = preview,
            fontSize = 13.sp,
            color = if (name.isBlank()) TextMuted else TextPrimary,
            maxLines = 1,
        )
    }

    if (editing) {
        NavigateEditDialog(
            action = action,
            onDismiss = { editing = false },
            onSave = { latitude, longitude, destinationName ->
                onUpdate(action.withNavigate(latitude, longitude, destinationName))
                editing = false
            },
        )
    }
}

@Composable
private fun NavigateEditDialog(
    action: ActionDef,
    onDismiss: () -> Unit,
    onSave: (latitude: Double, longitude: Double, name: String) -> Unit,
) {
    var latitudeText by remember(action) { mutableStateOf(action.navigateLat()?.toString().orEmpty()) }
    var longitudeText by remember(action) { mutableStateOf(action.navigateLon()?.toString().orEmpty()) }
    var nameText by remember(action) { mutableStateOf(action.navigateName()) }
    val latitude = latitudeText.trim().toDoubleOrNull()
    val longitude = longitudeText.trim().toDoubleOrNull()
    val canSave = latitude != null && latitude in -90.0..90.0 &&
        longitude != null && longitude in -180.0..180.0 &&
        !(latitude == 0.0 && longitude == 0.0)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = AccentGreen,
        unfocusedBorderColor = CardBorder,
        focusedLabelColor = AccentGreen,
        unfocusedLabelColor = TextSecondary,
        cursorColor = AccentGreen,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = {
            Text(stringResource(R.string.automation_navigate_dialog_title), color = TextPrimary, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text(stringResource(R.string.automation_navigate_name_label)) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = latitudeText,
                    onValueChange = { latitudeText = it },
                    label = { Text(stringResource(R.string.automation_navigate_latitude_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = longitudeText,
                    onValueChange = { longitudeText = it },
                    label = { Text(stringResource(R.string.automation_navigate_longitude_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (canSave) onSave(latitude, longitude, nameText.trim())
                },
                enabled = canSave,
            ) {
                Text(
                    stringResource(R.string.automation_save_button),
                    color = if (canSave) AccentGreen else TextMuted,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.automation_cancel_button), color = TextSecondary)
            }
        },
    )
}
