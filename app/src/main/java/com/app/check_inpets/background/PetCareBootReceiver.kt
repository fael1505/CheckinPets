package com.app.check_inpets.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.app.check_inpets.MainActivity
import com.app.check_inpets.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PetCareBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = AppContainer.getInstance(appContext)
                TurnAlarmScheduler.scheduleDailyNoonUpdateCheck(appContext)

                val linkedId = container.preferencesDataStore.linkedCaregiverId.first()
                if (linkedId != null) {
                    val cachedTime = container.preferencesDataStore.cachedPreferredTime.first()
                    TurnAlarmScheduler.scheduleFromCachedPreferredTime(appContext, cachedTime)
                }

                if (action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    val shouldRestart = container.preferencesDataStore.pendingRestartAfterUpdate.first()
                    if (shouldRestart) {
                        container.preferencesDataStore.setPendingRestartAfterUpdate(false)
                        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        runCatching { appContext.startActivity(launchIntent) }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
