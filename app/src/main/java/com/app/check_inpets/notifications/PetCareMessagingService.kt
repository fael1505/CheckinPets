package com.app.check_inpets.notifications

import com.app.check_inpets.di.AppContainer
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PetCareMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val container = AppContainer.getInstance(applicationContext)
        serviceScope.launch {
            container.repository.updateFcmTokenForCurrentUser(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val container = AppContainer.getInstance(applicationContext)
        serviceScope.launch {
            val notificationsEnabled = container.preferencesDataStore.notificationsEnabled.first()
            if (notificationsEnabled) {
                val slotTime = message.data["slotTime"]
                val escalationId = message.data["escalationId"] ?: "fcm_${System.currentTimeMillis()}"
                PetCareNotificationHelper.showEscalatedToMeNotification(
                    context = applicationContext,
                    slotTime = slotTime,
                    escalationId = escalationId
                )
            }
            container.repository.refresh()
        }
    }
}
