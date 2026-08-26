package com.byd.wjx.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.cluster.SteeringWheelKeyService
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull

private sealed interface LearnUiState {
    data object Waiting : LearnUiState
    data class Rejected(val keyCode: Int) : LearnUiState
    data class Captured(val keyCode: Int) : LearnUiState
    data object TimedOut : LearnUiState
}

@Composable
internal fun LearnButtonDialog(
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
    preferLongPress: Boolean = false,
) {
    var state by remember { mutableStateOf<LearnUiState>(LearnUiState.Waiting) }
    DisposableEffect(Unit) {
        onDispose { clearSteeringKeyLearning() }
    }
    LaunchedEffect(Unit) {
        armSteeringKeyLearning(preferLongPress)
        SteeringWheelKeyService.capturedKey.filterNotNull().collect { result ->
            state = if (result.assignable) {
                SteeringWheelKeyService.learnMode = false
                LearnUiState.Captured(result.keyCode)
            } else {
                LearnUiState.Rejected(result.keyCode)
            }
        }
    }
    LaunchedEffect(state) {
        if (state is LearnUiState.Waiting || state is LearnUiState.Rejected) {
            delay(LEARN_TIMEOUT_MS)
            if (state is LearnUiState.Waiting || state is LearnUiState.Rejected) {
                SteeringWheelKeyService.learnMode = false
                state = LearnUiState.TimedOut
            }
        }
    }
    val restart = {
        armSteeringKeyLearning(preferLongPress)
        state = LearnUiState.Waiting
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.learn_button_dialog_title)) },
        text = { LearnButtonContent(state) },
        confirmButton = {
            when (val current = state) {
                is LearnUiState.Captured -> TextButton(onClick = { onSave(current.keyCode) }) {
                    Text(stringResource(R.string.learn_button_save))
                }
                is LearnUiState.TimedOut -> TextButton(onClick = restart) {
                    Text(stringResource(R.string.learn_button_again))
                }
                else -> Unit
            }
        },
        dismissButton = {
            if (state is LearnUiState.Captured) {
                TextButton(onClick = restart) {
                    Text(stringResource(R.string.learn_button_again))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.learn_button_cancel))
                }
            }
        },
    )
}

@Composable
private fun LearnButtonContent(state: LearnUiState) {
    when (state) {
        is LearnUiState.Waiting -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AccentGreen)
            Text(stringResource(R.string.learn_button_waiting))
        }
        is LearnUiState.Rejected -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.learn_button_rejected))
            Text(steeringButtonLabel(state.keyCode), color = TextSecondary, fontSize = 12.sp)
        }
        is LearnUiState.Captured -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.learn_button_captured))
            Text(
                "${steeringButtonLabel(state.keyCode)} (${state.keyCode})",
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
            )
        }
        is LearnUiState.TimedOut -> Text(stringResource(R.string.learn_button_timeout))
    }
}

private fun armSteeringKeyLearning(preferLongPress: Boolean) {
    SteeringWheelKeyService.capturedKey.value = null
    SteeringWheelKeyService.learnPreferLongPress = preferLongPress
    SteeringWheelKeyService.learnMode = true
}

private fun clearSteeringKeyLearning() {
    SteeringWheelKeyService.learnMode = false
    SteeringWheelKeyService.learnPreferLongPress = false
    SteeringWheelKeyService.capturedKey.value = null
}

private const val LEARN_TIMEOUT_MS = 10_000L
