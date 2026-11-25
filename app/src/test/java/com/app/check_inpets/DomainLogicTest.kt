package com.app.check_inpets

import com.app.check_inpets.domain.PetHealthCalculator
import com.app.check_inpets.domain.TurnResolver
import com.app.check_inpets.model.AppSettings
import com.app.check_inpets.model.CareLog
import com.app.check_inpets.model.Caregiver
import com.app.check_inpets.model.EscalationStatus
import com.app.check_inpets.model.TurnEscalation
import com.app.check_inpets.model.TurnStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DomainLogicTest {

    private val zone = ZoneId.of("UTC")
    private val settings = AppSettings()

    private val dev = Caregiver("00001", "Rafael", isDeveloper = true, preferredTime = "08:00", turnOrder = 1)
    private val ana = Caregiver("10002", "Ana", isDeveloper = false, preferredTime = "13:00", turnOrder = 2)
    private val lucas = Caregiver("10003", "Lucas", isDeveloper = false, preferredTime = "20:00", turnOrder = 3)
    private val roster = listOf(dev, ana, lucas)

    private fun epochAt(hour: Int, minute: Int, daysAgo: Long = 0): Long {
        val date = LocalDate.of(2026, 11, 18).minusDays(daysAgo)
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), zone).toInstant().toEpochMilli()
    }

    @Test
    fun `scheduled turn returns MyScheduledTurn inside active window`() {
        val now = epochAt(8, 15)
        val status = TurnResolver.resolveTurnStatus(
            currentUser = dev,
            allCaregivers = roster,
            logs = emptyList(),
            todayEscalations = emptyList(),
            nowMillis = now,
            zoneId = zone
        )
        assertTrue(status is TurnStatus.MyScheduledTurn)
        assertEquals("08:00", (status as TurnStatus.MyScheduledTurn).slotTime)
    }

    @Test
    fun `early care before scheduled time does not suppress scheduled turn`() {
        val earlyLog = CareLog("early", dev.id, dev.name, epochAt(7, 57), wasScheduledTurn = false)
        val now = epochAt(8, 0)
        val status = TurnResolver.resolveTurnStatus(
            currentUser = dev,
            allCaregivers = roster,
            logs = listOf(earlyLog),
            todayEscalations = emptyList(),
            nowMillis = now,
            zoneId = zone
        )
        assertTrue(status is TurnStatus.MyScheduledTurn)
        assertEquals("08:00", (status as TurnStatus.MyScheduledTurn).slotTime)
    }

    @Test
    fun `outside own window but escalated to user returns EscalatedToMe`() {
        val now = epochAt(8, 20)
        val escalation = TurnEscalation(
            id = "esc-1",
            dateKey = "2026-11-18",
            originalCaregiverId = dev.id,
            originalCaregiverName = dev.name,
            targetCaregiverId = ana.id,
            targetCaregiverName = ana.name,
            slotTime = "08:00",
            status = EscalationStatus.PENDING,
            createdAtEpochMillis = now - 60_000L
        )
        val status = TurnResolver.resolveTurnStatus(
            currentUser = ana,
            allCaregivers = roster,
            logs = emptyList(),
            todayEscalations = listOf(escalation),
            nowMillis = now,
            zoneId = zone
        )
        assertTrue(status is TurnStatus.EscalatedToMe)
    }

    @Test
    fun `outside window and previous slot uncared keeps uncared pending without advancing`() {
        val now = epochAt(10, 30)
        val status = TurnResolver.resolveTurnStatus(
            currentUser = ana,
            allCaregivers = roster,
            logs = emptyList(),
            todayEscalations = emptyList(),
            nowMillis = now,
            zoneId = zone
        )
        assertTrue(status is TurnStatus.NotMyTurn)
        val notMyTurn = status as TurnStatus.NotMyTurn
        assertTrue(notMyTurn.hasUncaredPendingTurn)
        assertEquals("08:00", notMyTurn.pendingSlotTime)
        assertEquals(null, notMyTurn.nextTurnDescription)
    }

    @Test
    fun `outside window and previous slot cared advances to next schedule`() {
        val now = epochAt(10, 30)
        val morningCare = CareLog("c1", dev.id, dev.name, epochAt(8, 10), wasScheduledTurn = true)
        val status = TurnResolver.resolveTurnStatus(
            currentUser = ana,
            allCaregivers = roster,
            logs = listOf(morningCare),
            todayEscalations = emptyList(),
            nowMillis = now,
            zoneId = zone
        )
        assertTrue(status is TurnStatus.NotMyTurn)
        val notMyTurn = status as TurnStatus.NotMyTurn
        assertTrue(!notMyTurn.hasUncaredPendingTurn)
        assertEquals("Próximo horário: 13:00", notMyTurn.nextTurnDescription)
    }

    @Test
    fun `scheduled turn never expires after 60 minutes until cared`() {
        val now = epochAt(11, 30)
        val status = TurnResolver.resolveTurnStatus(
            currentUser = dev,
            allCaregivers = roster,
            logs = emptyList(),
            todayEscalations = emptyList(),
            nowMillis = now,
            zoneId = zone
        )
        assertTrue(status is TurnStatus.MyScheduledTurn)
        val myTurn = status as TurnStatus.MyScheduledTurn
        assertEquals("08:00", myTurn.slotTime)
        assertTrue(myTurn.isOverdue)
    }

    @Test
    fun `findNextCaregiverForEscalation wraps around circularly`() {
        val nextFromLucas = TurnResolver.findNextCaregiverForEscalation(lucas, roster)
        assertEquals("00001", nextFromLucas?.id)

        val nextFromDev = TurnResolver.findNextCaregiverForEscalation(dev, roster)
        assertEquals("10002", nextFromDev?.id)
    }

    @Test
    fun `nextNonExistentCaregiverId increments current id by 1`() {
        assertEquals("59072", TurnResolver.nextNonExistentCaregiverId("59071", listOf(dev)))
        assertEquals("00002", TurnResolver.nextNonExistentCaregiverId("00001", listOf(dev)))
        assertEquals("00000", TurnResolver.nextNonExistentCaregiverId("99999", emptyList()))
    }

    @Test
    fun `pet health score reflects regularity and discipline`() {
        val now = epochAt(14, 0)
        val logs = listOf(
            CareLog("1", "10002", "Ana", epochAt(13, 5, 0), true),
            CareLog("2", "00001", "Rafael", epochAt(8, 0, 0), true),
            CareLog("3", "10003", "Lucas", epochAt(20, 0, 1), true),
            CareLog("4", "10002", "Ana", epochAt(13, 0, 1), true),
            CareLog("5", "00001", "Rafael", epochAt(8, 0, 1), true)
        )
        val report = PetHealthCalculator.calculate(
            logs = logs,
            caregivers = roster,
            escalations = emptyList(),
            settings = settings,
            nowMillis = now,
            zoneId = zone
        )
        assertTrue(report.score >= 80)
    }
}
