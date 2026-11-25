package com.app.check_inpets

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.check_inpets.background.BackgroundPermissionsHelper
import com.app.check_inpets.background.BackgroundPermissionsStatus
import com.app.check_inpets.background.TurnAlarmScheduler
import com.app.check_inpets.model.TurnStatus
import com.app.check_inpets.notifications.PetCareNotificationHelper
import com.app.check_inpets.ui.MainViewModel
import com.app.check_inpets.ui.components.DiscreteHealthHeader
import com.app.check_inpets.ui.screens.DeveloperScreen
import com.app.check_inpets.ui.screens.HistoryChartsScreen
import com.app.check_inpets.ui.screens.HomeScreen
import com.app.check_inpets.ui.screens.LoginScreen
import com.app.check_inpets.ui.screens.SettingsScreen
import com.app.check_inpets.ui.theme.CheckinPetsTheme
import com.app.check_inpets.ui.theme.SageGreen
import com.app.check_inpets.update.AppUpdateManager
import com.app.check_inpets.update.AppUpdateState

private enum class AppTab(
    val label: String,
    val icon: ImageVector
) {
    HOME("Início", Icons.Rounded.Pets),
    HISTORY("Histórico", Icons.Rounded.BarChart),
    SETTINGS("Config.", Icons.Rounded.Settings),
    DEVELOPER("Dev", Icons.Rounded.Code)
}

class MainActivity : ComponentActivity() {

