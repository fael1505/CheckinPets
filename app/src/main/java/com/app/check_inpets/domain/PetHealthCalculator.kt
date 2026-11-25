package com.app.check_inpets.domain

import com.app.check_inpets.model.AppSettings
import com.app.check_inpets.model.CareLog
import com.app.check_inpets.model.Caregiver
import com.app.check_inpets.model.EscalationStatus
import com.app.check_inpets.model.PetHealthReport
import com.app.check_inpets.model.TurnEscalation
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

object PetHealthCalculator {

    fun calculate(
        logs: List<CareLog>,
        caregivers: List<Caregiver>,
        escalations: List<TurnEscalation>,
        settings: AppSettings,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): PetHealthReport {
        if (logs.isEmpty()) {
            val scheduledCount = caregivers.count { it.isActive && !it.preferredTime.isNullOrBlank() }
            val baseTarget = maxOf(1, if (scheduledCount > 0) scheduledCount else settings.expectedDailyCaresDefault)
            return PetHealthReport(
                score = 100,
                statusLabel = "Iniciando padrão",
                learnedDailyTarget = baseTarget.toDouble(),
                disciplineScore = 100,
                regularityScore = 100,
                daysObserved = 0
            )
        }

        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val windowStartDate = today.minusDays(13)

        val logsByDay = logs
            .map { it to Instant.ofEpochMilli(it.caredAtEpochMillis).atZone(zoneId).toLocalDate() }
            .filter { (_, date) -> !date.isBefore(windowStartDate) && !date.isAfter(today) }
            .groupBy({ it.second }, { it.first })

        val earliestDate = logs.minOf {
            Instant.ofEpochMilli(it.caredAtEpochMillis).atZone(zoneId).toLocalDate()
        }
        val daysObserved = (ChronoUnit.DAYS.between(earliestDate, today).toInt() + 1).coerceIn(1, 14)

        val scheduledSlotsPerDay = caregivers.count { it.isActive && !it.preferredTime.isNullOrBlank() }
        val baselineDaily = maxOf(
            1.0,
            if (scheduledSlotsPerDay > 0) scheduledSlotsPerDay.toDouble()
            else settings.expectedDailyCaresDefault.toDouble()
        )

        val pastDaysCount = maxOf(1, daysObserved - 1)
        val completedPastDaysLogs = logsByDay
            .filterKeys { it.isBefore(today) }
            .values
            .sumOf { it.size }
        val observedDailyAvg = if (daysObserved >= 3) {
            completedPastDaysLogs.toDouble() / pastDaysCount
        } else {
            baselineDaily
        }

        val patternWeight = ((daysObserved - 1).coerceIn(0, 7)) / 7.0
        val learnedDailyTarget = (baselineDaily * (1.0 - patternWeight * 0.5)) +
            (observedDailyAvg.coerceAtLeast(1.0) * (patternWeight * 0.5))

        val recentWindowDays = minOf(daysObserved, 7)
        var dailyDeviationSum = 0.0
        for (offset in 1 until recentWindowDays) {
            val d = today.minusDays(offset.toLong())
            val count = logsByDay[d]?.size?.toDouble() ?: 0.0
            val ratio = (count / learnedDailyTarget).coerceIn(0.0, 1.3)
            dailyDeviationSum += abs(1.0 - ratio.coerceAtMost(1.0))
        }
        val pastRegularity = if (recentWindowDays > 1) {
            100.0 - (dailyDeviationSum / (recentWindowDays - 1)) * 65.0
        } else {
            95.0
        }

        val latestLogMillis = logs.maxOf { it.caredAtEpochMillis }
        val hoursSinceLast = ((nowMillis - latestLogMillis).coerceAtLeast(0L)) / 3_600_000.0
        val expectedIntervalHours = (24.0 / learnedDailyTarget).coerceIn(4.0, 24.0)
        val overdueHours = (hoursSinceLast - expectedIntervalHours * 1.25).coerceAtLeast(0.0)
        val recencyPenalty = (overdueHours * 3.5).coerceAtMost(40.0)

        val regularityScore = (pastRegularity - recencyPenalty).roundToInt().coerceIn(10, 100)

        val recentLogs = logsByDay.values.flatten()
        val scheduledRatio = if (recentLogs.isNotEmpty()) {
            recentLogs.count { it.wasScheduledTurn }.toDouble() / recentLogs.size
        } else {
            1.0
        }
        val unresolvedEscalations = escalations.count {
            it.status == EscalationStatus.PENDING || it.status == EscalationStatus.DECLINED_AGAIN
        }
        val escalationPenalty = unresolvedEscalations * settings.healthDecayPerMissedTurn
        val disciplineRaw = 60.0 + (scheduledRatio * 40.0) - escalationPenalty
        val disciplineScore = disciplineRaw.roundToInt().coerceIn(10, 100)

        val finalScore = ((regularityScore * 0.6) + (disciplineScore * 0.4))
            .roundToInt()
            .coerceIn(5, 100)

        val statusLabel = when {
            finalScore >= 85 -> "Excelente"
            finalScore >= 65 -> "Boa disciplina"
            finalScore >= 40 -> "Atenção"
            else -> "Baixa disciplina"
        }

        return PetHealthReport(
            score = finalScore,
            statusLabel = statusLabel,
            learnedDailyTarget = learnedDailyTarget,
            disciplineScore = disciplineScore,
            regularityScore = regularityScore,
            daysObserved = daysObserved
        )
    }
}
