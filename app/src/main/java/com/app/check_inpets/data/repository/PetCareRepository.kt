package com.app.check_inpets.data.repository

import com.app.check_inpets.model.AppSettings
import com.app.check_inpets.model.CareLog
import com.app.check_inpets.model.Caregiver
import com.app.check_inpets.model.TurnEscalation
import kotlinx.coroutines.flow.Flow

data class PetCareSnapshot(
    val caregivers: List<Caregiver> = emptyList(),
    val careLogs: List<CareLog> = emptyList(),
    val escalations: List<TurnEscalation> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val isUsingFirebaseBackend: Boolean = false
)

interface PetCareRepository {
    val snapshot: Flow<PetCareSnapshot>
    val linkedCaregiverId: Flow<String?>
    val notificationsEnabled: Flow<Boolean>

    suspend fun refresh()
    suspend fun loginWithFiveDigitId(id: String): Result<Caregiver>
    suspend fun createInitialDeveloperAndLogin(
        id: String,
        name: String,
        preferredTime: String?
    ): Result<Caregiver>
    suspend fun logoutDevice()

    suspend fun recordPetCare(
        caregiver: Caregiver,
        wasScheduledTurn: Boolean,
        pendingEscalationId: String? = null,
        notes: String? = null
    ): Result<CareLog>

    suspend fun declineTurnAndNotifyNext(
        decliningCaregiver: Caregiver,
        slotTime: String,
        existingEscalationId: String? = null
    ): Result<TurnEscalation>

    suspend fun updateMyPreferredTime(caregiverId: String, preferredTime: String?): Result<Unit>
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun updateFcmTokenForCurrentUser(token: String)

    // Developer operations
    suspend fun upsertCaregiver(caregiver: Caregiver): Result<Unit>
    suspend fun deleteCaregiver(caregiverId: String): Result<Unit>
    suspend fun updateAppSettings(settings: AppSettings): Result<Unit>
    suspend fun clearAllLogs(): Result<Unit>
}
