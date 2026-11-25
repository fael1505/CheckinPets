package com.app.check_inpets.background

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

data class BackgroundPermissionsStatus(
    val isIgnoringBatteryOptimizations: Boolean,
    val canDrawOverlays: Boolean,
    val canScheduleExactAlarms: Boolean,
    val hasNotificationsPermission: Boolean
) {
    val allGranted: Boolean
        get() = isIgnoringBatteryOptimizations &&
            canDrawOverlays &&
            canScheduleExactAlarms &&
            hasNotificationsPermission
}

object BackgroundPermissionsHelper {

    fun checkStatus(context: Context): BackgroundPermissionsStatus {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)

        val canOverlay = Settings.canDrawOverlays(context)

        val canExactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        val hasNotif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        return BackgroundPermissionsStatus(
            isIgnoringBatteryOptimizations = isIgnoringBattery,
            canDrawOverlays = canOverlay,
            canScheduleExactAlarms = canExactAlarm,
            hasNotificationsPermission = hasNotif
        )
    }

    fun launchBatteryOptimizationRequest(context: Context) {
        AppForegroundTracker.suppressNextExitNotification()
        val packageUri = Uri.parse("package:${context.packageName}")
        val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val started = runCatching {
            context.startActivity(directIntent)
            true
        }.getOrDefault(false)

        if (!started) {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
    }

    fun launchOverlayPermissionRequest(context: Context) {
        AppForegroundTracker.suppressNextExitNotification()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun launchExactAlarmPermissionRequest(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AppForegroundTracker.suppressNextExitNotification()
            val intent = Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }
}