    private var openHomeTrigger by mutableIntStateOf(0)
    private var openUpdateTrigger by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PetCareNotificationHelper.ensureChannels(applicationContext)
        handleLaunchIntent(intent)
        enableEdgeToEdge()
        setContent {
            CheckinPetsTheme {
                CheckinPetsApp(
                    openHomeTrigger = openHomeTrigger,
                    openUpdateTrigger = openUpdateTrigger
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        val fromTurnAlert = intent?.getBooleanExtra(
            PetCareNotificationHelper.EXTRA_OPEN_HOME_FROM_ALERT,
            false
        ) == true
        if (fromTurnAlert) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
            openHomeTrigger++
            intent.removeExtra(PetCareNotificationHelper.EXTRA_OPEN_HOME_FROM_ALERT)
        }
        val fromUpdateNotif = intent?.getBooleanExtra(
            PetCareNotificationHelper.EXTRA_OPEN_UPDATE_DIALOG,
            false
        ) == true
        if (fromUpdateNotif) {
            openUpdateTrigger++
            intent.removeExtra(PetCareNotificationHelper.EXTRA_OPEN_UPDATE_DIALOG)
        }
    }
}

@Composable
private fun CheckinPetsApp(
    openHomeTrigger: Int,
    openUpdateTrigger: Int,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var permissionsStatus by remember {
        mutableStateOf(BackgroundPermissionsHelper.checkStatus(context))
    }
    var dismissedOptionalSetup by rememberSaveable { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permissionsStatus = BackgroundPermissionsHelper.checkStatus(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsStatus = BackgroundPermissionsHelper.checkStatus(context)
                viewModel.onInstallPermissionReturned()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(openUpdateTrigger) {
        if (openUpdateTrigger > 0) {
            viewModel.checkForAppUpdate(manual = false)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.installConfirmIntents.collect { confirmIntent ->
            runCatching {
                context.startActivity(confirmIntent)
            }.onFailure { err ->
                snackbarHostState.showSnackbar(
                    err.message ?: "Não foi possível abrir o instalador do Android."
                )
            }
        }
    }

    val currentUser = uiState.currentUser

    LaunchedEffect(
        uiState.isReady,
        currentUser?.id,
        currentUser?.preferredTime,
        uiState.snapshot.careLogs.size,
        uiState.snapshot.escalations.size
    ) {
        if (!uiState.isReady) return@LaunchedEffect
        if (currentUser != null) {
            TurnAlarmScheduler.syncSchedule(
                context = context,
                currentUser = currentUser,
                logs = uiState.snapshot.careLogs,
                escalations = uiState.snapshot.escalations,
                allCaregivers = uiState.snapshot.caregivers
            )
        } else {
            TurnAlarmScheduler.cancelAll(context)
        }
    }

    LaunchedEffect(currentUser?.isDeveloper) {
        if (currentUser?.isDeveloper != true && currentTab == AppTab.DEVELOPER) {
            currentTab = AppTab.HOME
        }
    }

    LaunchedEffect(openHomeTrigger) {
        if (openHomeTrigger > 0) {
            currentTab = AppTab.HOME
        }
    }

    if (!uiState.isReady) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (currentUser == null) {
        LoginScreen(
            hasRegisteredAccounts = uiState.snapshot.caregivers.isNotEmpty(),
            errorMessage = uiState.loginError,
            isBusy = uiState.isBusy,
            onLoginClick = viewModel::loginWithId,
            onCreateInitialDeveloper = viewModel::createInitialDeveloperAndLogin,
            onClearError = viewModel::clearLoginError
        )
        return
    }

    val visibleTabs = buildList {
        add(AppTab.HOME)
        add(AppTab.HISTORY)
        add(AppTab.SETTINGS)
        if (currentUser.isDeveloper) {
            add(AppTab.DEVELOPER)
        }
    }

    val hasUncaredAlert = when (val status = uiState.turnStatus) {
        is TurnStatus.NotMyTurn -> status.hasUncaredPendingTurn
        is TurnStatus.MyScheduledTurn -> status.isOverdue
        is TurnStatus.EscalatedToMe -> true
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            DiscreteHealthHeader(
                user = currentUser,
                healthReport = uiState.healthReport,
                hasUncaredAlert = hasUncaredAlert && currentTab == AppTab.HOME
            )
        },
        bottomBar = {
            NavigationBar {
                visibleTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (currentTab) {
            AppTab.HOME -> HomeScreen(
                currentUser = currentUser,
                turnStatus = uiState.turnStatus,
                lastCareLog = uiState.lastCareLog,
                healthReport = uiState.healthReport,
                isBusy = uiState.isBusy,
                onConfirmCare = viewModel::recordCareNow,
                onDeclineTurn = { viewModel.declineCurrentTurn() },
                modifier = contentModifier
            )

            AppTab.HISTORY -> HistoryChartsScreen(
                lastCareLog = uiState.lastCareLog,
                caregiverStats = uiState.caregiverStats,
                last7DaysPoints = uiState.last7DaysPoints,
                careLogs = uiState.snapshot.careLogs,
                modifier = contentModifier
            )

            AppTab.SETTINGS -> SettingsScreen(
                currentUser = currentUser,
                notificationsEnabled = uiState.notificationsEnabled,
                isUsingFirebaseBackend = uiState.snapshot.isUsingFirebaseBackend,
                permissionsStatus = permissionsStatus,
                onUpdatePreferredTime = viewModel::updateMyPreferredTime,
                onToggleNotifications = viewModel::setNotificationsEnabled,
                onLogout = viewModel::logoutDevice,
                onCheckAppUpdate = { viewModel.checkForAppUpdate(manual = true) },
                isCheckingAppUpdate = updateState is AppUpdateState.Checking,
                modifier = contentModifier
            )

            AppTab.DEVELOPER -> DeveloperScreen(
                currentUserId = currentUser.id,
                caregivers = uiState.snapshot.caregivers,
                settings = uiState.snapshot.settings,
                onUpsertCaregiver = viewModel::upsertCaregiver,
                onDeleteCaregiver = viewModel::deleteCaregiver,
                onUpdateAppSettings = viewModel::updateAppSettings,
                onClearLogs = viewModel::clearLogs,
                modifier = contentModifier
            )
        }
    }

    val needsBatteryExemption = !permissionsStatus.isIgnoringBatteryOptimizations
    val needsExtraPermissions = !permissionsStatus.allGranted

    if (needsBatteryExemption || (needsExtraPermissions && !dismissedOptionalSetup)) {
        BackgroundPermissionsDialog(
            status = permissionsStatus,
            isMandatoryBattery = needsBatteryExemption,
            onRequestBattery = { BackgroundPermissionsHelper.launchBatteryOptimizationRequest(context) },
            onRequestOverlay = { BackgroundPermissionsHelper.launchOverlayPermissionRequest(context) },
            onRequestExactAlarm = { BackgroundPermissionsHelper.launchExactAlarmPermissionRequest(context) },
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onDismiss = {
                if (!needsBatteryExemption) {
                    dismissedOptionalSetup = true
                }
            }
        )
    }

    AppUpdateDialogs(
        updateState = updateState,
        onConfirmDownload = { viewModel.confirmAndDownloadUpdate() },
        onDismiss = { viewModel.dismissUpdateDialog() },
        onOpenInstallPermission = {
            context.startActivity(AppUpdateManager.createInstallPermissionIntent(context))
        }
    )
}

@Composable
private fun AppUpdateDialogs(
    updateState: AppUpdateState,
    onConfirmDownload: () -> Unit,
    onDismiss: () -> Unit,
    onOpenInstallPermission: () -> Unit
) {
    when (updateState) {
        is AppUpdateState.UpdateAvailable -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Nova atualização disponível") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Versão atual: v${updateState.current.versionName}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Nova versão: v${updateState.remote.versionName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = SageGreen
                        )
                        if (updateState.remote.releaseNotes.isNotBlank()) {
                            Text(
                                text = updateState.remote.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = onConfirmDownload) {
                        Text("Atualizar agora")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Depois")
                    }
                }
            )
        }

        is AppUpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Baixando atualização...") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Versão v${updateState.remote.versionName} • ${updateState.progressPercent}%")
                        LinearProgressIndicator(
                            progress = { updateState.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {}
            )
        }

        is AppUpdateState.NeedsInstallPermission -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Permissão para instalar") },
                text = {
                    Text("Autorize o Check-in Pets a instalar atualizações nas configurações do Android para concluir a instalação da versão v${updateState.remote.versionName}.")
                },
                confirmButton = {
                    Button(onClick = onOpenInstallPermission) {
                        Text("Autorizar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                }
            )
        }

        is AppUpdateState.Installing -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Instalando v${updateState.remote.versionName}...") },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Preparando instalação e reinício automático...")
                    }
                },
                confirmButton = {}
            )
        }

        else -> Unit
    }
}

