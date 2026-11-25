package com.app.check_inpets.background

import android.content.Context
import android.util.Log
import com.app.check_inpets.di.AppContainer
import com.app.check_inpets.domain.TurnResolver
import com.app.check_inpets.model.EscalationStatus
import com.app.check_inpets.model.TurnStatus
import com.app.check_inpets.notifications.PetCareNotificationHelper
import com.app.check_inpets.update.AppUpdateManager
import com.app.check_inpets.update.AppUpdateState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId

object TurnBackgroundCoordinator {

    private val mutex = Mutex()

    suspend fun handleTurnArrived(
        context: Context,
        slotTime: String?,
        refreshRemote: Boolean
    ) = mutex.withLock {
        val appContext = context.applicationContext
        val container = AppContainer.getInstance(appContext)
        if (refreshRemote) {
            withTimeoutOrNull(6_000L) {
                runCatching { container.repository.refresh() }
            }
        }

        val linkedId = container.preferencesDataStore.linkedCaregiverId.first() ?: return@withLock
        val notificationsEnabled = container.preferencesDataStore.notificationsEnabled.first()
        val snapshot = container.repository.snapshot.first()
        val user = snapshot.caregivers.firstOrNull { it.id == linkedId && it.isActive }
        if (snapshot.caregivers.isNotEmpty() && user == null) {
            return@withLock
        }

        val fallbackSlot = slotTime?.takeIf { it.isNotBlank() }
            ?: container.preferencesDataStore.cachedPreferredTime.first()
        val activeSlotTime = slotTime?.takeIf { it.isNotBlank() }
            ?: user?.preferredTime?.takeIf { it.isNotBlank() }
            ?: fallbackSlot
            ?: return@withLock

        val zone = TurnResolver.BRASILIA_ZONE
        val nowMillis = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val todayKey = TurnResolver.todayKey(nowMillis, zone)
        val dismissedKey = container.preferencesDataStore.dismissedSlotKey.first()

        val alreadyCared = TurnAlarmScheduler.hasCaredForSlot(snapshot.careLogs, activeSlotTime, today, zone)
        val alreadyDeclined = user != null &&
            TurnAlarmScheduler.hasDeclinedSlot(snapshot.escalations, user.id, activeSlotTime, todayKey)
        val alreadyDismissed = dismissedKey == "${todayKey}_$activeSlotTime"

        if (alreadyCared || alreadyDeclined || alreadyDismissed) {
            PetCareNotificationHelper.dismissAllTurnNotifications(appContext)
            if (user != null) {
                TurnAlarmScheduler.syncSchedule(
                    context = appContext,
                    currentUser = user,
                    logs = snapshot.careLogs,
                    escalations = snapshot.escalations,
                    nowMillis = nowMillis,
                    zoneId = zone,
                    allCaregivers = snapshot.caregivers
                )
            }
            return@withLock
        }

        val arrivedKey = "${todayKey}_$activeSlotTime"
        val lastArrived = container.preferencesDataStore.lastAutoOpenSlotKey.first()
        if (notificationsEnabled && lastArrived != arrivedKey) {
            container.preferencesDataStore.setLastAutoOpenSlotKey(arrivedKey)
            PetCareNotificationHelper.showTurnArrivedNotificationWithVibration(appContext, activeSlotTime)
        }

        if (user != null) {
            TurnAlarmScheduler.syncSchedule(
                context = appContext,
                currentUser = user,
                logs = snapshot.careLogs,
                escalations = snapshot.escalations,
                nowMillis = nowMillis,
                zoneId = zone,
                allCaregivers = snapshot.caregivers
            )
        } else {
            TurnAlarmScheduler.scheduleFromCachedPreferredTime(appContext, activeSlotTime, nowMillis, zone)
        }
    }

