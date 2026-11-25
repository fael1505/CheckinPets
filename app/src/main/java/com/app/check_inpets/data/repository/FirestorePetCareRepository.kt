package com.app.check_inpets.data.repository

import com.app.check_inpets.data.local.UserPreferencesDataStore
import com.app.check_inpets.domain.TurnResolver
import com.app.check_inpets.model.AppSettings
import com.app.check_inpets.model.CareLog
import com.app.check_inpets.model.Caregiver
import com.app.check_inpets.model.EscalationStatus
import com.app.check_inpets.model.TurnEscalation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Repositório remoto usando Cloud Firestore (plano gratuito Spark do Firebase).
 *
 * Coleções utilizadas:
 * - `caregivers/{id}`
 * - `careLogs/{id}`
 * - `turnEscalations/{id}`
 * - `appSettings/default`
 */
class FirestorePetCareRepository(
    private val prefs: UserPreferencesDataStore
) : PetCareRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val caregiversCol = db.collection("caregivers")
    private val careLogsCol = db.collection("careLogs")
    private val escalationsCol = db.collection("turnEscalations")
    private val settingsDoc = db.collection("appSettings").document("default")

    private val _snapshot = MutableStateFlow(PetCareSnapshot(isUsingFirebaseBackend = true))
    override val snapshot: Flow<PetCareSnapshot> = _snapshot.asStateFlow()
    override val linkedCaregiverId: Flow<String?> = prefs.linkedCaregiverId
    override val notificationsEnabled: Flow<Boolean> = prefs.notificationsEnabled

    init {
        scope.launch {
            runCatching { ensureSignedIn() }
            startRealtimeListeners()
            refresh()
        }
    }

    private suspend fun ensureSignedIn() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    private fun startRealtimeListeners() {
        caregiversCol
            .orderBy("turnOrder", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val list = snap.documents.mapNotNull { it.toCaregiverOrNull() }
                    _snapshot.update { it.copy(caregivers = list, isUsingFirebaseBackend = true) }
                }
            }

        careLogsCol
            .orderBy("caredAtEpochMillis", Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val list = snap.documents.mapNotNull { it.toCareLogOrNull() }
                    _snapshot.update { it.copy(careLogs = list, isUsingFirebaseBackend = true) }
                }
            }

        escalationsCol
            .orderBy("createdAtEpochMillis", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val list = snap.documents
                        .mapNotNull { it.toTurnEscalationOrNull() }
                        .sortedByDescending { it.createdAtEpochMillis }
                    _snapshot.update { it.copy(escalations = list, isUsingFirebaseBackend = true) }
                }
            }

        settingsDoc.addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                _snapshot.update {
                    it.copy(settings = doc.toAppSettings(), isUsingFirebaseBackend = true)
                }
            }
        }
    }

    override suspend fun refresh() {
        runCatching {
            ensureSignedIn()
            val caregiversSnap = caregiversCol
                .orderBy("turnOrder", Query.Direction.ASCENDING)
                .get()
                .await()
            val logsSnap = careLogsCol
                .orderBy("caredAtEpochMillis", Query.Direction.DESCENDING)
                .limit(200)
                .get()
                .await()
            val escSnap = escalationsCol
                .orderBy("createdAtEpochMillis", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()
            val settingsSnap = settingsDoc.get().await()

            val caregivers = caregiversSnap.documents.mapNotNull { it.toCaregiverOrNull() }
            val logs = logsSnap.documents.mapNotNull { it.toCareLogOrNull() }
            val escalations = escSnap.documents
                .mapNotNull { it.toTurnEscalationOrNull() }
                .sortedByDescending { it.createdAtEpochMillis }
            val settings = if (settingsSnap.exists()) settingsSnap.toAppSettings() else AppSettings()

            _snapshot.value = PetCareSnapshot(
                caregivers = caregivers,
                careLogs = logs,
                escalations = escalations,
                settings = settings,
                isUsingFirebaseBackend = true
            )
        }
    }

    override suspend fun loginWithFiveDigitId(id: String): Result<Caregiver> = runCatching {
        val cleanId = id.trim()
        require(cleanId.matches(Regex("^\\d{5}$"))) { "O ID deve conter 5 dígitos numéricos." }
        ensureSignedIn()

        val doc = caregiversCol.document(cleanId).get().await()
        val caregiver = doc.toCaregiverOrNull()
            ?: error("ID $cleanId não encontrado no banco.")
        check(caregiver.isActive) { "Este ID está desativado." }

        val fcmToken = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
        val authUid = auth.currentUser?.uid

        val updates = mutableMapOf<String, Any?>(
            "linkedAuthUid" to authUid
        )
        if (fcmToken != null) {
            updates["fcmToken"] = fcmToken
        }
        caregiversCol.document(cleanId).set(updates, SetOptions.merge()).await()

        prefs.setLinkedCaregiverId(cleanId)
        refresh()

        caregiver.copy(linkedAuthUid = authUid, fcmToken = fcmToken ?: caregiver.fcmToken)
    }

    override suspend fun createInitialDeveloperAndLogin(
        id: String,
        name: String,
        preferredTime: String?
    ): Result<Caregiver> = runCatching {
        val cleanId = id.trim()
        val cleanName = name.trim()
        val normalizedTime = preferredTime?.trim()?.takeIf { it.isNotEmpty() }
        require(cleanId.matches(Regex("^\\d{5}$"))) { "O ID deve conter 5 dígitos numéricos." }
        require(cleanName.isNotBlank()) { "Informe seu nome." }

        ensureSignedIn()
        val fcmToken = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
        val authUid = auth.currentUser?.uid

        val caregiver = Caregiver(
            id = cleanId,
            name = cleanName,
            isDeveloper = true,
            preferredTime = normalizedTime,
            turnOrder = 1,
            isActive = true,
            linkedAuthUid = authUid,
            fcmToken = fcmToken
        )
        caregiversCol.document(cleanId).set(caregiver.toMap()).await()
        prefs.setLinkedCaregiverId(cleanId)
        refresh()
        caregiver
    }

    override suspend fun logoutDevice() {
        prefs.setLinkedCaregiverId(null)
    }

    override suspend fun recordPetCare(
        caregiver: Caregiver,
        wasScheduledTurn: Boolean,
        pendingEscalationId: String?,
        notes: String?
    ): Result<CareLog> = runCatching {
        ensureSignedIn()
        val now = System.currentTimeMillis()
        val logId = UUID.randomUUID().toString()
        val log = CareLog(
            id = logId,
            caregiverId = caregiver.id,
            caregiverName = caregiver.name,
            caredAtEpochMillis = now,
            wasScheduledTurn = wasScheduledTurn,
            notes = notes
        )

        careLogsCol.document(logId).set(
            mapOf(
                "id" to log.id,
                "caregiverId" to log.caregiverId,
                "caregiverName" to log.caregiverName,
                "caredAtEpochMillis" to log.caredAtEpochMillis,
                "wasScheduledTurn" to log.wasScheduledTurn,
                "notes" to log.notes
            )
        ).await()

        val openPendingEscalations = _snapshot.value.escalations.filter {
            it.status == EscalationStatus.PENDING && it.createdAtEpochMillis <= now
        }.map { it.id }.toMutableSet()
        if (pendingEscalationId != null) {
            openPendingEscalations.add(pendingEscalationId)
        }
        for (escId in openPendingEscalations) {
            escalationsCol.document(escId)
                .update("status", EscalationStatus.ACCEPTED_DONE.name)
                .await()
        }

        refresh()
        log
    }

    override suspend fun declineTurnAndNotifyNext(
        decliningCaregiver: Caregiver,
        slotTime: String,
        existingEscalationId: String?
    ): Result<TurnEscalation> = runCatching {
        ensureSignedIn()
        val state = _snapshot.value
        val next = TurnResolver.findNextCaregiverForEscalation(decliningCaregiver, state.caregivers)
        val targetId = next?.id
            ?: TurnResolver.nextNonExistentCaregiverId(decliningCaregiver.id, state.caregivers)
        val targetName = next?.name.orEmpty()

        if (existingEscalationId != null) {
            escalationsCol.document(existingEscalationId)
                .update("status", EscalationStatus.DECLINED_AGAIN.name)
                .await()
        }

        val dateKey = TurnResolver.todayKey()
        val now = System.currentTimeMillis()
        val escalationId = UUID.randomUUID().toString()
        val escalation = TurnEscalation(
            id = escalationId,
            dateKey = dateKey,
            originalCaregiverId = decliningCaregiver.id,
            originalCaregiverName = decliningCaregiver.name,
            targetCaregiverId = targetId,
            targetCaregiverName = targetName,
            slotTime = slotTime,
            status = EscalationStatus.PENDING,
            createdAtEpochMillis = now
        )

        escalationsCol.document(escalationId).set(
            mapOf(
                "id" to escalation.id,
                "dateKey" to escalation.dateKey,
                "originalCaregiverId" to escalation.originalCaregiverId,
                "originalCaregiverName" to escalation.originalCaregiverName,
                "targetCaregiverId" to escalation.targetCaregiverId,
                "targetCaregiverName" to escalation.targetCaregiverName,
                "slotTime" to escalation.slotTime,
                "status" to escalation.status.name,
                "createdAtEpochMillis" to escalation.createdAtEpochMillis
            )
        ).await()

        refresh()
        escalation
    }

    override suspend fun updateMyPreferredTime(caregiverId: String, preferredTime: String?): Result<Unit> =
        runCatching {
            ensureSignedIn()
            caregiversCol.document(caregiverId)
                .update("preferredTime", preferredTime)
                .await()
            refresh()
        }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        prefs.setNotificationsEnabled(enabled)
    }

    override suspend fun updateFcmTokenForCurrentUser(token: String) {
        val id = prefs.linkedCaregiverId.first() ?: return
        runCatching {
            ensureSignedIn()
            caregiversCol.document(id)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
                .await()
        }
    }

    override suspend fun upsertCaregiver(caregiver: Caregiver): Result<Unit> = runCatching {
        ensureSignedIn()
        caregiversCol.document(caregiver.id)
            .set(caregiver.toMap(), SetOptions.merge())
            .await()
        refresh()
    }

    override suspend fun deleteCaregiver(caregiverId: String): Result<Unit> = runCatching {
        ensureSignedIn()
        caregiversCol.document(caregiverId).delete().await()
        refresh()
    }

    override suspend fun updateAppSettings(settings: AppSettings): Result<Unit> = runCatching {
        ensureSignedIn()
        settingsDoc.set(
            mapOf(
                "expectedDailyCaresDefault" to settings.expectedDailyCaresDefault,
                "healthDecayPerMissedTurn" to settings.healthDecayPerMissedTurn
            )
        ).await()
        refresh()
    }

    override suspend fun clearAllLogs(): Result<Unit> = runCatching {
        ensureSignedIn()
        val logsSnap = careLogsCol.get().await()
        val escSnap = escalationsCol.get().await()
        val allRefs = logsSnap.documents.map { it.reference } + escSnap.documents.map { it.reference }
        for (chunk in allRefs.chunked(400)) {
            val batch = db.batch()
            for (ref in chunk) {
                batch.delete(ref)
            }
            batch.commit().await()
        }
        refresh()
    }

    private fun Caregiver.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "isDeveloper" to isDeveloper,
        "preferredTime" to preferredTime,
        "turnOrder" to turnOrder,
        "isActive" to isActive,
        "linkedAuthUid" to linkedAuthUid,
        "fcmToken" to fcmToken
    )

    private fun DocumentSnapshot.toCaregiverOrNull(): Caregiver? {
        val docId = getString("id") ?: id
        val name = getString("name") ?: return null
        return Caregiver(
            id = docId,
            name = name,
            isDeveloper = getBoolean("isDeveloper") ?: false,
            preferredTime = getString("preferredTime"),
            turnOrder = (getLong("turnOrder") ?: 0L).toInt(),
            isActive = getBoolean("isActive") ?: true,
            linkedAuthUid = getString("linkedAuthUid"),
            fcmToken = getString("fcmToken")
        )
    }

    private fun DocumentSnapshot.toCareLogOrNull(): CareLog? {
        val caregiverId = getString("caregiverId") ?: return null
        val caregiverName = getString("caregiverName") ?: ""
        val caredAt = getLong("caredAtEpochMillis") ?: return null
        return CareLog(
            id = getString("id") ?: id,
            caregiverId = caregiverId,
            caregiverName = caregiverName,
            caredAtEpochMillis = caredAt,
            wasScheduledTurn = getBoolean("wasScheduledTurn") ?: true,
            notes = getString("notes")
        )
    }

    private fun DocumentSnapshot.toTurnEscalationOrNull(): TurnEscalation? {
        val dateKey = getString("dateKey") ?: return null
        val originalId = getString("originalCaregiverId") ?: return null
        val targetId = getString("targetCaregiverId") ?: return null
        val slotTime = getString("slotTime") ?: return null
        val statusStr = getString("status") ?: EscalationStatus.PENDING.name
        return TurnEscalation(
            id = getString("id") ?: id,
            dateKey = dateKey,
            originalCaregiverId = originalId,
            originalCaregiverName = getString("originalCaregiverName") ?: "",
            targetCaregiverId = targetId,
            targetCaregiverName = getString("targetCaregiverName") ?: "",
            slotTime = slotTime,
            status = runCatching { EscalationStatus.valueOf(statusStr) }
                .getOrDefault(EscalationStatus.PENDING),
            createdAtEpochMillis = getLong("createdAtEpochMillis") ?: 0L
        )
    }

    private fun DocumentSnapshot.toAppSettings(): AppSettings = AppSettings(
        expectedDailyCaresDefault = (getLong("expectedDailyCaresDefault") ?: 2L).toInt(),
        healthDecayPerMissedTurn = getDouble("healthDecayPerMissedTurn") ?: 12.0
    )
}