@Composable
private fun BackgroundPermissionsDialog(
    status: BackgroundPermissionsStatus,
    isMandatoryBattery: Boolean,
    onRequestBattery: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestNotifications: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!isMandatoryBattery) onDismiss()
        },
        title = {
            Text(
                if (isMandatoryBattery) {
                    "Desativar economia de bateria"
                } else {
                    "Permissões de segundo plano"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isMandatoryBattery) {
                        "Para rodar em segundo plano, abrir automaticamente no seu horário e enviar a notificação com vibração após 10 minutos, é obrigatório desativar a economia de bateria para este app."
                    } else {
                        "Ative as permissões abaixo para garantir que o app abra automaticamente no horário e envie o lembrete com vibração."
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                PermissionDialogItem(
                    title = "1. Economia de bateria (obrigatório)",
                    granted = status.isIgnoringBatteryOptimizations,
                    onGrant = onRequestBattery
                )

                PermissionDialogItem(
                    title = "2. Sobrepor apps (abrir automaticamente)",
                    granted = status.canDrawOverlays,
                    onGrant = onRequestOverlay
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PermissionDialogItem(
                        title = "3. Alarmes e lembretes exatos",
                        granted = status.canScheduleExactAlarms,
                        onGrant = onRequestExactAlarm
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionDialogItem(
                        title = "4. Notificações",
                        granted = status.hasNotificationsPermission,
                        onGrant = onRequestNotifications
                    )
                }
            }
        },
        confirmButton = {
            if (isMandatoryBattery) {
                Button(
                    onClick = onRequestBattery,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Desativar economia de bateria")
                }
            } else {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Continuar")
                }
            }
        },
        dismissButton = if (!isMandatoryBattery) {
            {
                TextButton(onClick = onDismiss) {
                    Text("Agora não")
                }
            }
        } else null
    )
}

@Composable
private fun PermissionDialogItem(
    title: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (granted) "Ativado" else "Pendente",
                style = MaterialTheme.typography.labelSmall,
                color = if (granted) SageGreen else MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        if (!granted) {
            OutlinedButton(
                onClick = onGrant,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Ativar")
            }
        }
    }
}