    suspend fun handleTenMinuteReminder(
        context: Context,
        slotTime: String?,
        escalationId: String?,
        refreshRemote: Boolean
    ) = mutex.withLock {
        val appContext = context.applicationContext
        val container = AppContainer.getInstance(appContext)
        if (refreshRemote) {
            withTimeoutOrNull(6_000L) {
                runCatching { container.repository.refresh() }
            }
        }

        val notificationsEnabled = container.preferencesDataStore.notificationsEnabled.first()
        if (!notificationsEnabled) return@withLock

        val nowMillis = System.currentTimeMillis()
        val linkedId = container.preferencesDataStore.linkedCaregiverId.first() ?: return@withLock
        val snapshot = container.repository.snapshot.first()
        val user = snapshot.caregivers.firstOrNull { it.id == linkedId && it.isActive }
        if (snapshot.caregivers.isNotEmpty() && user == null) {
            return@withLock
        }

        val zone = TurnResolver.BRASILIA_ZONE
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val todayKey = TurnResolver.todayKey(nowMillis, zone)

        if (!escalationId.isNullOrBlank()) {
            val escalation = snapshot.escalations.firstOrNull { it.id == escalationId }
            val escSlot = escalation?.slotTime ?: slotTime
            if (escSlot != null && TurnAlarmScheduler.hasCaredForSlot(snapshot.careLogs, escSlot, today, zone)) {
                PetCareNotificationHelper.dismissAllTurnNotifications(appContext)
                return@withLock
            }

            val reminderKey = "esc_$escalationId"
            if (container.preferencesDataStore.lastReminderSlotKey.first() != reminderKey) {
                container.preferencesDataStore.setLastReminderSlotKey(reminderKey)
                PetCareNotificationHelper.showTenMinuteReminderWithVibration(
                    context = appContext,
                    slotTime = escSlot,
                    escalationId = escalationId
                )
            }
            return@withLock
        }

        val activeSlotTime = slotTime?.takeIf { it.isNotBlank() }
            ?: user?.preferredTime
            ?: container.preferencesDataStore.cachedPreferredTime.first()
            ?: return@withLock

        val alreadyCared = TurnAlarmScheduler.hasCaredForSlot(snapshot.careLogs, activeSlotTime, today, zone)

        if (alreadyCared) {
            PetCareNotificationHelper.dismissAllTurnNotifications(appContext)
            if (user != null) {
                TurnAlarmScheduler.syncSchedule(
                    context = appContext,
                    currentUser = user,
                    logs = snapshot.careLogs,
                    escalations = snapshot.escalations,
                    nowMillis = nowMillis,
                    zoneId = zone,
                    allCaregivers = snapshot.caregivers
                )
            }
            return@withLock
        }

        val reminderKey = "${todayKey}_$activeSlotTime"
        if (container.preferencesDataStore.lastReminderSlotKey.first() != reminderKey) {
            container.preferencesDataStore.setLastReminderSlotKey(reminderKey)
            PetCareNotificationHelper.showTenMinuteReminderWithVibration(appContext, activeSlotTime)
        }

        if (user != null) {
            TurnAlarmScheduler.syncSchedule(
                context = appContext,
                currentUser = user,
                logs = snapshot.careLogs,
                escalations = snapshot.escalations,
                nowMillis = nowMillis,
                zoneId = zone,
                allCaregivers = snapshot.caregivers
            )
        } else {
            TurnAlarmScheduler.scheduleFromCachedPreferredTime(appContext, activeSlotTime, nowMillis, zone)
        }
    }

    suspend fun handleSnoozeReminder(
        context: Context,
        slotTime: String?,
        escalationId: String?,
        refreshRemote: Boolean
    ) = mutex.withLock {
        val appContext = context.applicationContext
        val container = AppContainer.getInstance(appContext)
        container.preferencesDataStore.setSnoozeUntilMillis(null)

        if (refreshRemote) {
            withTimeoutOrNull(6_000L) {
                runCatching { container.repository.refresh() }
            }
        }

        val notificationsEnabled = container.preferencesDataStore.notificationsEnabled.first()
        if (!notificationsEnabled) return@withLock

        val linkedId = container.preferencesDataStore.linkedCaregiverId.first() ?: return@withLock
        val snapshot = container.repository.snapshot.first()
        val user = snapshot.caregivers.firstOrNull { it.id == linkedId && it.isActive }
        if (snapshot.caregivers.isNotEmpty() && user == null) {
            return@withLock
        }

        val zone = TurnResolver.BRASILIA_ZONE
        val nowMillis = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

        if (!escalationId.isNullOrBlank()) {
            val escalation = snapshot.escalations.firstOrNull { it.id == escalationId }
            val escSlot = escalation?.slotTime ?: slotTime
            if (escSlot != null && TurnAlarmScheduler.hasCaredForSlot(snapshot.careLogs, escSlot, today, zone)) {
                PetCareNotificationHelper.dismissAllTurnNotifications(appContext)
                return@withLock
            }
            PetCareNotificationHelper.showSnoozeReminderWithVibration(
                context = appContext,
                slotTime = escSlot,
                escalationId = escalationId
            )
            return@withLock
        }

        val activeSlotTime = slotTime?.takeIf { it.isNotBlank() }
            ?: user?.preferredTime
            ?: container.preferencesDataStore.cachedPreferredTime.first()
        if (activeSlotTime != null) {
            val alreadyCared = TurnAlarmScheduler.hasCaredForSlot(snapshot.careLogs, activeSlotTime, today, zone)
            if (alreadyCared) {
                PetCareNotificationHelper.dismissAllTurnNotifications(appContext)
                return@withLock
            }
        }

        PetCareNotificationHelper.showSnoozeReminderWithVibration(appContext, activeSlotTime)
    }

