package com.byd.wjx.ui.settings

import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.ActivationPaymentOrder
import com.byd.wjx.activation.ActivationPaymentStatus
import com.byd.wjx.activation.ActivationSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsActivationCoordinatorTest {
    @Test
    fun `payment flow creates polls and stops on terminal status`() = runTest {
        val manager = mockk<ActivationManager>()
        val order = paymentOrder()
        coEvery { manager.createPayment("alipay") } returns order
        coEvery { manager.checkPayment(order) } returns paymentStatus(status = "expired")
        val coordinator = SettingsActivationCoordinator(manager, paymentPollDelayMillis = 1L)

        val updates = coordinator.paymentUpdates("alipay").toList()

        assertEquals(3, updates.size)
        assertTrue(updates[0] is ActivationPaymentUpdate.Creating)
        assertEquals(order, (updates[1] as ActivationPaymentUpdate.OrderCreated).order)
        assertEquals(
            "expired",
            (updates[2] as ActivationPaymentUpdate.StatusUpdated).status.status,
        )
        coVerify(exactly = 1) { manager.checkPayment(order) }
    }

    @Test
    fun `transient poll failure is reported before polling continues`() = runTest {
        val manager = mockk<ActivationManager>()
        val order = paymentOrder()
        var checks = 0
        coEvery { manager.createPayment("wxpay") } returns order
        coEvery { manager.checkPayment(order) } coAnswers {
            checks += 1
            if (checks == 1) error("临时网络异常")
            paymentStatus(status = "paid", paid = true)
        }
        val coordinator = SettingsActivationCoordinator(manager, paymentPollDelayMillis = 1L)

        val updates = coordinator.paymentUpdates("wxpay").toList()

        assertEquals("临时网络异常", (updates[2] as ActivationPaymentUpdate.PollRetry).message)
        assertEquals(true, (updates[3] as ActivationPaymentUpdate.StatusUpdated).status.paid)
        coVerify(exactly = 2) { manager.checkPayment(order) }
    }

    private fun paymentOrder() = ActivationPaymentOrder(
        orderNo = "order-1",
        paymentType = "alipay",
        paymentName = "支付宝",
        payType = "url",
        checkoutMode = "hosted",
        payInfo = "https://pay.example/order-1",
        payUrl = "https://pay.example/order-1",
        amount = "15.90",
        pollToken = "poll-1",
        expiresAt = 1_800_000_000_000L,
    )

    private fun paymentStatus(status: String, paid: Boolean = false) = ActivationPaymentStatus(
        status = status,
        paid = paid,
        message = status,
        snapshot = ActivationSnapshot(activated = paid),
    )
}
