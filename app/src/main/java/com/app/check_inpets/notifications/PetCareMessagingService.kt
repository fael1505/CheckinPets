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
                val title = message.notification?.title
                    ?: "Sua vez de cuidar dos cachorros!"
                val declinedBy = message.data["declinedByName"]
                val slotTime = message.data["slotTime"]
                val body = message.notification?.body
                    ?: if (!declinedBy.isNullOrBlank()) {
                        "$declinedBy não poderá cuidar (${slotTime ?: "agora"}). Você é o próximo da fila!"
                    } else {
                        "Alguém recusou o turno e agora é a sua vez de cuidar dos pets."
                    }

                PetCareNotificationHelper.showTurnEscalationNotification(
                    context = applicationContext,
                    title = title,
                    body = body
                )
            }
            container.repository.refresh()
        }
    }
}
