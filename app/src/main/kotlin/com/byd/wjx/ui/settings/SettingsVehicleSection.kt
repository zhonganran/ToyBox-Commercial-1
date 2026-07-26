package com.byd.wjx.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.ui.theme.AccentContent
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun BatterySection(state: SettingsUiState, viewModel: SettingsViewModel) {
    SectionHeader(text = stringResource(R.string.settings_battery_section_header))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsTextField(
                    label = stringResource(R.string.settings_battery_capacity_label),
                    value = state.batteryCapacity,
                    onValueChange = viewModel::updateBatteryCapacity,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                SettingsTextField(
                    label = stringResource(R.string.settings_fuel_tank_capacity_label),
                    value = state.fuelTankCapacity,
                    onValueChange = viewModel::updateFuelTankCapacity,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                SettingsSaveButton(onClick = viewModel::saveEnergyCapacities)
            }
            state.capacitySaveStatus?.let {
                Text(
                    text = it,
                    color = if (it == stringResource(R.string.settings_saved)) AccentGreen else AccentOrange,
                    fontSize = 12.sp,
                )
            }
            Text(
                stringResource(R.string.settings_fuel_tank_capacity_note),
                color = TextSecondary,
                fontSize = 12.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsTextField(
                    label = stringResource(R.string.settings_tariff_home_label, state.currencySymbol),
                    value = state.homeTariff,
                    onValueChange = viewModel::updateHomeTariff,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                SettingsTextField(
                    label = stringResource(R.string.settings_tariff_dc_label, state.currencySymbol),
                    value = state.dcTariff,
                    onValueChange = viewModel::updateDcTariff,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsTextField(
                    label = stringResource(R.string.settings_fuel_price_label, state.currencySymbol),
                    value = state.fuelPricePerLiter,
                    onValueChange = viewModel::updateFuelPricePerLiter,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.weight(1f))
                SettingsSaveButton(onClick = viewModel::saveTariffs)
            }
            state.tariffSaveStatus?.let {
                Text(it, color = AccentGreen, fontSize = 12.sp)
            }
            Text(stringResource(R.string.settings_tariff_trip_label), color = TextSecondary, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitChip("慢充", state.tripCostTariff == "home") { viewModel.saveTripCostTariff("home") }
                UnitChip("快充", state.tripCostTariff == "dc") { viewModel.saveTripCostTariff("dc") }
                UnitChip(
                    stringResource(R.string.settings_tariff_trip_custom_chip),
                    state.tripCostTariff != "home" && state.tripCostTariff != "dc",
                ) {
                    viewModel.saveTripCostTariff(state.homeTariff)
                }
            }
            if (state.tripCostTariff != "home" && state.tripCostTariff != "dc") {
                SettingsTextField(
                    label = stringResource(R.string.settings_tariff_custom_label, state.currencySymbol),
                    value = state.tripCostTariff,
                    onValueChange = viewModel::saveTripCostTariff,
                    keyboardType = KeyboardType.Decimal,
                )
            }

            Text(
                stringResource(R.string.settings_tariff_future_note),
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )

            HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 4.dp))

            Button(
                onClick = viewModel::showRecalcConfirm,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentOrange),
                border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.4f)),
            ) {
                Text(stringResource(R.string.settings_recalc_all_button), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            state.recalcStatus?.let {
                Text(it, color = AccentGreen, fontSize = 12.sp)
            }
            Text(
                stringResource(R.string.settings_recalc_note),
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
    }

    SectionHeader(text = stringResource(R.string.settings_consumption_section_header))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsTextField(
                label = stringResource(R.string.settings_consumption_good_label),
                value = state.consumptionGood,
                onValueChange = viewModel::saveConsumptionGood,
                keyboardType = KeyboardType.Decimal,
            )
            SettingsTextField(
                label = stringResource(R.string.settings_consumption_bad_label),
                value = state.consumptionBad,
                onValueChange = viewModel::saveConsumptionBad,
                keyboardType = KeyboardType.Decimal,
            )
        }
    }
}

@Composable
internal fun PlacesSection(onNavigateToPlaces: () -> Unit) {
    SectionHeader(text = stringResource(R.string.settings_places_section_header))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToPlaces)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Place,
                contentDescription = null,
                tint = AccentGreen,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_places_entry_title),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stringResource(R.string.settings_places_entry_description),
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = AccentGreen,
            unfocusedBorderColor = CardBorder,
            focusedLabelColor = AccentGreen,
            unfocusedLabelColor = TextSecondary,
            cursorColor = AccentGreen,
        ),
    )
}

@Composable
private fun SettingsSaveButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(92.dp)
            .height(56.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentGreen,
            contentColor = AccentContent,
        ),
    ) {
        Text(
            text = stringResource(R.string.settings_save_button),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