    suspend fun handleAppLeftWithoutAnswering(context: Context) = mutex.withLock {
        if (AppForegroundTracker.isForeground) {
            Log.d("CheckinPetsBg", "handleAppLeftWithoutAnswering skipped: isForeground=true")
            return@withLock
        }

        val appContext = context.applicationContext
        val container = AppContainer.getInstance(appContext)
        val notificationsEnabled = container.preferencesDataStore.notificationsEnabled.first()
        if (!notificationsEnabled) return@withLock

        val linkedId = container.preferencesDataStore.linkedCaregiverId.first() ?: return@withLock
        val snapshot = container.repository.snapshot.first()
        val user = snapshot.caregivers.firstOrNull { it.id == linkedId && it.isActive } ?: return@withLock

        val nowMillis = System.currentTimeMillis()

        val turnStatus = TurnResolver.resolveTurnStatus(
            currentUser = user,
            allCaregivers = snapshot.caregivers,
            logs = snapshot.careLogs,
            todayEscalations = snapshot.escalations,
            nowMillis = nowMillis
        )

        val pendingInfo: Pair<String, String?>? = when (turnStatus) {
            is TurnStatus.MyScheduledTurn -> turnStatus.slotTime to null
            is TurnStatus.EscalatedToMe -> turnStatus.escalation.slotTime to turnStatus.escalation.id
            is TurnStatus.NotMyTurn -> {
                val pendingSlot = turnStatus.pendingSlotTime
                if (turnStatus.hasUncaredPendingTurn && pendingSlot != null) {
                    pendingSlot to null
                } else {
                    null
                }
            }
        }

        if (pendingInfo != null) {
            PetCareNotificationHelper.showExitWithoutMarkingNotification(
                context = appContext,
                slotTime = pendingInfo.first,
                escalationId = pendingInfo.second
            )
        }
    }

    suspend fun syncPendingAlertInBackground(
        context: Context,
        slotTime: String?,
        escalationId: String?
    ) = mutex.withLock {
        val appContext = context.applicationContext
        val container = AppContainer.getInstance(appContext)
        withTimeoutOrNull(8_000L) {
            runCatching { container.repository.refresh() }
        }

        val linkedId = container.preferencesDataStore.linkedCaregiverId.first()
        if (linkedId == null) {
            PetCareNotificationHelper.dismissAllTurnNotifications(appContext)
            return@withLock
        }

        val snapshot = container.repository.snapshot.first()
        val user = snapshot.caregivers.firstOrNull { it.id == linkedId && it.isActive }
        val zone = TurnResolver.BRASILIA_ZONE
        val nowMillis = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val todayKey = TurnResolver.todayKey(nowMillis, zone)
        val dismissedKey = container.preferencesDataStore.dismissedSlotKey.first()

        val stillPending = isAlertStillPending(
            user = user,
            slotTime = slotTime,
            escalationId = escalationId,
            snapshot = snapshot,
            today = today,
            todayKey = todayKey,
            dismissedKey = dismissedKey,
            nowMillis = nowMillis,
            zone = zone
        )

        if (!stillPending) {
            Log.d("CheckinPetsBg", "syncPendingAlertInBackground: cared remotely, dismissing notification")
            PetCareNotificationHelper.dismissAllTurnNotifications(appContext)
            if (user != null) {
                TurnAlarmScheduler.syncSchedule(
                    context = appContext,
                    currentUser = user,
                    logs = snapshot.careLogs,
                    escalations = snapshot.escalations,
                    nowMillis = nowMillis,
                    zoneId = zone,
                    allCaregivers = snapshot.caregivers
                )
            }
        } else {
            Log.d("CheckinPetsBg", "syncPendingAlertInBackground: still pending, keeping notification & rescheduling sync")
            if (user != null) {
                val turnStatus = TurnResolver.resolveTurnStatus(
                    currentUser = user,
                    allCaregivers = snapshot.caregivers,
                    logs = snapshot.careLogs,
                    todayEscalations = snapshot.escalations,
                    nowMillis = nowMillis,
                    zoneId = zone
                )
                if (turnStatus is TurnStatus.EscalatedToMe) {
                    val notificationsEnabled = container.preferencesDataStore.notificationsEnabled.first()
                    val lastEscId = container.preferencesDataStore.lastEscalationNotifiedId.first()
                    if (notificationsEnabled && lastEscId != turnStatus.escalation.id) {
                        container.preferencesDataStore.setLastEscalationNotifiedId(turnStatus.escalation.id)
                        PetCareNotificationHelper.showEscalatedToMeNotification(
                            context = appContext,
                            slotTime = turnStatus.escalation.slotTime,
                            escalationId = turnStatus.escalation.id
                        )
                    }
                }
            }
            TurnAlarmScheduler.schedulePendingAlertSync(appContext, slotTime, escalationId)
        }
    }

