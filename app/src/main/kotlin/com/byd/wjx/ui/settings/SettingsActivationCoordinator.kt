package com.byd.wjx.ui.settings

import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.ActivationPaymentOrder
import com.byd.wjx.activation.ActivationPaymentStatus
import com.byd.wjx.activation.ActivationResult
import com.byd.wjx.activation.ActivationSnapshot
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

internal sealed interface ActivationPaymentUpdate {
    data object Creating : ActivationPaymentUpdate

    data class OrderCreated(val order: ActivationPaymentOrder) : ActivationPaymentUpdate

    data class PollRetry(val message: String) : ActivationPaymentUpdate

    data class StatusUpdated(val status: ActivationPaymentStatus) : ActivationPaymentUpdate
}

class SettingsActivationCoordinator private constructor(
    private val activationManager: ActivationManager,
    private val config: Config,
) {
    @Inject
    constructor(activationManager: ActivationManager) : this(
        activationManager = activationManager,
        config = Config(DEFAULT_PAYMENT_POLL_DELAY_MS),
    )

    internal constructor(
        activationManager: ActivationManager,
        paymentPollDelayMillis: Long,
    ) : this(activationManager, Config(paymentPollDelayMillis))

    internal suspend fun loadSnapshot(refreshDeviceCode: Boolean): ActivationSnapshot =
        if (refreshDeviceCode) activationManager.refreshSnapshot() else activationManager.currentSnapshot()

    internal suspend fun activate(code: String): ActivationResult = activationManager.activate(code)

    internal fun paymentUpdates(paymentType: String): Flow<ActivationPaymentUpdate> = flow {
        emit(ActivationPaymentUpdate.Creating)
        val order = activationManager.createPayment(paymentType)
        emit(ActivationPaymentUpdate.OrderCreated(order))

        while (currentCoroutineContext().isActive) {
            delay(config.paymentPollDelayMillis)
            val status = try {
                activationManager.checkPayment(order)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                emit(
                    ActivationPaymentUpdate.PollRetry(
                        error.message ?: "查询支付状态失败，正在重试",
                    ),
                )
                continue
            }
            emit(ActivationPaymentUpdate.StatusUpdated(status))
            if (status.paid || status.status == "expired" || status.status == "failed") return@flow
        }
    }

    private companion object {
        const val DEFAULT_PAYMENT_POLL_DELAY_MS = 2_500L
    }

    private data class Config(val paymentPollDelayMillis: Long)
}
