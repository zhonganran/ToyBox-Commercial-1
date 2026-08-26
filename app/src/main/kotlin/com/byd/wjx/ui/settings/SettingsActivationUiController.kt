package com.byd.wjx.ui.settings

import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.security.EndpointPrivacy
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Owns authorization screen actions, payment request generations, and payment job cancellation. */
internal class SettingsActivationUiController(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<SettingsUiState>,
    private val coordinator: SettingsActivationCoordinator,
    private val refreshVehicleEventSound: suspend (String?) -> Unit,
    private val syncVehicleEventSound: suspend (String) -> Unit,
) {
    private var paymentJob: Job? = null
    private var paymentRequestId = 0L

    fun refresh(refreshDeviceCode: Boolean = true) {
        scope.launch {
            state.update { it.copy(activationLoading = true, activationMessage = null) }
            val snapshot = coordinator.loadSnapshot(refreshDeviceCode)
            state.update {
                it.copy(activation = snapshot, activationLoading = false)
            }
            refreshVehicleEventSound(null)
        }
    }

    fun updateCode(value: String) {
        state.update { it.copy(activationCodeInput = value.uppercase(Locale.US)) }
    }

    fun activate() {
        val code = state.value.activationCodeInput
        scope.launch {
            state.update { it.copy(activationLoading = true, activationMessage = null) }
            val result = coordinator.activate(code)
            state.update {
                it.copy(
                    activation = result.snapshot,
                    activationLoading = false,
                    activationMessage = result.message,
                    activationCodeInput = if (result.success) "" else it.activationCodeInput,
                )
            }
            if (result.success) {
                syncVehicleEventSound("activation_success")
            } else {
                refreshVehicleEventSound(null)
            }
        }
    }

    fun startPayment(paymentType: String) {
        val requestId = ++paymentRequestId
        paymentJob?.cancel()
        paymentJob = scope.launch {
            WjxFileLogger.i(TAG, "payment order create start type=$paymentType")
            try {
                coordinator.paymentUpdates(paymentType).collect { update ->
                    if (requestId == paymentRequestId) applyPaymentUpdate(update)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestId == paymentRequestId) {
                    state.update {
                        it.copy(
                            activationPayment = it.activationPayment.copy(
                                loading = false,
                                message = EndpointPrivacy.userMessage(error, "创建支付订单失败"),
                            ),
                        )
                    }
                    WjxFileLogger.w(
                        TAG,
                        "payment order create failed type=$paymentType: ${error.message}",
                        error,
                    )
                }
            } finally {
                if (requestId == paymentRequestId) paymentJob = null
            }
        }
    }

    fun dismissPayment() {
        paymentRequestId += 1L
        paymentJob?.cancel()
        paymentJob = null
        state.update { it.copy(activationPayment = ActivationPaymentUiState()) }
    }

    private suspend fun applyPaymentUpdate(update: ActivationPaymentUpdate) {
        when (update) {
            ActivationPaymentUpdate.Creating -> state.update {
                it.copy(
                    activationPayment = ActivationPaymentUiState(
                        loading = true,
                        message = "正在创建安全支付订单",
                    ),
                )
            }
            is ActivationPaymentUpdate.OrderCreated -> state.update {
                it.copy(
                    activationPayment = ActivationPaymentUiState(
                        order = update.order,
                        message = "请扫码支付，到账后会自动激活",
                    ),
                )
            }
            is ActivationPaymentUpdate.PollRetry -> state.update {
                it.copy(activationPayment = it.activationPayment.copy(message = update.message))
            }
            is ActivationPaymentUpdate.StatusUpdated -> {
                val result = update.status
                state.update {
                    it.copy(
                        activation = result.snapshot,
                        activationPayment = it.activationPayment.copy(
                            loading = false,
                            message = result.message,
                        ),
                    )
                }
                if (result.paid) syncVehicleEventSound("payment_activation_success")
            }
        }
    }

    private companion object {
        const val TAG = "SettingsActivationUi"
    }
}
