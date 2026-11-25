package com.app.check_inpets.domain

import com.app.check_inpets.model.CareLog
import com.app.check_inpets.model.Caregiver
import com.app.check_inpets.model.EscalationStatus
import com.app.check_inpets.model.TurnEscalation
import com.app.check_inpets.model.TurnStatus
import com.app.check_inpets.ui.components.TimeFormatting
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object TurnResolver {

    val BRASILIA_ZONE: ZoneId = ZoneOffset.ofHours(-3)

    private const val OVERDUE_MINUTES = 10L

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun todayKey(nowMillis: Long = System.currentTimeMillis(), zoneId: ZoneId = BRASILIA_ZONE): String {
        return Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate().format(dateFormatter)
    }

    fun parseTimeOrNull(raw: String?): LocalTime? {
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalTime.parse(raw.trim(), timeFormatter) }.getOrNull()
    }

    fun resolveTurnStatus(
        currentUser: Caregiver,
        allCaregivers: List<Caregiver>,
        logs: List<CareLog>,
        todayEscalations: List<TurnEscalation>,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = BRASILIA_ZONE
    ): TurnStatus {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val nowTime = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalTime()
        val dateKey = today.format(dateFormatter)
        val yesterday = today.minusDays(1)
        val yesterdayKey = yesterday.format(dateFormatter)

        // 1. If someone declined and escalated to THIS device/ID and care hasn't been done yet
        val pendingForMe = todayEscalations
            .sortedByDescending { it.createdAtEpochMillis }
            .firstOrNull { esc ->
                esc.status == EscalationStatus.PENDING &&
                    esc.targetCaregiverId == currentUser.id &&
                    esc.createdAtEpochMillis <= nowMillis &&
                    !hasCareSince(logs, escalationSlotStartMillis(esc, zoneId))
            }
        if (pendingForMe != null) {
            val slotStart = escalationSlotStartMillis(pendingForMe, zoneId)
            val isOverdue = nowMillis >= slotStart + OVERDUE_MINUTES * 60_000L
            return TurnStatus.EscalatedToMe(pendingForMe, isOverdue = isOverdue)
        }

        // 2. Check if currentUser has a preferredTime whose active or missed turn hasn't been cared for yet
        // Never move on to the next schedule until care is completed (or declined by this user).
        val preferred = parseTimeOrNull(currentUser.preferredTime)
        if (preferred != null && currentUser.isActive) {
            val todaySlotMillis = ZonedDateTime.of(today, preferred, zoneId).toInstant().toEpochMilli()
            if (nowMillis >= todaySlotMillis) {
                val alreadyDeclinedToday = todayEscalations.any {
                    it.dateKey == dateKey &&
                        it.originalCaregiverId == currentUser.id &&
                        it.slotTime == currentUser.preferredTime
                }
                val alreadyCaredSinceSlot = hasCareSince(logs, todaySlotMillis)
                if (!alreadyDeclinedToday && !alreadyCaredSinceSlot) {
                    val isOverdue = nowMillis >= todaySlotMillis + OVERDUE_MINUTES * 60_000L
                    return TurnStatus.MyScheduledTurn(
                        slotTime = currentUser.preferredTime!!,
                        isOverdue = isOverdue
                    )
                }
            } else {
                val yesterdaySlotMillis = ZonedDateTime.of(yesterday, preferred, zoneId).toInstant().toEpochMilli()
                val hadActivityBeforeYesterdaySlot = logs.any { it.caredAtEpochMillis < yesterdaySlotMillis }
                if (hadActivityBeforeYesterdaySlot && !hasCareSince(logs, yesterdaySlotMillis)) {
                    val alreadyDeclinedYesterday = todayEscalations.any {
                        it.dateKey == yesterdayKey &&
                            it.originalCaregiverId == currentUser.id &&
                            it.slotTime == currentUser.preferredTime
                    }
                    if (!alreadyDeclinedYesterday) {
                        return TurnStatus.MyScheduledTurn(
                            slotTime = currentUser.preferredTime!!,
                            isOverdue = true
                        )
                    }
                }
            }
        }

        // 3. Check if ANY arrived scheduled turn or escalation still hasn't been cared for.
        // Never advance to the next scheduled time until care is completed.
        val uncaredSlotTime = findUncaredPendingSlotTime(
            allCaregivers = allCaregivers,
            logs = logs,
            escalations = todayEscalations,
            nowMillis = nowMillis,
            today = today,
            yesterday = yesterday,
            zoneId = zoneId
        )
        if (uncaredSlotTime != null) {
            return TurnStatus.NotMyTurn(
                nextTurnDescription = null,
                hasUncaredPendingTurn = true,
                pendingSlotTime = uncaredSlotTime
            )
        }

        // 4. All past turns up to now have been cared for — show next upcoming schedule
        val nextUpcoming = findNextUpcomingScheduleText(allCaregivers, nowTime, nowMillis)
        return TurnStatus.NotMyTurn(
            nextTurnDescription = nextUpcoming,
            hasUncaredPendingTurn = false,
            pendingSlotTime = null
        )
    }

    private fun findUncaredPendingSlotTime(
        allCaregivers: List<Caregiver>,
        logs: List<CareLog>,
        escalations: List<TurnEscalation>,
        nowMillis: Long,
        today: LocalDate,
        yesterday: LocalDate,
        zoneId: ZoneId
    ): String? {
        val pendingEsc = escalations
            .sortedByDescending { it.createdAtEpochMillis }
            .firstOrNull { esc ->
                (esc.status == EscalationStatus.PENDING || esc.status == EscalationStatus.DECLINED_AGAIN) &&
                    esc.createdAtEpochMillis <= nowMillis &&
                    !hasCareSince(logs, escalationSlotStartMillis(esc, zoneId))
            }
        if (pendingEsc != null) {
            return pendingEsc.slotTime
        }

        val scheduledCaregivers = allCaregivers
            .filter { it.isActive }
            .mapNotNull { cg -> parseTimeOrNull(cg.preferredTime)?.let { cg to it } }
            .sortedByDescending { it.second }

        for ((cg, time) in scheduledCaregivers) {
            val todaySlotMillis = ZonedDateTime.of(today, time, zoneId).toInstant().toEpochMilli()
            if (nowMillis >= todaySlotMillis) {
                if (!hasCareSince(logs, todaySlotMillis)) {
                    return cg.preferredTime
                }
            } else {
                val yesterdaySlotMillis = ZonedDateTime.of(yesterday, time, zoneId).toInstant().toEpochMilli()
                val hadActivityBefore = logs.any { it.caredAtEpochMillis < yesterdaySlotMillis }
                if (hadActivityBefore && !hasCareSince(logs, yesterdaySlotMillis)) {
                    return cg.preferredTime
                }
            }
        }
        return null
    }

    private fun hasCareSince(logs: List<CareLog>, slotStartMillis: Long): Boolean {
        return logs.any { it.caredAtEpochMillis >= slotStartMillis }
    }

    private fun escalationSlotStartMillis(escalation: TurnEscalation, zoneId: ZoneId): Long {
        val date = runCatching { LocalDate.parse(escalation.dateKey, dateFormatter) }.getOrNull()
        val time = parseTimeOrNull(escalation.slotTime)
        return if (date != null && time != null) {
            ZonedDateTime.of(date, time, zoneId).toInstant().toEpochMilli()
        } else {
            escalation.createdAtEpochMillis
        }
    }

    fun findNextCaregiverForEscalation(
        decliningUser: Caregiver,
        allCaregivers: List<Caregiver>
    ): Caregiver? {
        val activeOthers = allCaregivers.filter { it.isActive && it.id != decliningUser.id }
        if (activeOthers.isEmpty()) return null

        val ordered = activeOthers.sortedWith(
            compareBy<Caregiver> { it.turnOrder }
                .thenBy { parseTimeOrNull(it.preferredTime) ?: LocalTime.MAX }
                .thenBy { it.id }
        )

        val afterOrder = ordered.firstOrNull {
            it.turnOrder > decliningUser.turnOrder ||
                (it.turnOrder == decliningUser.turnOrder && it.id > decliningUser.id)
        }
        return afterOrder ?: ordered.first()
    }

    fun nextNonExistentCaregiverId(
        currentCaregiverId: String,
        allCaregivers: List<Caregiver> = emptyList()
    ): String {
        val existingIds = allCaregivers.mapTo(HashSet()) { it.id }
        val currentNum = currentCaregiverId.trim().toIntOrNull() ?: 0
        for (offset in 1..99_999) {
            val candidate = "%05d".format((currentNum + offset) % 100_000)
            if (candidate !in existingIds) return candidate
        }
        return "%05d".format((currentNum + 1) % 100_000)
    }

    private fun findNextUpcomingScheduleText(
        caregivers: List<Caregiver>,
        nowTime: LocalTime,
        nowMillis: Long
    ): String? {
        val scheduled = caregivers
            .filter { it.isActive && parseTimeOrNull(it.preferredTime) != null }
            .map { it to parseTimeOrNull(it.preferredTime)!! }
            .sortedBy { it.second }

        if (scheduled.isEmpty()) return null

        val upcomingToday = scheduled.firstOrNull { !it.second.isBefore(nowTime) }
        return if (upcomingToday != null) {
            val formatted = TimeFormatting.formatBrasiliaSlotForDevice(
                upcomingToday.first.preferredTime!!,
                nowMillis
            )
            "Próximo horário: $formatted"
        } else {
            val firstTomorrow = scheduled.first()
            val formatted = TimeFormatting.formatBrasiliaSlotForDevice(
                firstTomorrow.first.preferredTime!!,
                nowMillis
            )
            "Próximo horário: amanhã às $formatted"
        }
    }
}
