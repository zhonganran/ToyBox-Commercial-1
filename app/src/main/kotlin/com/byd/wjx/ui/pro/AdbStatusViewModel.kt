package com.byd.wjx.ui.pro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.data.autoservice.AdbOnDeviceClient
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdbStatusUiState(
    val connected: Boolean? = null,
    val dialogVisible: Boolean = false,
    val connecting: Boolean = false,
    val authorizationResult: AdbAuthorizationResult? = null,
)

enum class AdbAuthorizationResult {
    SUCCESS,
    FAILURE,
}

/** Owns the global top-bar ADB state and the one manual authorization flow. */
@HiltViewModel
class AdbStatusViewModel @Inject constructor(
    private val adb: AdbOnDeviceClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdbStatusUiState())
    val uiState: StateFlow<AdbStatusUiState> = _uiState.asStateFlow()
    private var authorizationJob: Job? = null

    init {
        viewModelScope.launch {
            while (isActive) {
                if (!_uiState.value.connecting) refreshConnectionState()
                delay(CONNECTION_CHECK_INTERVAL_MS)
            }
        }
    }

    fun showAuthorizationDialog() {
        _uiState.update {
            it.copy(
                dialogVisible = true,
                authorizationResult = null,
            )
        }
        viewModelScope.launch { refreshConnectionState() }
    }

    fun dismissAuthorizationDialog() {
        _uiState.update { it.copy(dialogVisible = false) }
    }

    fun requestAuthorization() {
        if (authorizationJob?.isActive == true) return
        authorizationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    dialogVisible = true,
                    connecting = true,
                    authorizationResult = null,
                )
            }
            val result = runCatching { adb.connect() }
                .getOrElse { Result.failure(it) }
            val connected = result.isSuccess && runCatching { adb.isConnected() }.getOrDefault(false)
            if (!connected) {
                WjxFileLogger.w(TAG, "manual ADB authorization failed: ${result.exceptionOrNull()?.message}")
            }
            _uiState.update {
                it.copy(
                    connected = connected,
                    connecting = false,
                    authorizationResult = if (connected) {
                        AdbAuthorizationResult.SUCCESS
                    } else {
                        AdbAuthorizationResult.FAILURE
                    },
                )
            }
        }
    }

    private suspend fun refreshConnectionState() {
        val connected = runCatching { adb.isConnected() }.getOrDefault(false)
        _uiState.update { current ->
            if (current.connected == connected) current else current.copy(connected = connected)
        }
    }

    companion object {
        private const val TAG = "AdbStatusViewModel"
        private const val CONNECTION_CHECK_INTERVAL_MS = 5_000L
    }
}
