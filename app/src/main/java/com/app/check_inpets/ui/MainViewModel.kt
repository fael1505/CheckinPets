package com.app.check_inpets.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.check_inpets.background.TurnAlarmScheduler
import com.app.check_inpets.data.repository.PetCareSnapshot
import com.app.check_inpets.di.AppContainer
import com.app.check_inpets.domain.PetHealthCalculator
import com.app.check_inpets.domain.TurnResolver
import com.app.check_inpets.model.AppSettings
import com.app.check_inpets.model.CareLog
import com.app.check_inpets.model.Caregiver
import com.app.check_inpets.model.CaregiverStats
import com.app.check_inpets.model.DailyCarePoint
import com.app.check_inpets.model.PetHealthReport
import com.app.check_inpets.model.TurnStatus
import com.app.check_inpets.notifications.PetCareNotificationHelper
import com.app.check_inpets.update.AppUpdateManager
import com.app.check_inpets.update.AppUpdateState
import com.app.check_inpets.update.RemoteAppVersion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class MainUiState(
    val isReady: Boolean = false,
    val currentUser: Caregiver? = null,
    val snapshot: PetCareSnapshot = PetCareSnapshot(),
    val turnStatus: TurnStatus = TurnStatus.NotMyTurn(null),
    val healthReport: PetHealthReport = PetHealthReport(
        score = 100,
        statusLabel = "Iniciando padrão",
        learnedDailyTarget = 2.0,
        disciplineScore = 100,
        regularityScore = 100,
        daysObserved = 0
    ),
    val lastCareLog: CareLog? = null,
    val caregiverStats: List<CaregiverStats> = emptyList(),
    val last7DaysPoints: List<DailyCarePoint> = emptyList(),
    val notificationsEnabled: Boolean = true,
    val loginError: String? = null,
    val isBusy: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppContainer.getInstance(application).repository

    private val loginErrorFlow = MutableStateFlow<String?>(null)
    private val isBusyFlow = MutableStateFlow(false)
    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState
    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            AppUpdateManager.foregroundUpdates.collect { update ->
                _updateState.value = update
            }
        }
        checkForAppUpdate(manual = false)
    }

    private val tickerFlow = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            val millisIntoMinute = now % 60_000L
            val millisUntilNextMinute = 60_000L - millisIntoMinute + 150L
            delay(minOf(5_000L, millisUntilNextMinute))
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        repository.snapshot,
        repository.linkedCaregiverId,
        repository.notificationsEnabled,
        tickerFlow,
        loginErrorFlow,
        isBusyFlow
    ) { values ->
        val snapshot = values[0] as PetCareSnapshot
        val linkedId = values[1] as String?
        val notifEnabled = values[2] as Boolean
        val nowMillis = values[3] as Long
        val loginError = values[4] as String?
        val isBusy = values[5] as Boolean

        val currentUser = linkedId?.let { id ->
            snapshot.caregivers.firstOrNull { it.id == id && it.isActive }
        }

        val todayKey = TurnResolver.todayKey(nowMillis)
        val todayEscalations = snapshot.escalations.filter { it.dateKey == todayKey }

        val turnStatus = if (currentUser != null) {
            TurnResolver.resolveTurnStatus(
                currentUser = currentUser,
                allCaregivers = snapshot.caregivers,
                logs = snapshot.careLogs,
                todayEscalations = snapshot.escalations,
                nowMillis = nowMillis
            )
        } else {
            TurnStatus.NotMyTurn(null)
        }

        val healthReport = PetHealthCalculator.calculate(
            logs = snapshot.careLogs,
            caregivers = snapshot.caregivers,
            escalations = todayEscalations,
            settings = snapshot.settings,
            nowMillis = nowMillis
        )

        val stats = buildCaregiverStats(snapshot)
        val dailyPoints = buildLast7DaysPoints(snapshot.careLogs, nowMillis)

        MainUiState(
            isReady = true,
            currentUser = currentUser,
            snapshot = snapshot,
            turnStatus = turnStatus,
            healthReport = healthReport,
            lastCareLog = snapshot.careLogs.firstOrNull(),
            caregiverStats = stats,
            last7DaysPoints = dailyPoints,
            notificationsEnabled = notifEnabled,
            loginError = loginError,
            isBusy = isBusy
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MainUiState()
    )

    fun clearLoginError() {
        loginErrorFlow.value = null
    }

    fun loginWithId(id: String) {
        viewModelScope.launch {
            isBusyFlow.value = true
            loginErrorFlow.value = null
            repository.loginWithFiveDigitId(id)
                .onSuccess {
                    _events.emit("Bem-vindo(a), ${it.name}!")
                }
                .onFailure { err ->
                    loginErrorFlow.value = err.message ?: "Não foi possível entrar."
                }
            isBusyFlow.value = false
        }
    }

    fun createInitialDeveloperAndLogin(id: String, name: String, preferredTime: String?) {
        viewModelScope.launch {
            isBusyFlow.value = true
            loginErrorFlow.value = null
            repository.createInitialDeveloperAndLogin(id, name, preferredTime)
                .onSuccess {
                    _events.emit("Conta principal criada. Bem-vindo(a), ${it.name}!")
                }
                .onFailure { err ->
                    loginErrorFlow.value = err.message ?: "Não foi possível criar a conta."
                }
            isBusyFlow.value = false
        }
    }

    fun logoutDevice() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            TurnAlarmScheduler.cancelAll(app)
            PetCareNotificationHelper.dismissAllTurnNotifications(app)
            repository.logoutDevice()
            loginErrorFlow.value = null
        }
    }

    fun recordCareNow(wasScheduledTurn: Boolean) {
        val state = uiState.value
        val user = state.currentUser ?: return
        val escalationId = (state.turnStatus as? TurnStatus.EscalatedToMe)?.escalation?.id

        viewModelScope.launch {
            isBusyFlow.value = true
            repository.recordPetCare(
                caregiver = user,
                wasScheduledTurn = wasScheduledTurn,
                pendingEscalationId = escalationId
            ).onSuccess {
                val app = getApplication<Application>()
                TurnAlarmScheduler.cancelSnoozeReminder(app)
                AppContainer.getInstance(app).preferencesDataStore.setSnoozeUntilMillis(null)
                PetCareNotificationHelper.dismissAllTurnNotifications(app)
                _events.emit("Cuidado registrado! Os cachorros agradecem.")
            }.onFailure {
                _events.emit(it.message ?: "Erro ao registrar cuidado.")
            }
            isBusyFlow.value = false
        }
    }

    fun declineCurrentTurn() {
        val state = uiState.value
        val user = state.currentUser ?: return
        val turn = state.turnStatus

        if (turn is TurnStatus.NotMyTurn) {
            if (!turn.hasUncaredPendingTurn) return
            viewModelScope.launch {
                val app = getApplication<Application>()
                val pendingSlot = turn.pendingSlotTime
                if (!pendingSlot.isNullOrBlank()) {
                    val todayKey = TurnResolver.todayKey()
                    AppContainer.getInstance(app).preferencesDataStore
                        .setDismissedSlotKey("${todayKey}_$pendingSlot")
                }
                TurnAlarmScheduler.cancelSnoozeReminder(app)
                AppContainer.getInstance(app).preferencesDataStore.setSnoozeUntilMillis(null)
                PetCareNotificationHelper.dismissAllTurnNotifications(app)
                _events.emit("Ok, aviso encerrado neste dispositivo.")
            }
            return
        }

        val (slotTime, escalationId) = when (turn) {
            is TurnStatus.MyScheduledTurn -> turn.slotTime to null
            is TurnStatus.EscalatedToMe -> turn.escalation.slotTime to turn.escalation.id
            is TurnStatus.NotMyTurn -> return
        }

        viewModelScope.launch {
            isBusyFlow.value = true
            repository.declineTurnAndNotifyNext(
                decliningCaregiver = user,
                slotTime = slotTime,
                existingEscalationId = escalationId
            ).onSuccess { esc ->
                val app = getApplication<Application>()
                TurnAlarmScheduler.cancelSnoozeReminder(app)
                AppContainer.getInstance(app).preferencesDataStore.setSnoozeUntilMillis(null)
                PetCareNotificationHelper.dismissAllTurnNotifications(app)
                if (esc.targetCaregiverName.isNotBlank()) {
                    _events.emit("Vez passada para ${esc.targetCaregiverName}.")
                }
            }.onFailure {
                _events.emit(it.message ?: "Não foi possível passar a vez.")
            }
            isBusyFlow.value = false
        }
    }

    fun updateMyPreferredTime(preferredTime: String?) {
        val user = uiState.value.currentUser ?: return
        viewModelScope.launch {
            repository.updateMyPreferredTime(user.id, preferredTime)
                .onSuccess {
                    AppContainer.getInstance(getApplication()).preferencesDataStore.clearTurnPromptKeys()
                    _events.emit("Horário preferencial atualizado.")
                }
                .onFailure { _events.emit(it.message ?: "Erro ao atualizar horário.") }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setNotificationsEnabled(enabled)
        }
    }

    fun upsertCaregiver(caregiver: Caregiver) {
        viewModelScope.launch {
            repository.upsertCaregiver(caregiver)
                .onSuccess { _events.emit("Conta ${caregiver.id} salva no banco.") }
                .onFailure { _events.emit(it.message ?: "Erro ao salvar conta.") }
        }
    }

    fun deleteCaregiver(caregiverId: String) {
        viewModelScope.launch {
            repository.deleteCaregiver(caregiverId)
                .onSuccess { _events.emit("Conta $caregiverId removida.") }
                .onFailure { _events.emit(it.message ?: "Erro ao remover conta.") }
        }
    }

    fun updateAppSettings(settings: AppSettings) {
        viewModelScope.launch {
            repository.updateAppSettings(settings)
                .onSuccess { _events.emit("Configurações globais salvas.") }
                .onFailure { _events.emit(it.message ?: "Erro ao salvar configurações.") }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearAllLogs()
                .onSuccess { _events.emit("Histórico limpo.") }
                .onFailure { _events.emit(it.message ?: "Erro ao limpar histórico.") }
        }
    }

    fun checkForAppUpdate(manual: Boolean) {
        val current = _updateState.value
        if (current is AppUpdateState.Downloading || current is AppUpdateState.Installing) return

        viewModelScope.launch {
            if (manual) {
                _updateState.value = AppUpdateState.Checking
            }
            when (val result = AppUpdateManager.checkForUpdate(getApplication())) {
                is AppUpdateState.UpdateAvailable -> {
                    _updateState.value = result
                }
                is AppUpdateState.UpToDate -> {
                    _updateState.value = AppUpdateState.Idle
                    if (manual) {
                        _events.emit("Você já está na versão mais recente (v${result.current.versionName}).")
                    }
                }
                is AppUpdateState.Error -> {
                    _updateState.value = AppUpdateState.Idle
                    if (manual) {
                        _events.emit(result.message)
                    }
                }
                else -> _updateState.value = AppUpdateState.Idle
            }
        }
    }

    fun confirmAndDownloadUpdate() {
        val remote = (_updateState.value as? AppUpdateState.UpdateAvailable)?.remote ?: return
        viewModelScope.launch {
            val app = getApplication<Application>()
            _updateState.value = AppUpdateState.Downloading(0, remote)
            AppUpdateManager.downloadApk(app, remote) { progress ->
                _updateState.value = AppUpdateState.Downloading(progress, remote)
            }.onSuccess { apkFile ->
                startInstall(apkFile, remote)
            }.onFailure { err ->
                _updateState.value = AppUpdateState.Idle
                _events.emit(err.message ?: "Erro ao baixar atualização.")
            }
        }
    }

    fun onInstallPermissionReturned() {
        val state = _updateState.value as? AppUpdateState.NeedsInstallPermission ?: return
        val app = getApplication<Application>()
        if (AppUpdateManager.canRequestPackageInstalls(app)) {
            viewModelScope.launch {
                startInstall(state.apkFile, state.remote)
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateState.value = AppUpdateState.Idle
    }

    private suspend fun startInstall(apkFile: java.io.File, remote: RemoteAppVersion) {
        val app = getApplication<Application>()
        if (!AppUpdateManager.canRequestPackageInstalls(app)) {
            _updateState.value = AppUpdateState.NeedsInstallPermission(apkFile, remote)
            return
        }
        _updateState.value = AppUpdateState.Installing(remote)
        AppUpdateManager.installApk(app, apkFile).onFailure { err ->
            _updateState.value = AppUpdateState.Idle
            _events.emit(err.message ?: "Não foi possível iniciar a instalação.")
        }
    }

    private fun buildCaregiverStats(snapshot: PetCareSnapshot): List<CaregiverStats> {
        val countsById = snapshot.careLogs.groupBy { it.caregiverId }
        val knownCaregivers = snapshot.caregivers.associateBy { it.id }

        val allIds = (knownCaregivers.keys + countsById.keys).distinct()
        return allIds.map { id ->
            val logs = countsById[id].orEmpty()
            val name = knownCaregivers[id]?.name ?: logs.firstOrNull()?.caregiverName ?: "Cuidador"
            CaregiverStats(
                caregiverId = id,
                caregiverName = name,
                totalCount = logs.size,
                scheduledCount = logs.count { it.wasScheduledTurn }
            )
        }.sortedByDescending { it.totalCount }
    }

    private fun buildLast7DaysPoints(logs: List<CareLog>, nowMillis: Long): List<DailyCarePoint> {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val labelFormatter = DateTimeFormatter.ofPattern("dd/MM")
        val grouped = logs.groupBy {
            Instant.ofEpochMilli(it.caredAtEpochMillis).atZone(zone).toLocalDate()
        }
        return (6 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            DailyCarePoint(
                dateLabel = if (offset == 0) "Hoje" else day.format(labelFormatter),
                count = grouped[day]?.size ?: 0
            )
        }
    }
}
