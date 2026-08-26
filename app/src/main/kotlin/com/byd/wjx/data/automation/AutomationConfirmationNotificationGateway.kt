package com.byd.wjx.data.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.byd.wjx.R
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

internal const val AUTOMATION_CONFIRM_ACTION = "com.byd.wjx.AUTOMATION_CONFIRM"
internal const val AUTOMATION_CANCEL_ACTION = "com.byd.wjx.AUTOMATION_CANCEL"
internal const val AUTOMATION_NOTIFICATION_ID_EXTRA = "notif_id"

internal class AutomationConfirmationNotificationGateway(
    private val context: Context,
    private val vehicleStateRepository: VehicleStateRepository,
    private val executionCoordinator: AutomationActionExecutionCoordinator,
    private val scope: CoroutineScope,
) {
    private data class PendingConfirmation(
        val rule: RuleEntity,
        val actions: List<ActionDef>,
        val snapshot: String,
        val createdAt: Long,
        val phase: String,
    )

    private val pendingConfirmations = ConcurrentHashMap<Int, PendingConfirmation>()
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
        registerReceiver()
    }

    fun show(
        rule: RuleEntity,
        actions: List<ActionDef>,
        snapshot: String,
        phase: String,
    ) {
        val notificationId = NOTIFICATION_BASE_ID + rule.id.toInt()
        pendingConfirmations[notificationId] = PendingConfirmation(
            rule = rule,
            actions = actions,
            snapshot = snapshot,
            createdAt = System.currentTimeMillis(),
            phase = phase,
        )

        val summary = actions.joinToString(", ") { it.displayName }
        val confirmIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(AUTOMATION_CONFIRM_ACTION)
                .putExtra(AUTOMATION_NOTIFICATION_ID_EXTRA, notificationId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getBroadcast(
            context,
            notificationId + CANCEL_REQUEST_CODE_OFFSET,
            Intent(AUTOMATION_CANCEL_ACTION)
                .putExtra(AUTOMATION_NOTIFICATION_ID_EXTRA, notificationId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(rule.name)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .addAction(
                android.R.drawable.ic_menu_send,
                context.getString(R.string.service_confirm_action_yes),
                confirmIntent,
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.service_confirm_action_no),
                cancelIntent,
            )
            .setAutoCancel(true)
            .setTimeoutAfter(CONFIRM_TIMEOUT_MS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(notificationId, notification)
        WjxFileLogger.i(TAG, "Confirm requested: '${rule.name}' -> $summary")
    }

    fun cleanupExpired(now: Long = System.currentTimeMillis()) {
        val expired = pendingConfirmations.entries
            .filter { now - it.value.createdAt > CONFIRM_TIMEOUT_MS }
        expired.forEach { (notificationId, pending) ->
            if (!pendingConfirmations.remove(notificationId, pending)) return@forEach
            notificationManager.cancel(notificationId)
            scope.launch {
                executionCoordinator.recordConfirmationOutcome(
                    rule = pending.rule,
                    snapshot = pending.snapshot,
                    triggeredAt = pending.createdAt,
                    result = "timeout",
                )
            }
            WjxFileLogger.i(TAG, "Confirm timeout: '${pending.rule.name}'")
        }
    }

    private fun registerReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiveContext: Context, intent: Intent) {
                val notificationId = intent.getIntExtra(AUTOMATION_NOTIFICATION_ID_EXTRA, -1)
                val pending = pendingConfirmations.remove(notificationId) ?: return
                notificationManager.cancel(notificationId)
                when (intent.action) {
                    AUTOMATION_CONFIRM_ACTION -> scope.launch {
                        val currentData = vehicleStateRepository.actionSnapshot.value?.data
                            ?: vehicleStateRepository.fullSnapshot.value?.data
                        executionCoordinator.executeConfirmed(
                            rule = pending.rule,
                            actions = pending.actions,
                            snapshot = pending.snapshot,
                            data = currentData,
                            phase = pending.phase,
                        )
                    }

                    AUTOMATION_CANCEL_ACTION -> {
                        scope.launch {
                            executionCoordinator.recordConfirmationOutcome(
                                rule = pending.rule,
                                snapshot = pending.snapshot,
                                triggeredAt = pending.createdAt,
                                result = "cancelled",
                            )
                        }
                        WjxFileLogger.i(TAG, "Cancelled by user: '${pending.rule.name}'")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(AUTOMATION_CONFIRM_ACTION)
            addAction(AUTOMATION_CANCEL_ACTION)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Automation Confirmations",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Confirmation dialogs for automation rules"
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "AutomationEngine"
        private const val CHANNEL_ID = "wjx_automation_confirm"
        private const val CONFIRM_TIMEOUT_MS = 30_000L
        private const val NOTIFICATION_BASE_ID = 5000
        private const val CANCEL_REQUEST_CODE_OFFSET = 10_000
    }
}
