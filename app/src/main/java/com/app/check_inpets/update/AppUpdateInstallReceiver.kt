package com.app.check_inpets.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.app.check_inpets.MainActivity
import com.app.check_inpets.background.AppForegroundTracker
import com.app.check_inpets.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppUpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.d("CheckinPetsUpdate", "Install status=$status msg=$message")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmIntent != null) {
                    if (AppForegroundTracker.isForeground) {
                        AppUpdateManager.emitInstallEvent(InstallCallbackEvent.ConfirmRequired(confirmIntent))
                    } else {
                        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(confirmIntent) }
                    }
                } else {
                    AppUpdateManager.emitInstallEvent(
                        InstallCallbackEvent.Failed(status, "O sistema não retornou a tela de confirmação.")
                    )
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                val appContext = context.applicationContext
                AppUpdateManager.emitInstallEvent(InstallCallbackEvent.Succeeded)
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    AppContainer.getInstance(appContext).preferencesDataStore
                        .setPendingRestartAfterUpdate(false)
                }
                val launchIntent = Intent(appContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                runCatching { appContext.startActivity(launchIntent) }
            }

            else -> {
                val appContext = context.applicationContext
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    AppContainer.getInstance(appContext).preferencesDataStore
                        .setPendingRestartAfterUpdate(false)
                }
                val friendlyMessage = when (status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "Instalação cancelada."
                    PackageInstaller.STATUS_FAILURE_CONFLICT ->
                        "Assinatura incompatível com o APK instalado. Desinstale o app atual e instale a nova versão manualmente."
                    PackageInstaller.STATUS_FAILURE_STORAGE ->
                        "Espaço insuficiente no dispositivo para instalar a atualização."
                    else -> message?.takeIf { it.isNotBlank() }
                        ?: "Falha na instalação (código $status)."
                }
                AppUpdateManager.emitInstallEvent(InstallCallbackEvent.Failed(status, friendlyMessage))
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.app.check_inpets.ACTION_INSTALL_STATUS"
    }
}
