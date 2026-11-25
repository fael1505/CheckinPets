package com.app.check_inpets.model

import kotlinx.serialization.Serializable

@Serializable
data class Caregiver(
    val id: String,
    val name: String,
    val isDeveloper: Boolean = false,
    val preferredTime: String? = null, // "HH:mm" or null
    val turnOrder: Int = 0,
    val isActive: Boolean = true,
    val linkedAuthUid: String? = null,
    val fcmToken: String? = null
)

@Serializable
data class CareLog(
    val id: String,
    val caregiverId: String,
    val caregiverName: String,
    val caredAtEpochMillis: Long,
    val wasScheduledTurn: Boolean = true,
    val notes: String? = null
)

@Serializable
enum class EscalationStatus {
    PENDING,
    ACCEPTED_DONE,
    DECLINED_AGAIN
}

@Serializable
data class TurnEscalation(
    val id: String,
    val dateKey: String, // "yyyy-MM-dd"
    val originalCaregiverId: String,
    val originalCaregiverName: String,
    val targetCaregiverId: String,
    val targetCaregiverName: String,
    val slotTime: String,
    val status: EscalationStatus = EscalationStatus.PENDING,
    val createdAtEpochMillis: Long
)

@Serializable
data class AppSettings(
    val expectedDailyCaresDefault: Int = 2,
    val healthDecayPerMissedTurn: Double = 12.0
)

data class PetHealthReport(
    val score: Int, // 0..100
    val statusLabel: String,
    val learnedDailyTarget: Double,
    val disciplineScore: Int,
    val regularityScore: Int,
    val daysObserved: Int
)

sealed interface TurnStatus {
    data class MyScheduledTurn(
        val slotTime: String,
        val isOverdue: Boolean = false
    ) : TurnStatus

    data class EscalatedToMe(
        val escalation: TurnEscalation,
        val isOverdue: Boolean = false
    ) : TurnStatus

    data class NotMyTurn(
        val nextTurnDescription: String?,
        val hasUncaredPendingTurn: Boolean = false,
        val pendingSlotTime: String? = null
    ) : TurnStatus
}

data class CaregiverStats(
    val caregiverId: String,
    val caregiverName: String,
    val totalCount: Int,
    val scheduledCount: Int
)

data class DailyCarePoint(
    val dateLabel: String,
    val count: Int
)
