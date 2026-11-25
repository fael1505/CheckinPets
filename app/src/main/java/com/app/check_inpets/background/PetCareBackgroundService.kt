package com.app.check_inpets.background

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.app.check_inpets.notifications.PetCareNotificationHelper

/**
 * Serviço em primeiro plano que mantém a notificação "Hora de cuidar dos cachorros"
 * visível e não-fechável até que alguém marque que cuidou ou o usuário responda.
 */
class PetCareBackgroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val slotTime = intent?.getStringExtra(TurnAlarmScheduler.EXTRA_SLOT_TIME)
        val escalationId = intent?.getStringExtra(TurnAlarmScheduler.EXTRA_ESCALATION_ID)

        PetCareNotificationHelper.ensureChannels(this)
        val notification = PetCareNotificationHelper.buildOngoingReminderNotification(
            context = this,
            slotTime = slotTime,
            escalationId = escalationId,
            onlyAlertOnce = true,
            vibrate = false
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                PetCareNotificationHelper.REMINDER_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(PetCareNotificationHelper.REMINDER_NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    companion object {
        private const val ACTION_START = "com.app.check_inpets.ACTION_FGS_START"
        private const val ACTION_STOP = "com.app.check_inpets.ACTION_FGS_STOP"

        fun start(context: Context, slotTime: String?, escalationId: String?) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, PetCareBackgroundService::class.java).apply {
                action = ACTION_START
                if (!slotTime.isNullOrBlank()) {
                    putExtra(TurnAlarmScheduler.EXTRA_SLOT_TIME, slotTime)
                }
                if (!escalationId.isNullOrBlank()) {
                    putExtra(TurnAlarmScheduler.EXTRA_ESCALATION_ID, escalationId)
                }
            }
            runCatching {
                ContextCompat.startForegroundService(appContext, intent)
            }
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, PetCareBackgroundService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching {
                appContext.stopService(intent)
            }
        }
    }
}
