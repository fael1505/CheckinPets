package com.app.check_inpets.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TurnAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val slotTime = intent.getStringExtra(TurnAlarmScheduler.EXTRA_SLOT_TIME)
        val escalationId = intent.getStringExtra(TurnAlarmScheduler.EXTRA_ESCALATION_ID)

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    TurnAlarmScheduler.ACTION_TURN_ARRIVED -> {
                        TurnBackgroundCoordinator.handleTurnArrived(
                            context = appContext,
                            slotTime = slotTime,
                            refreshRemote = true
                        )
                    }

                    TurnAlarmScheduler.ACTION_REMINDER_10M -> {
                        TurnBackgroundCoordinator.handleTenMinuteReminder(
                            context = appContext,
                            slotTime = slotTime,
                            escalationId = escalationId,
                            refreshRemote = true
                        )
                    }

                    TurnAlarmScheduler.ACTION_SNOOZE_REMINDER -> {
                        TurnBackgroundCoordinator.handleSnoozeReminder(
                            context = appContext,
                            slotTime = slotTime,
                            escalationId = escalationId,
                            refreshRemote = true
                        )
                    }

                    TurnAlarmScheduler.ACTION_SNOOZE_CLICKED -> {
                        TurnBackgroundCoordinator.scheduleSnoozeFromNotification(
                            context = appContext,
                            slotTime = slotTime,
                            escalationId = escalationId
                        )
                    }

                    TurnAlarmScheduler.ACTION_NOTIFICATION_REPOST -> {
                        TurnBackgroundCoordinator.handleNotificationRepost(
                            context = appContext,
                            slotTime = slotTime,
                            escalationId = escalationId
                        )
                    }

                    TurnAlarmScheduler.ACTION_SYNC_PENDING_ALERT -> {
                        TurnBackgroundCoordinator.syncPendingAlertInBackground(
                            context = appContext,
                            slotTime = slotTime,
                            escalationId = escalationId
                        )
                    }

                    TurnAlarmScheduler.ACTION_CHECK_APP_UPDATE -> {
                        TurnBackgroundCoordinator.handleDailyUpdateCheck(appContext)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
