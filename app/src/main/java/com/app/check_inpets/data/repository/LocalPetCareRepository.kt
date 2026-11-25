package com.app.check_inpets.data.repository

import android.content.Context
import com.app.check_inpets.data.local.UserPreferencesDataStore
import com.app.check_inpets.domain.TurnResolver
import com.app.check_inpets.model.AppSettings
import com.app.check_inpets.model.CareLog
import com.app.check_inpets.model.Caregiver
import com.app.check_inpets.model.EscalationStatus
import com.app.check_inpets.model.TurnEscalation
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
private data class LocalDbState(
    val caregivers: List<Caregiver> = emptyList(),
    val careLogs: List<CareLog> = emptyList(),
    val escalations: List<TurnEscalation> = emptyList(),
    val settings: AppSettings = AppSettings()
)

class LocalPetCareRepository(
    private val context: Context,
    private val prefs: UserPreferencesDataStore
) : PetCareRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _snapshot = MutableStateFlow(PetCareSnapshot())

    override val snapshot: Flow<PetCareSnapshot> = _snapshot.asStateFlow()
    override val linkedCaregiverId: Flow<String?> = prefs.linkedCaregiverId
    override val notificationsEnabled: Flow<Boolean> = prefs.notificationsEnabled

    init {
        scope.launch {
            loadState()
        }
    }

    private suspend fun loadState() {
        val raw = prefs.localDbSnapshotJson.first()
        val state = if (!raw.isNullOrBlank()) {
            runCatching { json.decodeFromString<LocalDbState>(raw) }.getOrNull()
        } else null

        val resolved = state ?: LocalDbState().also { persistState(it) }
        publish(resolved)
    }

    private suspend fun persistState(state: LocalDbState) {
        prefs.saveLocalDbSnapshot(json.encodeToString(state))
        publish(state)
    }

    private fun publish(state: LocalDbState) {
        _snapshot.value = PetCareSnapshot(
            caregivers = state.caregivers.sortedWith(compareBy({ it.turnOrder }, { it.id })),
            careLogs = state.careLogs.sortedByDescending { it.caredAtEpochMillis },
            escalations = state.escalations.sortedByDescending { it.createdAtEpochMillis },
            settings = state.settings,
            isUsingFirebaseBackend = false
        )
    }

    private fun currentState(): LocalDbState {
        val snap = _snapshot.value
        return LocalDbState(
            caregivers = snap.caregivers,
            careLogs = snap.careLogs,
            escalations = snap.escalations,
            settings = snap.settings
        )
    }

    override suspend fun refresh() {
        loadState()
    }

    override suspend fun loginWithFiveDigitId(id: String): Result<Caregiver> {
        val cleanId = id.trim()
        if (!cleanId.matches(Regex("^\\d{5}$"))) {
            return Result.failure(IllegalArgumentException("O ID deve conter exatamente 5 dígitos numéricos."))
        }
        val state = currentState()
        val found = state.caregivers.firstOrNull { it.id == cleanId }
            ?: return Result.failure(IllegalArgumentException("ID $cleanId não encontrado no banco de dados."))
        if (!found.isActive) {
            return Result.failure(IllegalStateException("Este ID está desativado. Procure um desenvolvedor."))
        }

        val token = fetchFcmTokenIfAvailable()
        val updatedCaregiver = found.copy(fcmToken = token ?: found.fcmToken)
        val updatedState = state.copy(
            caregivers = state.caregivers.map { if (it.id == cleanId) updatedCaregiver else it }
        )
        persistState(updatedState)
        prefs.setLinkedCaregiverId(cleanId)
        return Result.success(updatedCaregiver)
    }

    override suspend fun createInitialDeveloperAndLogin(
        id: String,
        name: String,
        preferredTime: String?
    ): Result<Caregiver> {
        val cleanId = id.trim()
        val cleanName = name.trim()
        val normalizedTime = preferredTime?.trim()?.takeIf { it.isNotEmpty() }

        if (!cleanId.matches(Regex("^\\d{5}$"))) {
            return Result.failure(IllegalArgumentException("O ID deve conter exatamente 5 dígitos numéricos."))
        }
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Informe seu nome."))
        }
        if (normalizedTime != null && TurnResolver.parseTimeOrNull(normalizedTime) == null) {
            return Result.failure(IllegalArgumentException("Horário inválido. Use o formato HH:mm."))
        }

        val state = currentState()
        if (state.caregivers.any { it.id == cleanId }) {
            return Result.failure(IllegalArgumentException("Este ID já existe no banco de dados."))
        }

        val token = fetchFcmTokenIfAvailable()
        val devAccount = Caregiver(
            id = cleanId,
            name = cleanName,
            isDeveloper = true,
            preferredTime = normalizedTime,
            turnOrder = 1,
            isActive = true,
            fcmToken = token
        )
        persistState(state.copy(caregivers = state.caregivers + devAccount))
        prefs.setLinkedCaregiverId(cleanId)
        return Result.success(devAccount)
    }

    override suspend fun logoutDevice() {
        prefs.setLinkedCaregiverId(null)
    }

    override suspend fun recordPetCare(
        caregiver: Caregiver,
        wasScheduledTurn: Boolean,
        pendingEscalationId: String?,
        notes: String?
    ): Result<CareLog> {
        val state = currentState()
        val now = System.currentTimeMillis()
        val log = CareLog(
            id = UUID.randomUUID().toString(),
            caregiverId = caregiver.id,
            caregiverName = caregiver.name,
            caredAtEpochMillis = now,
            wasScheduledTurn = wasScheduledTurn,
            notes = notes
        )

        val updatedEscalations = state.escalations.map {
            if (it.id == pendingEscalationId || (it.status == EscalationStatus.PENDING && it.createdAtEpochMillis <= now)) {
                it.copy(status = EscalationStatus.ACCEPTED_DONE)
            } else {
                it
            }
        }

        persistState(
            state.copy(
                careLogs = listOf(log) + state.careLogs,
                escalations = updatedEscalations
            )
        )
        return Result.success(log)
    }

    override suspend fun declineTurnAndNotifyNext(
        decliningCaregiver: Caregiver,
        slotTime: String,
        existingEscalationId: String?
    ): Result<TurnEscalation> {
        val state = currentState()
        val nextCaregiver = TurnResolver.findNextCaregiverForEscalation(
            decliningUser = decliningCaregiver,
            allCaregivers = state.caregivers
        )
        val targetId = nextCaregiver?.id
            ?: TurnResolver.nextNonExistentCaregiverId(decliningCaregiver.id, state.caregivers)
        val targetName = nextCaregiver?.name.orEmpty()

        val updatedEscalations = if (existingEscalationId != null) {
            state.escalations.map {
                if (it.id == existingEscalationId) it.copy(status = EscalationStatus.DECLINED_AGAIN)
                else it
            }
        } else {
            state.escalations
        }

        val newEscalation = TurnEscalation(
            id = UUID.randomUUID().toString(),
            dateKey = TurnResolver.todayKey(),
            originalCaregiverId = decliningCaregiver.id,
            originalCaregiverName = decliningCaregiver.name,
            targetCaregiverId = targetId,
            targetCaregiverName = targetName,
            slotTime = slotTime,
            status = EscalationStatus.PENDING,
            createdAtEpochMillis = System.currentTimeMillis()
        )

        persistState(
            state.copy(escalations = listOf(newEscalation) + updatedEscalations)
        )

        return Result.success(newEscalation)
    }

    override suspend fun updateMyPreferredTime(caregiverId: String, preferredTime: String?): Result<Unit> {
        val state = currentState()
        val normalized = preferredTime?.trim()?.takeIf { it.isNotEmpty() }
        if (normalized != null && TurnResolver.parseTimeOrNull(normalized) == null) {
            return Result.failure(IllegalArgumentException("Horário inválido. Use o formato HH:mm."))
        }
        persistState(
            state.copy(
                caregivers = state.caregivers.map {
                    if (it.id == caregiverId) it.copy(preferredTime = normalized) else it
                }
            )
        )
        return Result.success(Unit)
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        prefs.setNotificationsEnabled(enabled)
    }

    override suspend fun updateFcmTokenForCurrentUser(token: String) {
        val currentId = prefs.linkedCaregiverId.first() ?: return
        val state = currentState()
        persistState(
            state.copy(
                caregivers = state.caregivers.map {
                    if (it.id == currentId) it.copy(fcmToken = token) else it
                }
            )
        )
    }

    override suspend fun upsertCaregiver(caregiver: Caregiver): Result<Unit> {
        if (!caregiver.id.matches(Regex("^\\d{5}$"))) {
            return Result.failure(IllegalArgumentException("ID deve conter 5 dígitos numéricos."))
        }
        if (caregiver.name.isBlank()) {
            return Result.failure(IllegalArgumentException("Informe o nome da pessoa."))
        }
        val state = currentState()
        val exists = state.caregivers.any { it.id == caregiver.id }
        val updated = if (exists) {
            state.caregivers.map { if (it.id == caregiver.id) caregiver else it }
        } else {
            state.caregivers + caregiver
        }
        persistState(state.copy(caregivers = updated))
        return Result.success(Unit)
    }

    override suspend fun deleteCaregiver(caregiverId: String): Result<Unit> {
        val currentId = prefs.linkedCaregiverId.first()
        if (currentId == caregiverId) {
            return Result.failure(IllegalStateException("Não é possível excluir o ID atualmente vinculado a este dispositivo."))
        }
        val state = currentState()
        persistState(
            state.copy(
                caregivers = state.caregivers.filterNot { it.id == caregiverId },
                careLogs = state.careLogs.filterNot { it.caregiverId == caregiverId },
                escalations = state.escalations.filterNot {
                    it.originalCaregiverId == caregiverId || it.targetCaregiverId == caregiverId
                }
            )
        )
        return Result.success(Unit)
    }

    override suspend fun updateAppSettings(settings: AppSettings): Result<Unit> {
        val state = currentState()
        persistState(state.copy(settings = settings))
        return Result.success(Unit)
    }

    override suspend fun clearAllLogs(): Result<Unit> {
        val state = currentState()
        persistState(state.copy(careLogs = emptyList(), escalations = emptyList()))
        return Result.success(Unit)
    }

    private suspend fun fetchFcmTokenIfAvailable(): String? {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        return runCatching {
            FirebaseMessaging.getInstance().token.await()
        }.getOrNull()
    }
}
