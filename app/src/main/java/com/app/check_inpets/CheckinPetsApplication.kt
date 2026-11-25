package com.app.check_inpets

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.app.check_inpets.background.AppForegroundTracker
import com.app.check_inpets.background.PetCareBackgroundService
import com.app.check_inpets.background.TurnAlarmScheduler
import com.app.check_inpets.background.TurnBackgroundCoordinator
import com.app.check_inpets.di.AppContainer
import com.app.check_inpets.notifications.PetCareNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CheckinPetsApplication : Application(), Application.ActivityLifecycleCallbacks {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()
        PetCareNotificationHelper.ensureChannels(this)
        PetCareBackgroundService.stop(this)
        TurnAlarmScheduler.scheduleDailyNoonUpdateCheck(this)
        registerActivityLifecycleCallbacks(this)
        observeScheduleUpdates()
    }

    private fun observeScheduleUpdates() {
        val container = AppContainer.getInstance(this)
        appScope.launch {
            combine(
                container.repository.snapshot,
                container.repository.linkedCaregiverId
            ) { snapshot, linkedId ->
                snapshot to linkedId
            }.collect { (snapshot, linkedId) ->
                if (linkedId == null) {
                    TurnAlarmScheduler.cancelAll(applicationContext)
                    PetCareNotificationHelper.dismissAllTurnNotifications(applicationContext)
                    return@collect
                }
                val user = snapshot.caregivers.firstOrNull { it.id == linkedId && it.isActive }
                if (user != null) {
                    container.preferencesDataStore.setCachedPreferredTime(user.preferredTime)
                    TurnAlarmScheduler.syncSchedule(
                        context = applicationContext,
                        currentUser = user,
                        logs = snapshot.careLogs,
                        escalations = snapshot.escalations,
                        allCaregivers = snapshot.caregivers
                    )

                    val nowMillis = System.currentTimeMillis()
                    val turnStatus = com.app.check_inpets.domain.TurnResolver.resolveTurnStatus(
                        currentUser = user,
                        allCaregivers = snapshot.caregivers,
                        logs = snapshot.careLogs,
                        todayEscalations = snapshot.escalations,
                        nowMillis = nowMillis
                    )
                    val hasUncaredPending = when (turnStatus) {
                        is com.app.check_inpets.model.TurnStatus.MyScheduledTurn -> true
                        is com.app.check_inpets.model.TurnStatus.EscalatedToMe -> true
                        is com.app.check_inpets.model.TurnStatus.NotMyTurn -> turnStatus.hasUncaredPendingTurn
                    }
                    if (!hasUncaredPending) {
                        PetCareNotificationHelper.dismissAllTurnNotifications(applicationContext)
                    }
                }
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) {
        if (startedActivityCount == 0) {
            AppForegroundTracker.isForeground = true
            AppForegroundTracker.consumeSuppressExitNotification()
        }
        startedActivityCount++
        Log.d("CheckinPetsBg", "onActivityStarted: count=$startedActivityCount")
    }

    override fun onActivityResumed(activity: Activity) {
        AppForegroundTracker.isForeground = true
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        Log.d("CheckinPetsBg", "onActivityStopped: count=$startedActivityCount changingConfig=${activity.isChangingConfigurations}")
        if (startedActivityCount == 0 && !activity.isChangingConfigurations) {
            AppForegroundTracker.isForeground = false
            val suppressed = AppForegroundTracker.consumeSuppressExitNotification()
            Log.d("CheckinPetsBg", "onActivityStopped -> left app, suppressed=$suppressed")
            if (!suppressed) {
                appScope.launch {
                    TurnBackgroundCoordinator.handleAppLeftWithoutAnswering(applicationContext)
                }
            }
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
