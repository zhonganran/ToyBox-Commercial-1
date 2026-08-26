package com.byd.wjx.ui.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.graphics.Color
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
internal fun UrlActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier,
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Link, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = preview,
            fontSize = 13.sp,
            color = if (url.isBlank()) TextMuted else TextPrimary,
            maxLines = 1,
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
            },
        )
    }
}

@Composable
private fun UrlEditDialog(
    initialUrl: String,
    initialMinimize: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit,
) {
    var urlText by remember { mutableStateOf(initialUrl) }
    var minimize by remember { mutableStateOf(initialMinimize) }
    val trimmed = urlText.trim()
    val urlValid = trimmed.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*:.+"))
    val canSave = trimmed.isNotBlank() && urlValid
    val errorColor = Color(0xFFEF4444)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = AccentGreen,
        unfocusedBorderColor = CardBorder,
        focusedLabelColor = AccentGreen,
        unfocusedLabelColor = TextSecondary,
        cursorColor = AccentGreen,
        errorBorderColor = errorColor,
        errorLabelColor = errorColor,
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
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { minimize = !minimize }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = minimize,
                        onCheckedChange = { minimize = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AccentGreen,
                            uncheckedColor = CardBorder,
                            checkmarkColor = TextPrimary,
                        ),
                    )
                    Text(
                        stringResource(R.string.automation_url_minimize_label),
                        fontSize = 13.sp,
                        color = TextPrimary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (canSave) onSave(trimmed, minimize) }, enabled = canSave) {
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
