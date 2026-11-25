package com.app.check_inpets.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.app.check_inpets.background.BackgroundPermissionsHelper
import com.app.check_inpets.background.BackgroundPermissionsStatus
import com.app.check_inpets.domain.TurnResolver
import com.app.check_inpets.model.Caregiver
import com.app.check_inpets.ui.components.TimeFormatting
import com.app.check_inpets.ui.theme.CheckinPetsTheme
import com.app.check_inpets.ui.theme.SageGreen
import com.app.check_inpets.update.AppUpdateManager
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentUser: Caregiver,
    notificationsEnabled: Boolean,
    isUsingFirebaseBackend: Boolean,
    onUpdatePreferredTime: (String?) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onCheckAppUpdate: () -> Unit = {},
    isCheckingAppUpdate: Boolean = false,
    modifier: Modifier = Modifier,
    permissionsStatus: BackgroundPermissionsStatus = BackgroundPermissionsStatus(
        isIgnoringBatteryOptimizations = true,
        canDrawOverlays = true,
        canScheduleExactAlarms = true,
        hasNotificationsPermission = true
    )
) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var hasPostNotifPermission by remember(permissionsStatus.hasNotificationsPermission) {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPostNotifPermission = granted
        if (granted) onToggleNotifications(true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = currentUser.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ID vinculado: ${currentUser.id} • Ordem de rodízio: #${currentUser.turnOrder}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (currentUser.isDeveloper) "Perfil: Desenvolvedor" else "Perfil: Cuidador",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Horário preferencial (opcional)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = currentUser.preferredTime?.let {
                                "Configurado para ${TimeFormatting.formatBrasiliaSlotForDevice(it)}"
                            } ?: "Sem horário fixo definido",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (currentUser.preferredTime == null) "Definir horário" else "Alterar horário")
                    }
                    if (currentUser.preferredTime != null) {
                        TextButton(onClick = { onUpdatePreferredTime(null) }) {
                            Text("Remover")
                        }
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Notificações gerais",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Receber lembretes, avisos de horário e repasses de vez",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = notificationsEnabled && hasPostNotifPermission,
                        onCheckedChange = { checked ->
                            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotifPermission) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onToggleNotifications(checked)
                            }
                        }
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.BatteryChargingFull,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Segundo plano e bateria",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                PermissionStatusRow(
                    label = "Economia de bateria",
                    granted = permissionsStatus.isIgnoringBatteryOptimizations,
                    grantedText = "Desativada",
                    missingText = "Obrigatório desativar",
                    buttonText = "Desativar",
                    onAction = { BackgroundPermissionsHelper.launchBatteryOptimizationRequest(context) }
                )

                PermissionStatusRow(
                    label = "Abrir app automaticamente",
                    granted = permissionsStatus.canDrawOverlays,
                    grantedText = "Permitido",
                    missingText = "Sobrepor apps desativado",
                    buttonText = "Permitir",
                    onAction = { BackgroundPermissionsHelper.launchOverlayPermissionRequest(context) }
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PermissionStatusRow(
                        label = "Alarmes no horário exato",
                        granted = permissionsStatus.canScheduleExactAlarms,
                        grantedText = "Permitido",
                        missingText = "Requer permissão",
                        buttonText = "Permitir",
                        onAction = { BackgroundPermissionsHelper.launchExactAlarmPermissionRequest(context) }
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            val currentVersion = remember(context) { AppUpdateManager.getCurrentVersion(context) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Atualização do app",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Versão atual: v${currentVersion.versionName} (${currentVersion.versionCode}) • Verificação diária às 12h",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onCheckAppUpdate,
                    enabled = !isCheckingAppUpdate,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isCheckingAppUpdate) "Verificando..." else "Atualizar")
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Armazenamento",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isUsingFirebaseBackend) {
                        "Sincronizado com Firebase Firestore."
                    } else {
                        "Dados armazenados neste dispositivo."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedButton(
            onClick = { showLogoutConfirm = true },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Logout,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Trocar de ID / Desvincular dispositivo")
        }
    }

    if (showTimePicker) {
        val parsed = TurnResolver.parseTimeOrNull(currentUser.preferredTime)
        val pickerState = rememberTimePickerState(
            initialHour = parsed?.hour ?: 9,
            initialMinute = parsed?.minute ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Escolher horário preferencial") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                Button(
                    onClick = {
                        val formatted = String.format(
                            Locale.getDefault(),
                            "%02d:%02d",
                            pickerState.hour,
                            pickerState.minute
                        )
                        showTimePicker = false
                        onUpdatePreferredTime(formatted)
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Trocar de ID?") },
            text = { Text("Este dispositivo voltará para a tela inicial de identificação de 5 dígitos.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout()
                    }
                ) {
                    Text("Desvincular")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun PermissionStatusRow(
    label: String,
    granted: Boolean,
    grantedText: String,
    missingText: String,
    buttonText: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (granted) grantedText else missingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) SageGreen else MaterialTheme.colorScheme.error
            )
        }
        if (!granted) {
            OutlinedButton(
                onClick = onAction,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(buttonText)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    CheckinPetsTheme {
        SettingsScreen(
            currentUser = Caregiver("00001", "Rafael", isDeveloper = true, preferredTime = "08:00", turnOrder = 1),
            notificationsEnabled = true,
            isUsingFirebaseBackend = true,
            onUpdatePreferredTime = {},
            onToggleNotifications = {},
            onLogout = {}
        )
    }
}
