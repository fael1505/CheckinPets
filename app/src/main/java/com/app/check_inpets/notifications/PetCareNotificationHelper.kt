package com.app.check_inpets.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.app.check_inpets.MainActivity
import com.app.check_inpets.R
import com.app.check_inpets.background.TurnAlarmReceiver
import com.app.check_inpets.background.TurnAlarmScheduler
import com.app.check_inpets.ui.components.TimeFormatting

object PetCareNotificationHelper {

    const val CHANNEL_ID = "pet_care_turns"
    const val CHANNEL_REMINDER_ID = "pet_care_reminders"
    private const val LEGACY_CHANNEL_BACKGROUND_ID = "pet_care_background"

    const val REMINDER_NOTIFICATION_ID = 2001
    const val TURN_ARRIVED_NOTIFICATION_ID = 2002
    const val UPDATE_NOTIFICATION_ID = 2003

    const val EXTRA_OPEN_HOME_FROM_ALERT = "extra_open_home_from_alert"
    const val EXTRA_SLOT_TIME = "extra_slot_time"
    const val EXTRA_OPEN_UPDATE_DIALOG = "extra_open_update_dialog"

    private val VIBRATION_PATTERN = longArrayOf(0L, 450L, 200L, 450L)

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (manager.getNotificationChannel(LEGACY_CHANNEL_BACKGROUND_ID) != null) {
            manager.deleteNotificationChannel(LEGACY_CHANNEL_BACKGROUND_ID)
        }

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Turnos de Cuidado",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas quando chega a sua vez ou outro cuidador passa o turno."
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
            }
            manager.createNotificationChannel(channel)
        }

        if (manager.getNotificationChannel(CHANNEL_REMINDER_ID) == null) {
            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDER_ID,
                "Lembretes de Cuidado",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificação com vibração para lembrar de cuidar dos cachorros."
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
            }
            manager.createNotificationChannel(reminderChannel)
        }
    }

    fun ensureChannel(context: Context) {
        ensureChannels(context)
    }

    @SuppressLint("MissingPermission")
    fun showTurnEscalationNotification(
        context: Context,
        title: String,
        body: String
    ) {
        ensureChannels(context)
        if (!hasPostNotificationPermission(context)) return

        val pendingIntent = createOpenAppPendingIntent(
            context = context,
            requestCode = 1001,
            openHomeFromAlert = false,
            slotTime = null
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(
            (System.currentTimeMillis() and 0x7FFFFFFF).toInt(),
            notification
        )
    }

    @SuppressLint("MissingPermission")
    fun showTenMinuteReminderWithVibration(
        context: Context,
        slotTime: String?,
        escalationId: String? = null
    ) {
        postSingleReminderNotification(
            context = context,
            slotTime = slotTime,
            escalationId = escalationId,
            onlyAlertOnce = true
        )
    }

    @SuppressLint("MissingPermission")
    fun showExitWithoutMarkingNotification(
        context: Context,
        slotTime: String?,
        escalationId: String? = null
    ) {
        postSingleReminderNotification(
            context = context,
            slotTime = slotTime,
            escalationId = escalationId,
            onlyAlertOnce = false
        )
    }

    @SuppressLint("MissingPermission")
    fun showSnoozeReminderWithVibration(
        context: Context,
        slotTime: String?,
        escalationId: String? = null
    ) {
        postSingleReminderNotification(
            context = context,
            slotTime = slotTime,
            escalationId = escalationId,
            onlyAlertOnce = false
        )
    }

    @SuppressLint("MissingPermission")
    fun showTurnArrivedNotificationWithVibration(
        context: Context,
        slotTime: String?
    ) {
        postSingleReminderNotification(
            context = context,
            slotTime = slotTime,
            escalationId = null,
            onlyAlertOnce = false
        )
    }

    @SuppressLint("MissingPermission")
    private fun postSingleReminderNotification(
        context: Context,
        slotTime: String?,
        escalationId: String?,
        onlyAlertOnce: Boolean,
        vibrate: Boolean = true
    ) {
        ensureChannels(context)
        if (vibrate) {
            vibratePhone(context)
        }

        val hasPerm = hasPostNotificationPermission(context)
        Log.d("CheckinPetsBg", "postSingleReminderNotification: hasPerm=$hasPerm")
        if (!hasPerm) return

        val pendingIntent = createOpenAppPendingIntent(
            context = context,
            requestCode = REMINDER_NOTIFICATION_ID,
            openHomeFromAlert = true,
            slotTime = slotTime
        )
        val caredPendingIntent = createNotificationActionPendingIntent(
            context = context,
            requestCode = 3007,
            action = TurnAlarmScheduler.ACTION_NOTIFICATION_CARED,
            slotTime = slotTime,
            escalationId = escalationId
        )
        val declinePendingIntent = createNotificationActionPendingIntent(
            context = context,
            requestCode = 3008,
            action = TurnAlarmScheduler.ACTION_NOTIFICATION_DECLINE,
            slotTime = slotTime,
            escalationId = escalationId
        )
        val deletePendingIntent = createNotificationActionPendingIntent(
            context = context,
            requestCode = 3009,
            action = TurnAlarmScheduler.ACTION_NOTIFICATION_REPOST,
            slotTime = slotTime,
            escalationId = escalationId
        )

        val contentText = if (!slotTime.isNullOrBlank()) {
            val formattedSlot = TimeFormatting.formatBrasiliaSlotForDevice(slotTime)
            "Horário das $formattedSlot — responda abaixo ou no app para fechar este aviso."
        } else {
            "Responda abaixo ou no app para fechar este aviso."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Já cuidou dos cachorros?")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVibrate(if (vibrate) VIBRATION_PATTERN else longArrayOf(0L))
            .setOnlyAlertOnce(onlyAlertOnce)
            .setOngoing(true)
            .setAutoCancel(false)
            .setDeleteIntent(deletePendingIntent)
            .setContentIntent(pendingIntent)
            .addAction(0, "Cuidei", caredPendingIntent)
            .addAction(0, "Não irei cuidar", declinePendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(REMINDER_NOTIFICATION_ID, notification)
        TurnAlarmScheduler.schedulePendingAlertSync(context, slotTime, escalationId)
    }

    @SuppressLint("MissingPermission")
    fun repostOngoingNotificationSilently(
        context: Context,
        slotTime: String?,
        escalationId: String? = null
    ) {
        postSingleReminderNotification(
            context = context,
            slotTime = slotTime,
            escalationId = escalationId,
            onlyAlertOnce = true,
            vibrate = false
        )
    }

    private fun createNotificationActionPendingIntent(
        context: Context,
        requestCode: Int,
        action: String,
        slotTime: String?,
        escalationId: String?
    ): PendingIntent {
        val intent = Intent(context, TurnAlarmReceiver::class.java).apply {
            this.action = action
            if (!slotTime.isNullOrBlank()) {
                putExtra(TurnAlarmScheduler.EXTRA_SLOT_TIME, slotTime)
            }
            if (!escalationId.isNullOrBlank()) {
                putExtra(TurnAlarmScheduler.EXTRA_ESCALATION_ID, escalationId)
            }
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun dismissAllTurnNotifications(context: Context) {
        cancelReminderNotification(context)
        cancelTurnArrivedNotification(context)
        TurnAlarmScheduler.cancelPendingAlertSync(context)
    }

    @SuppressLint("MissingPermission")
    fun showAppUpdateAvailableNotification(
        context: Context,
        currentVersionName: String,
        newVersionName: String
    ) {
        ensureChannels(context)
        if (!hasPostNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_UPDATE_DIALOG, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            UPDATE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = "Nova versão v$newVersionName disponível (atual: v$currentVersionName). Toque para atualizar."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Atualização do Check-in Pets")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(UPDATE_NOTIFICATION_ID, notification)
    }

    fun cancelReminderNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(REMINDER_NOTIFICATION_ID)
    }

    fun cancelTurnArrivedNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(TURN_ARRIVED_NOTIFICATION_ID)
    }

    fun vibratePhone(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, -1))
            }
        }
    }

    fun createOpenAppPendingIntent(
        context: Context,
        requestCode: Int,
        openHomeFromAlert: Boolean,
        slotTime: String?
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_HOME_FROM_ALERT, openHomeFromAlert)
            if (!slotTime.isNullOrBlank()) {
                putExtra(EXTRA_SLOT_TIME, slotTime)
            }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            val options = ActivityOptions.makeBasic().apply {
                pendingIntentCreatorBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }
            PendingIntent.getActivity(context, requestCode, intent, flags, options.toBundle())
        } else {
            PendingIntent.getActivity(context, requestCode, intent, flags)
        }
    }

    private fun hasPostNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }
}
