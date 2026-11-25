package com.app.check_inpets.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.app.check_inpets.domain.TurnResolver
import com.app.check_inpets.model.CareLog
import com.app.check_inpets.model.Caregiver
import com.app.check_inpets.model.EscalationStatus
import com.app.check_inpets.model.TurnEscalation
import com.app.check_inpets.notifications.PetCareNotificationHelper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object TurnAlarmScheduler {

    const val ACTION_TURN_ARRIVED = "com.app.check_inpets.ACTION_TURN_ARRIVED"
    const val ACTION_REMINDER_10M = "com.app.check_inpets.ACTION_REMINDER_10M"
    const val ACTION_SNOOZE_REMINDER = "com.app.check_inpets.ACTION_SNOOZE_REMINDER"
    const val ACTION_SNOOZE_CLICKED = "com.app.check_inpets.ACTION_SNOOZE_CLICKED"
    const val ACTION_NOTIFICATION_REPOST = "com.app.check_inpets.ACTION_NOTIFICATION_REPOST"
    const val ACTION_SYNC_PENDING_ALERT = "com.app.check_inpets.ACTION_SYNC_PENDING_ALERT"
    const val ACTION_CHECK_APP_UPDATE = "com.app.check_inpets.ACTION_CHECK_APP_UPDATE"
    const val EXTRA_SLOT_TIME = "extra_slot_time"
    const val EXTRA_ESCALATION_ID = "extra_escalation_id"

    private const val REQUEST_CODE_ARRIVED = 3001
    private const val REQUEST_CODE_REMINDER = 3002
    private const val REQUEST_CODE_ESC_REMINDER = 3003
    private const val REQUEST_CODE_SNOOZE = 3004
    private const val REQUEST_CODE_SYNC_ALERT = 3006
    private const val REQUEST_CODE_UPDATE_12H = 3010

    const val ONE_MINUTE_MILLIS = 60_000L
    const val FIVE_MINUTES_MILLIS = 5 * 60_000L
    const val TEN_MINUTES_MILLIS = 10 * 60_000L

    fun syncSchedule(
        context: Context,
        currentUser: Caregiver?,
        logs: List<CareLog>,
        escalations: List<TurnEscalation>,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = TurnResolver.BRASILIA_ZONE,
        allCaregivers: List<Caregiver> = emptyList(),
        dismissedSlotKey: String? = null
    ) {
        if (currentUser == null || !currentUser.isActive) {
            cancelAll(context)
            return
        }

        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val todayKey = TurnResolver.todayKey(nowMillis, zoneId)

        // Collect all distinct valid scheduled times across ALL active caregivers so every device is alerted
        val allSlotTimes = buildList {
            allCaregivers.filter { it.isActive }.forEach { cg ->
                cg.preferredTime?.trim()?.takeIf { TurnResolver.parseTimeOrNull(it) != null }?.let { add(it) }
            }
            currentUser.preferredTime?.trim()?.takeIf { TurnResolver.parseTimeOrNull(it) != null }?.let { add(it) }
        }.distinct()

        data class ScheduledCandidate(
            val slotTime: String,
            val slotMillis: Long,
            val reminderMillis: Long
        )

        val arrivedCandidates = mutableListOf<ScheduledCandidate>()
        val reminderCandidates = mutableListOf<ScheduledCandidate>()

        for (slotTime in allSlotTimes) {
            val parsed = TurnResolver.parseTimeOrNull(slotTime) ?: continue
            val slotZonedToday = ZonedDateTime.of(today, parsed, zoneId)
            val slotMillisToday = slotZonedToday.toInstant().toEpochMilli()
            val reminderMillisToday = slotMillisToday + TEN_MINUTES_MILLIS

            val alreadyHandledToday = hasCaredForSlot(logs, slotTime, today, zoneId) ||
                hasDeclinedSlot(escalations, currentUser.id, slotTime, todayKey) ||
                dismissedSlotKey == "${todayKey}_$slotTime"

            val nextSlotMillis: Long
            val nextReminderMillis: Long

            if (alreadyHandledToday || nowMillis >= reminderMillisToday) {
                val tomorrowSlot = slotZonedToday.plusDays(1).toInstant().toEpochMilli()
                nextSlotMillis = tomorrowSlot
                nextReminderMillis = tomorrowSlot + TEN_MINUTES_MILLIS
            } else if (nowMillis < slotMillisToday) {
                nextSlotMillis = slotMillisToday
                nextReminderMillis = reminderMillisToday
            } else {
                // Slot already arrived today (< 10 min ago) and not handled yet:
                // Next arrival is tomorrow, but today's 10m reminder is still ahead.
                nextSlotMillis = slotZonedToday.plusDays(1).toInstant().toEpochMilli()
                nextReminderMillis = reminderMillisToday
            }

            arrivedCandidates.add(ScheduledCandidate(slotTime, nextSlotMillis, nextReminderMillis))
            reminderCandidates.add(ScheduledCandidate(slotTime, nextSlotMillis, nextReminderMillis))
        }

        val nextArrived = arrivedCandidates.minByOrNull { it.slotMillis }
        val nextReminder = reminderCandidates.minByOrNull { it.reminderMillis }

        if (nextArrived != null) {
            scheduleExact(
                context = context,
                requestCode = REQUEST_CODE_ARRIVED,
                action = ACTION_TURN_ARRIVED,
                triggerAtMillis = nextArrived.slotMillis,
                slotTime = nextArrived.slotTime,
                escalationId = null
            )
        } else {
            cancelAlarm(context, REQUEST_CODE_ARRIVED, ACTION_TURN_ARRIVED)
        }

        if (nextReminder != null) {
            scheduleExact(
                context = context,
                requestCode = REQUEST_CODE_REMINDER,
                action = ACTION_REMINDER_10M,
                triggerAtMillis = nextReminder.reminderMillis,
                slotTime = nextReminder.slotTime,
                escalationId = null
            )
        } else {
            cancelAlarm(context, REQUEST_CODE_REMINDER, ACTION_REMINDER_10M)
        }

        val pendingEsc = escalations.firstOrNull {
            it.dateKey == todayKey &&
                it.status == EscalationStatus.PENDING &&
                it.targetCaregiverId == currentUser.id
        }
        if (pendingEsc != null) {
            val escReminderAt = pendingEsc.createdAtEpochMillis + TEN_MINUTES_MILLIS
            if (escReminderAt > nowMillis) {
                scheduleExact(
                    context = context,
                    requestCode = REQUEST_CODE_ESC_REMINDER,
                    action = ACTION_REMINDER_10M,
                    triggerAtMillis = escReminderAt,
                    slotTime = pendingEsc.slotTime,
                    escalationId = pendingEsc.id
                )
            } else {
                cancelAlarm(context, REQUEST_CODE_ESC_REMINDER, ACTION_REMINDER_10M)
            }
        } else {
            cancelAlarm(context, REQUEST_CODE_ESC_REMINDER, ACTION_REMINDER_10M)
        }
    }

    fun scheduleSnoozeReminder(
        context: Context,
        triggerAtMillis: Long,
        slotTime: String?,
        escalationId: String?
    ) {
        scheduleExact(
            context = context,
            requestCode = REQUEST_CODE_SNOOZE,
            action = ACTION_SNOOZE_REMINDER,
            triggerAtMillis = triggerAtMillis,
            slotTime = slotTime.orEmpty(),
            escalationId = escalationId
        )
    }

    fun cancelSnoozeReminder(context: Context) {
        cancelAlarm(context, REQUEST_CODE_SNOOZE, ACTION_SNOOZE_REMINDER)
    }

    fun schedulePendingAlertSync(
        context: Context,
        slotTime: String?,
        escalationId: String?,
        delayMillis: Long = ONE_MINUTE_MILLIS
    ) {
        scheduleExact(
            context = context,
            requestCode = REQUEST_CODE_SYNC_ALERT,
            action = ACTION_SYNC_PENDING_ALERT,
            triggerAtMillis = System.currentTimeMillis() + delayMillis,
            slotTime = slotTime.orEmpty(),
            escalationId = escalationId
        )
    }

    fun cancelPendingAlertSync(context: Context) {
        cancelAlarm(context, REQUEST_CODE_SYNC_ALERT, ACTION_SYNC_PENDING_ALERT)
    }

    fun scheduleDailyNoonUpdateCheck(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val zone = TurnResolver.BRASILIA_ZONE
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val noonToday = ZonedDateTime.of(today, java.time.LocalTime.of(12, 0), zone)
        val noonMillis = noonToday.toInstant().toEpochMilli()
        val triggerAt = if (nowMillis < noonMillis) {
            noonMillis
        } else {
            noonToday.plusDays(1).toInstant().toEpochMilli()
        }
        scheduleExact(
            context = context,
            requestCode = REQUEST_CODE_UPDATE_12H,
            action = ACTION_CHECK_APP_UPDATE,
            triggerAtMillis = triggerAt,
            slotTime = "",
            escalationId = null
        )
    }

    fun scheduleFromCachedPreferredTime(
        context: Context,
        preferredTime: String?,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = TurnResolver.BRASILIA_ZONE
    ) {
        val parsed = TurnResolver.parseTimeOrNull(preferredTime) ?: return
        val slotTime = preferredTime?.trim() ?: return
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val slotToday = ZonedDateTime.of(today, parsed, zoneId)
        val slotMillis = slotToday.toInstant().toEpochMilli()
        val reminderMillis = slotMillis + TEN_MINUTES_MILLIS

        val targetSlot = if (nowMillis < slotMillis) slotMillis else slotToday.plusDays(1).toInstant().toEpochMilli()
        val targetReminder = if (nowMillis < reminderMillis) reminderMillis else slotToday.plusDays(1).toInstant().toEpochMilli() + TEN_MINUTES_MILLIS

        scheduleExact(context, REQUEST_CODE_ARRIVED, ACTION_TURN_ARRIVED, targetSlot, slotTime, null)
        scheduleExact(context, REQUEST_CODE_REMINDER, ACTION_REMINDER_10M, targetReminder, slotTime, null)
    }

    fun cancelAll(context: Context) {
        cancelAlarm(context, REQUEST_CODE_ARRIVED, ACTION_TURN_ARRIVED)
        cancelAlarm(context, REQUEST_CODE_REMINDER, ACTION_REMINDER_10M)
        cancelAlarm(context, REQUEST_CODE_ESC_REMINDER, ACTION_REMINDER_10M)
        cancelAlarm(context, REQUEST_CODE_SNOOZE, ACTION_SNOOZE_REMINDER)
        cancelAlarm(context, REQUEST_CODE_SYNC_ALERT, ACTION_SYNC_PENDING_ALERT)
    }

    fun hasCaredForSlot(
        logs: List<CareLog>,
        slotTime: String,
        date: LocalDate,
        zoneId: ZoneId = TurnResolver.BRASILIA_ZONE
    ): Boolean {
        val preferred = TurnResolver.parseTimeOrNull(slotTime) ?: return false
        val slotStartMillis = ZonedDateTime.of(date, preferred, zoneId).toInstant().toEpochMilli()
        return logs.any { it.caredAtEpochMillis >= slotStartMillis }
    }

    fun hasDeclinedSlot(
        escalations: List<TurnEscalation>,
        caregiverId: String,
        slotTime: String,
        todayKey: String
    ): Boolean {
        return escalations.any {
            it.dateKey == todayKey &&
                it.originalCaregiverId == caregiverId &&
                it.slotTime == slotTime
        }
    }

    private fun scheduleExact(
        context: Context,
        requestCode: Int,
        action: String,
        triggerAtMillis: Long,
        slotTime: String,
        escalationId: String?
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TurnAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_SLOT_TIME, slotTime)
            if (escalationId != null) {
                putExtra(EXTRA_ESCALATION_ID, escalationId)
            }
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        runCatching {
            if (canExact && action == ACTION_TURN_ARRIVED) {
                val showIntent = PetCareNotificationHelper.createOpenAppPendingIntent(
                    context = context,
                    requestCode = requestCode + 100,
                    openHomeFromAlert = true,
                    slotTime = slotTime
                )
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                    pendingIntent
                )
            } else if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        }
    }

    private fun cancelAlarm(context: Context, requestCode: Int, action: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TurnAlarmReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