    suspend fun handleNotificationRepost(
        context: Context,
        slotTime: String?,
        escalationId: String?
    ) = mutex.withLock {
        val appContext = context.applicationContext
        val container = AppContainer.getInstance(appContext)
        val linkedId = container.preferencesDataStore.linkedCaregiverId.first() ?: return@withLock
        val snapshot = container.repository.snapshot.first()
        val user = snapshot.caregivers.firstOrNull { it.id == linkedId && it.isActive }
        val zone = TurnResolver.BRASILIA_ZONE
        val nowMillis = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val todayKey = TurnResolver.todayKey(nowMillis, zone)
        val dismissedKey = container.preferencesDataStore.dismissedSlotKey.first()

        if (isAlertStillPending(user, slotTime, escalationId, snapshot, today, todayKey, dismissedKey, nowMillis, zone)) {
            PetCareNotificationHelper.repostOngoingNotificationSilently(appContext, slotTime, escalationId)
        }
    }

    private fun isAlertStillPending(
        user: com.app.check_inpets.model.Caregiver?,
        slotTime: String?,
        escalationId: String?,
        snapshot: com.app.check_inpets.data.repository.PetCareSnapshot,
        today: java.time.LocalDate,
        todayKey: String,
        dismissedKey: String?,
        nowMillis: Long,
        zone: ZoneId
    ): Boolean {
        val activeSlot = slotTime?.takeIf { it.isNotBlank() }
            ?: escalationId?.let { id -> snapshot.escalations.firstOrNull { it.id == id }?.slotTime }
        if (activeSlot != null) {
            if (TurnAlarmScheduler.hasCaredForSlot(snapshot.careLogs, activeSlot, today, zone)) return false
            if (user != null && TurnAlarmScheduler.hasDeclinedSlot(snapshot.escalations, user.id, activeSlot, todayKey)) return false
            if (dismissedKey == "${todayKey}_$activeSlot") return false
            return true
        }
        if (user != null) {
            val turnStatus = TurnResolver.resolveTurnStatus(
                currentUser = user,
                allCaregivers = snapshot.caregivers,
                logs = snapshot.careLogs,
                todayEscalations = snapshot.escalations,
                nowMillis = nowMillis,
                zoneId = zone
            )
            return turnStatus !is TurnStatus.NotMyTurn || turnStatus.hasUncaredPendingTurn
        }
        return true
    }

    suspend fun scheduleSnoozeFromNotification(
        context: Context,
        slotTime: String?,
        escalationId: String?
    ) = mutex.withLock {
        val appContext = context.applicationContext
        val container = AppContainer.getInstance(appContext)
        PetCareNotificationHelper.dismissAllTurnNotifications(appContext)

        val linkedId = container.preferencesDataStore.linkedCaregiverId.first() ?: return@withLock
        val snapshot = container.repository.snapshot.first()
        val user = snapshot.caregivers.firstOrNull { it.id == linkedId && it.isActive } ?: return@withLock
        val activeSlotTime = slotTime?.takeIf { it.isNotBlank() } ?: user.preferredTime

        val remindAtMillis = System.currentTimeMillis() + TurnAlarmScheduler.FIVE_MINUTES_MILLIS
        container.preferencesDataStore.setSnoozeUntilMillis(remindAtMillis)
        TurnAlarmScheduler.scheduleSnoozeReminder(
            context = appContext,
            triggerAtMillis = remindAtMillis,
            slotTime = activeSlotTime,
            escalationId = escalationId
        )
    }

    suspend fun handleDailyUpdateCheck(context: Context) {
        val appContext = context.applicationContext
        TurnAlarmScheduler.scheduleDailyNoonUpdateCheck(appContext)
        val result = AppUpdateManager.checkForUpdate(appContext)
        if (result is AppUpdateState.UpdateAvailable) {
            if (AppForegroundTracker.isForeground) {
                AppUpdateManager.notifyForegroundUpdateAvailable(result)
            } else {
                PetCareNotificationHelper.showAppUpdateAvailableNotification(
                    context = appContext,
                    currentVersionName = result.current.versionName,
                    newVersionName = result.remote.versionName
                )
            }
        }
    }
}
