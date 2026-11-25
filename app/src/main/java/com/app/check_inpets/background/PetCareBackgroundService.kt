package com.app.check_inpets.background

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Mantido apenas para compatibilidade durante atualizações; o app não usa mais serviço em
 * primeiro plano nem notificação persistente na barra de notificações.
 */
class PetCareBackgroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    companion object {
        fun stop(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, PetCareBackgroundService::class.java)
            runCatching {
                appContext.stopService(intent)
            }
        }
    }
}
